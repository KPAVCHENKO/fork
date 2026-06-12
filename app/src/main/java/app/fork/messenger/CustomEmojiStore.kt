package app.fork.messenger

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/**
 * Resolves custom/premium emoji (TextEntityTypeCustomEmoji) to their stickers so they
 * can be drawn inline in message text. Stickers are fetched in batches via TDLib and
 * cached; [version] ticks when new ones arrive so the UI re-renders.
 */
object CustomEmojiStore {
    private val cache = ConcurrentHashMap<Long, TdApi.Sticker>()
    private val requested = ConcurrentHashMap.newKeySet<Long>()
    private val pending = ArrayList<Long>()

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    /** Cached sticker for a custom emoji id, requesting it (batched) if unknown. */
    fun sticker(customEmojiId: Long): TdApi.Sticker? {
        cache[customEmojiId]?.let { return it }
        if (requested.add(customEmojiId)) enqueue(customEmojiId)
        return null
    }

    @Synchronized
    private fun enqueue(id: Long) {
        pending.add(id)
        if (pending.size >= 20) flush() else schedule()
    }

    @Volatile private var flushScheduled = false

    @Synchronized
    private fun schedule() {
        if (flushScheduled) return
        flushScheduled = true
        // Coalesce a burst of ids (one message often has several) into one request.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ flush() }, 120)
    }

    @Synchronized
    private fun flush() {
        flushScheduled = false
        if (pending.isEmpty()) return
        val ids = pending.toLongArray()
        pending.clear()
        TdClient.send(TdApi.GetCustomEmojiStickers(ids)) { res ->
            if (res is TdApi.Stickers) {
                res.stickers.orEmpty().filterNotNull().forEach { st ->
                    val cid = (st.fullType as? TdApi.StickerFullTypeCustomEmoji)?.customEmojiId
                    if (cid != null) cache[cid] = st
                }
                _version.value++
            }
        }
    }
}
