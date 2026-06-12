package app.fork.messenger.net

import android.content.Context
import android.util.Log
import app.fork.messenger.BuildConfig
import app.fork.messenger.TdClient
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import org.json.JSONArray
import org.json.JSONObject

/**
 * MTProto proxy manager.
 *
 * Responsibilities:
 *  - keep a pool of proxy candidates: PRIMARY (compiled / GitHub config), plus ones
 *    fetched from the public channel and ones the user added manually;
 *  - validate them with TDLib TestProxy (which runs THROUGH the device's current
 *    network — so a proxy that passes while a VPN is on is, by definition, working
 *    with that VPN);
 *  - auto-switch to a working proxy when the active one fails or the network/VPN
 *    changes, preferring the PRIMARY when it answers promptly (stability), otherwise
 *    the fastest responder (recovery);
 *  - expose live status (ok / latency / active) for the settings screen.
 *
 * Design guardrails learned the hard way (v0.16): never churn the proxy on a timer
 * while a handshake is in progress; only re-select when we are genuinely not
 * connected, and gate re-selection so it cannot loop.
 */
object ProxyPool {
    private const val TAG = "ProxyPool"
    private const val PREFS = "proxy_pool"
    private const val KEY_LIST = "list"
    private const val KEY_PREFERRED = "preferred" // "host:port" the user pinned, if any
    private const val MAX_POOL = 40
    private const val MAX_TEST_PARALLEL = 8   // candidates to try during failover
    private const val TEST_BATCH = 4          // batch size for "check all" (avoids storms)
    private const val LIVE_TRY_MS = 8000L     // wait for the real connection per candidate
    private const val PING_TIMEOUT_MS = 6000L

    /** Public channel that posts working proxies (user-provided). */
    private const val PROXY_CHANNEL = "telemtfreeproxy"

    enum class Source { PRIMARY, CHANNEL, MANUAL }

    data class ProxyEntry(
        val host: String,
        val port: Int,
        val secret: String,
        val source: Source,
        val lastOk: Boolean? = null, // null = unknown
        val latencyMs: Int = -1,
        val lastCheckedAt: Long = 0L,
    ) {
        val key: String get() = "$host:$port"
    }

    private data class Candidate(val host: String, val port: Int, val secret: String) {
        val key: String get() = "$host:$port"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var selecting = false

    /** Combined view (primary + pool) with status, for the settings screen. */
    private val _entries = MutableStateFlow<List<ProxyEntry>>(emptyList())
    val entries: StateFlow<List<ProxyEntry>> = _entries.asStateFlow()

    /** "host:port" of the currently enabled proxy. */
    private val _activeKey = MutableStateFlow<String?>(null)
    val activeKey: StateFlow<String?> = _activeKey.asStateFlow()

    /** True while a manual "check all" is running. */
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    // In-memory status cache keyed by "host:port" (mirrors persisted status).
    private val status = ConcurrentHashMap<String, Triple<Boolean, Int, Long>>() // ok, latency, time

    private fun proxyOf(c: Candidate) = TdApi.Proxy(c.host, c.port, TdApi.ProxyTypeMtproto(c.secret))
    private fun proxyOf(e: ProxyEntry) = TdApi.Proxy(e.host, e.port, TdApi.ProxyTypeMtproto(e.secret))

    // ---------- Candidate assembly ----------

    /** Primary first, then user-pinned, then pool — deduped. */
    private fun candidates(context: Context): List<Candidate> {
        val result = LinkedHashMap<String, Candidate>()
        primaryCandidate(context)?.let { result[it.key] = it }
        loadSaved(context).forEach { result.putIfAbsent(it.key, it) }
        return result.values.toList()
    }

