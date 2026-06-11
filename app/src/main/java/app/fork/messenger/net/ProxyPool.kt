package app.fork.messenger.net

import android.content.Context
import android.util.Log
import app.fork.messenger.BuildConfig
import app.fork.messenger.TdClient
import java.util.concurrent.atomic.AtomicBoolean
import org.drinkless.tdlib.TdApi
import org.json.JSONArray
import org.json.JSONObject

/**
 * Пул MTProto-прокси: несколько кандидатов (зашитый + подобранные из канала),
 * автоматический выбор САМОГО БЫСТРОГО рабочего через TDLib TestProxy и
 * авто-переключение, если активный перестал отвечать.
 *
 * Зачем:
 *  - скорость: при старте параллельно тестируем все прокси и включаем тот, что
 *    ответил первым (а не ждём один зашитый);
 *  - надёжность/обход: если в сети белые списки или включён VPN, рабочим
 *    окажется не каждый прокси — берём тот, что проходит именно сейчас;
 *  - без перебоев: список пополняется свежими прокси из публичного канала.
 */
object ProxyPool {
    private const val TAG = "ProxyPool"
    private const val PREFS = "proxy_pool"
    private const val KEY_LIST = "list"
    private const val MAX_POOL = 24
    private const val MAX_TESTED = 10
    private const val TEST_DC = 2
    private const val TEST_TIMEOUT = 8.0

    /** Публичный канал с рабочими прокси (пользователь). */
    private const val PROXY_CHANNEL = "telemtfreeproxy"

    data class Candidate(val host: String, val port: Int, val secret: String)

    @Volatile
    private var selecting = false

    /** Кандидаты: зашитый при сборке + сохранённые из канала, без дублей. */
    fun candidates(context: Context): List<Candidate> {
        val result = LinkedHashSet<Candidate>()
        // Сохранённый с GitHub адрес (ProxyConfig) или зашитый — первым.
        val primary = ProxyConfig.current(context)
        if (primary.host.isNotBlank() && primary.port != 0 && primary.secret.isNotBlank()) {
            result += Candidate(primary.host, primary.port, primary.secret)
        }
        if (BuildConfig.PROXY_HOST.isNotBlank() && BuildConfig.PROXY_PORT != 0) {
            result += Candidate(BuildConfig.PROXY_HOST, BuildConfig.PROXY_PORT, BuildConfig.PROXY_SECRET)
        }
        result += loadSaved(context)
        return result.toList()
    }

    private fun proxyOf(c: Candidate) = TdApi.Proxy(c.host, c.port, TdApi.ProxyTypeMtproto(c.secret))

    /**
     * Тестирует кандидатов параллельно и включает первый ответивший (самый быстрый).
     * Можно звать до авторизации (TestProxy это позволяет).
     */
    fun selectAndEnable(context: Context) {
        if (selecting) return
        val list = candidates(context).take(MAX_TESTED)
        if (list.isEmpty()) return
        if (list.size == 1) {
            enable(list.first())
            return
        }
        selecting = true
        val decided = AtomicBoolean(false)
        var pending = list.size

        list.forEach { candidate ->
            val started = System.currentTimeMillis()
            TdClient.send(TdApi.TestProxy(proxyOf(candidate), TEST_DC, TEST_TIMEOUT)) { result ->
                pending--
                val ok = result is TdApi.Ok
                if (ok && decided.compareAndSet(false, true)) {
                    val ms = System.currentTimeMillis() - started
                    Log.i(TAG, "selected ${candidate.host}:${candidate.port} (${ms}ms)")
                    enable(candidate)
                    selecting = false
                } else if (pending == 0 && !decided.get()) {
                    // Никто не прошёл тест — включаем основной, TDLib продолжит ретраи.
                    Log.w(TAG, "no proxy passed test, falling back to primary")
                    enable(list.first())
                    selecting = false
                }
            }
        }
    }

    /** Делает кандидата единственным включённым прокси. */
    private fun enable(c: Candidate) {
        TdClient.send(TdApi.GetProxies()) { result ->
            val proxies = (result as? TdApi.AddedProxies)?.proxies.orEmpty().filterNotNull()
            val existing = proxies.firstOrNull {
                it.proxy.server == c.host && it.proxy.port == c.port &&
                    it.proxy.type is TdApi.ProxyTypeMtproto
            }
            // Лишние прокси убираем, чтобы не накапливались.
            proxies.filter { it !== existing }.forEach { TdClient.send(TdApi.RemoveProxy(it.id)) }
            if (existing != null) {
                if (!existing.isEnabled) TdClient.send(TdApi.EnableProxy(existing.id))
            } else {
                TdClient.send(TdApi.AddProxy(proxyOf(c), true, "Fork"))
            }
        }
    }

    // ---------- Пополнение из публичного канала ----------

    /** Читает канал с прокси, парсит ссылки, проверяет TestProxy и сохраняет рабочие. */
    fun refreshFromChannel(context: Context) {
        TdClient.send(TdApi.SearchPublicChat(PROXY_CHANNEL)) { chatResult ->
            val chat = chatResult as? TdApi.Chat ?: return@send
            // Открываем канал, чтобы история подгрузилась с сервера.
            TdClient.send(TdApi.OpenChat(chat.id))
            TdClient.send(TdApi.GetChatHistory(chat.id, 0, 0, 60, false)) { histResult ->
                val messages = (histResult as? TdApi.Messages)?.messages.orEmpty().filterNotNull()
                val parsed = LinkedHashSet<Candidate>()
                for (m in messages) {
                    val text = (m.content as? TdApi.MessageText)?.text?.text ?: continue
                    parsed += parseProxies(text)
                }
                TdClient.send(TdApi.CloseChat(chat.id))
                if (parsed.isEmpty()) return@send
                // Проверяем каждый и сохраняем прошедшие тест.
                val good = java.util.Collections.synchronizedList(mutableListOf<Candidate>())
                var pending = parsed.size
                parsed.forEach { cand ->
                    TdClient.send(TdApi.TestProxy(proxyOf(cand), TEST_DC, TEST_TIMEOUT)) { res ->
                        pending--
                        if (res is TdApi.Ok) good += cand
                        if (pending == 0 && good.isNotEmpty()) {
                            persist(context, good)
                            Log.i(TAG, "channel refresh: ${good.size} working proxies saved")
                        }
                    }
                }
            }
        }
    }

    /** Достаёт server/port/secret из текста (формат tg://proxy?server=..&port=..&secret=..). */
    private fun parseProxies(text: String): List<Candidate> {
        val regex = Regex("""server=([^&\s]+)&port=(\d+)&secret=([0-9a-fA-F]+)""")
        return regex.findAll(text).mapNotNull { m ->
            val (host, portStr, secret) = m.destructured
            val port = portStr.toIntOrNull() ?: return@mapNotNull null
            Candidate(host, port, secret)
        }.toList()
    }

    // ---------- Хранение пула ----------

    private fun loadSaved(context: Context): List<Candidate> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LIST, null)
            ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Candidate(o.getString("host"), o.getInt("port"), o.getString("secret"))
        }
    }.getOrDefault(emptyList())

    private fun persist(context: Context, fresh: List<Candidate>) {
        val merged = LinkedHashSet<Candidate>().apply {
            addAll(fresh)
            addAll(loadSaved(context))
        }.take(MAX_POOL)
        val arr = JSONArray()
        merged.forEach {
            arr.put(JSONObject().put("host", it.host).put("port", it.port).put("secret", it.secret))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LIST, arr.toString()).apply()
    }
}