    private fun primaryCandidate(context: Context): Candidate? {
        val p = ProxyConfig.current(context)
        if (p.host.isNotBlank() && p.port != 0 && p.secret.isNotBlank()) {
            return Candidate(p.host, p.port, p.secret)
        }
        if (BuildConfig.PROXY_HOST.isNotBlank() && BuildConfig.PROXY_PORT != 0) {
            return Candidate(BuildConfig.PROXY_HOST, BuildConfig.PROXY_PORT, BuildConfig.PROXY_SECRET)
        }
        return null
    }

    /** Rebuilds the public [entries] list (primary + pool) with current statuses. */
    fun refreshEntries(context: Context) {
        val primaryKey = primaryCandidate(context)?.key
        val list = candidates(context).map { c ->
            val st = status[c.key]
            ProxyEntry(
                host = c.host, port = c.port, secret = c.secret,
                source = when {
                    c.key == primaryKey -> Source.PRIMARY
                    isManual(context, c.key) -> Source.MANUAL
                    else -> Source.CHANNEL
                },
                lastOk = st?.first,
                latencyMs = st?.second ?: -1,
                lastCheckedAt = st?.third ?: 0L,
            )
        }
        _entries.value = list
    }

    // ---------- Startup / cleanup (used by TdClient) ----------

    /**
     * STARTUP fast path: enable the active proxy IMMEDIATELY (no GetProxies round-trip)
     * so it is active for TDLib's very first connection attempt. Uses the user's pinned
     * proxy if set, otherwise the primary.
     */
    fun enablePrimaryFast(context: Context) {
        val chosen = pinned(context) ?: primaryCandidate(context) ?: return
        _activeKey.value = chosen.key
        TdClient.send(TdApi.AddProxy(proxyOf(chosen), true, "Fork"))
        refreshEntries(context)
    }

    /** Reconcile to a single enabled proxy (pinned or primary); clears stale entries. */
    fun cleanupKeepingPrimary(context: Context) {
        val chosen = pinned(context) ?: primaryCandidate(context) ?: return
        enableExact(context, Candidate(chosen.host, chosen.port, chosen.secret))
    }

    /**
     * Connect DIRECTLY (disable the proxy). Used when a VPN is active: the VPN already
     * bypasses the block, so routing through a remote proxy only adds latency. TDLib
     * then connects straight to Telegram through the VPN tunnel.
     */
    fun goDirect(context: Context) {
        _activeKey.value = null
        TdClient.send(TdApi.DisableProxy())
        refreshEntries(context)
    }

    // ---------- Auto failover / selection ----------

    /**
     * Called when we are NOT connected (stall or network/VPN change). Tries each
     * candidate LIVE — enables it and waits to see if the real connection comes up —
     * primary/pinned first, stopping at the first that connects.
     *
     * Why live-try instead of TestProxy: on-device testing showed TestProxy gives
     * false negatives (it failed for the very proxy we were connected through, esp.
     * under parallel load). Enabling a proxy and watching the actual connection state
     * is the ground truth, and it naturally validates VPN-compatibility (a proxy that
     * brings the connection up while a VPN is on is, by definition, working with it).
     */
    fun selectWorking(context: Context, reason: String) {
        if (selecting) return
        val cands = candidates(context)
        if (cands.isEmpty()) return
        selecting = true
        scope.launch {
            try {
                for (c in cands.take(MAX_TEST_PARALLEL)) {
                    Log.i(TAG, "select($reason): trying ${c.key}")
                    enableExact(context, c)
                    val ok = withTimeoutOrNull(LIVE_TRY_MS) {
                        while (TdClient.connectionState.value != "подключено") delay(200)
                        true
                    } ?: false
                    status[c.key] = Triple(ok, -1, System.currentTimeMillis())
                    refreshEntries(context)
                    if (ok) {
                        Log.i(TAG, "select($reason) -> ${c.key} works")
                        break
                    }
                }
            } finally {
                selecting = false
            }
        }
    }

    /**
     * Manual "Проверить все": validate proxies without disrupting the connection.
     * The currently-active proxy is marked OK if we are connected (ground truth);
     * the rest are probed with TestProxy in SMALL batches (parallel storms caused
     * false negatives), updating status live.
     */
    fun checkAll(context: Context) {
        if (_checking.value) return
        val cands = candidates(context)
        if (cands.isEmpty()) return
        _checking.value = true
        scope.launch {
            try {
                val connected = TdClient.connectionState.value == "подключено"
                val active = _activeKey.value
                for (batch in cands.chunked(TEST_BATCH)) {
                    batch.map { c ->
                        async {
                            // PingProxy returns the REAL round-trip latency (like Telegram),
                            // unlike TestProxy whose wall-time includes the whole handshake.
                            val (ok, ms) = pingOne(c)
                            val finalOk = ok || (connected && c.key == active)
                            status[c.key] = Triple(finalOk, if (ok) ms else -1, System.currentTimeMillis())
                            refreshEntries(context)
                        }
                    }.awaitAll()
                }
                persistStatuses(context)
            } finally {
                _checking.value = false
            }
        }
    }

    /**
     * PingProxy as a suspend call; returns (ok, realLatencyMs). PingProxy reports the
     * actual round-trip ping (like Telegram's ~tens-of-ms numbers), unlike measuring
     * TestProxy wall-time which folds in the whole TLS+DC handshake (seconds).
     */
    private suspend fun pingOne(c: Candidate): Pair<Boolean, Int> {
        val deferred = CompletableDeferred<Pair<Boolean, Int>>()
        TdClient.send(TdApi.PingProxy(proxyOf(c))) { res ->
            if (res is TdApi.Seconds) deferred.complete(true to (res.seconds * 1000).toInt())
            else deferred.complete(false to -1)
        }
        return withTimeoutOrNull(PING_TIMEOUT_MS) { deferred.await() } ?: (false to -1)
    }

    /** Make a specific candidate the single enabled proxy. */
    private fun enableExact(context: Context, c: Candidate) {
        _activeKey.value = c.key
        TdClient.send(TdApi.GetProxies()) { result ->
            val proxies = (result as? TdApi.AddedProxies)?.proxies.orEmpty().filterNotNull()
            val existing = proxies.firstOrNull {
                it.proxy.server == c.host && it.proxy.port == c.port &&
                    it.proxy.type is TdApi.ProxyTypeMtproto
            }
            proxies.filter { it !== existing }.forEach { TdClient.send(TdApi.RemoveProxy(it.id)) }
            if (existing != null) {
                if (!existing.isEnabled) TdClient.send(TdApi.EnableProxy(existing.id))
            } else {
                TdClient.send(TdApi.AddProxy(proxyOf(c), true, "Fork"))
            }
        }
    }

    // ---------- User actions (settings screen) ----------

    /** User pinned a proxy: persist as preferred and enable it now. */
    fun selectManual(context: Context, entry: ProxyEntry) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PREFERRED, entry.key).apply()
        enableExact(context, Candidate(entry.host, entry.port, entry.secret))
        refreshEntries(context)
    }

    /** Clear the pin and return to the primary. */
    fun clearPin(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_PREFERRED).apply()
        primaryCandidate(context)?.let { enableExact(context, it) }
        refreshEntries(context)
    }

    /** Add a proxy from a tg://proxy or t.me/proxy link. Returns true if parsed. */
    fun addManual(context: Context, link: String): Boolean {
        val c = parseProxies(link).firstOrNull() ?: return false
        val saved = loadSaved(context).toMutableList()
        if (saved.none { it.key == c.key }) {
            saved.add(0, c)
            saveCandidates(context, saved, markManual = setOf(c.key))
        }
        refreshEntries(context)
        return true
    }

    fun removeEntry(context: Context, entry: ProxyEntry) {
        val saved = loadSaved(context).filterNot { it.key == entry.key }
        saveCandidates(context, saved, markManual = manualKeys(context) - entry.key)
        status.remove(entry.key)
        refreshEntries(context)
    }

    // ---------- Channel refresh ----------

    /** Read the channel, parse links, test them, persist working ones. */
    fun refreshFromChannel(context: Context) {
        TdClient.send(TdApi.SearchPublicChat(PROXY_CHANNEL)) { chatResult ->
            val chat = chatResult as? TdApi.Chat ?: return@send
            TdClient.send(TdApi.OpenChat(chat.id))
            TdClient.send(TdApi.GetChatHistory(chat.id, 0, 0, 80, false)) { histResult ->
                val messages = (histResult as? TdApi.Messages)?.messages.orEmpty().filterNotNull()
                val parsed = LinkedHashSet<Candidate>()
                for (m in messages) {
                    val text = (m.content as? TdApi.MessageText)?.text?.text ?: continue
                    parsed += parseProxies(text)
                }
                TdClient.send(TdApi.CloseChat(chat.id))
                if (parsed.isEmpty()) return@send
                val toTest = parsed.take(MAX_POOL)
                scope.launch {
                    val good = mutableListOf<Candidate>()
                    for (batch in toTest.chunked(TEST_BATCH)) {
                        batch.map { cand ->
                            async {
                                val (ok, ms) = pingOne(cand)
                                status[cand.key] = Triple(ok, if (ok) ms else -1, System.currentTimeMillis())
                                if (ok) synchronized(good) { good += cand }
                            }
                        }.awaitAll()
                    }
                    mergeIntoPool(context, good)
                    refreshEntries(context)
                    Log.i(TAG, "channel refresh: ${good.size} working proxies saved")
                }
            }
        }
    }

    private fun parseProxies(text: String): List<Candidate> {
        val regex = Regex("""server=([^&\s]+)&port=(\d+)&secret=([0-9a-fA-F]+)""")
        return regex.findAll(text).mapNotNull { m ->
            val (host, portStr, secret) = m.destructured
            val port = portStr.toIntOrNull() ?: return@mapNotNull null
            Candidate(host, port, secret)
        }.toList()
    }

    // ---------- Persistence ----------

    private fun pinned(context: Context): Candidate? {
        val key = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PREFERRED, null) ?: return null
        return candidates(context).firstOrNull { it.key == key }
    }

    private fun isManual(context: Context, key: String): Boolean = key in manualKeys(context)

    private fun manualKeys(context: Context): Set<String> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LIST, null)
            ?: return emptySet()
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            if (o.optBoolean("manual", false)) "${o.getString("host")}:${o.getInt("port")}" else null
        }.toSet()
    }.getOrDefault(emptySet())

    private fun loadSaved(context: Context): List<Candidate> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LIST, null)
            ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            // hydrate status cache from persistence
            if (o.has("ok")) {
                status.putIfAbsent(
                    "${o.getString("host")}:${o.getInt("port")}",
                    Triple(o.getBoolean("ok"), o.optInt("latency", -1), o.optLong("checked", 0L)),
                )
            }
            Candidate(o.getString("host"), o.getInt("port"), o.getString("secret"))
        }
    }.getOrDefault(emptyList())

    private fun mergeIntoPool(context: Context, fresh: List<Candidate>) {
        val merged = LinkedHashMap<String, Candidate>()
        fresh.forEach { merged[it.key] = it }
        loadSaved(context).forEach { merged.putIfAbsent(it.key, it) }
        saveCandidates(context, merged.values.take(MAX_POOL), markManual = manualKeys(context))
    }

    private fun saveCandidates(context: Context, list: List<Candidate>, markManual: Set<String>) {
        val arr = JSONArray()
        list.take(MAX_POOL).forEach { c ->
            val o = JSONObject()
                .put("host", c.host).put("port", c.port).put("secret", c.secret)
            if (c.key in markManual) o.put("manual", true)
            status[c.key]?.let { (ok, lat, t) ->
                o.put("ok", ok).put("latency", lat).put("checked", t)
            }
            arr.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LIST, arr.toString()).apply()
    }

    private fun persistStatuses(context: Context) {
        saveCandidates(context, loadSaved(context), markManual = manualKeys(context))
    }
}
