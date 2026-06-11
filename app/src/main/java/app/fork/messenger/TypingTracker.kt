package app.fork.messenger

import java.util.concurrent.ConcurrentHashMap
import org.drinkless.tdlib.TdApi

/**
 * Индикаторы «печатает / записывает голосовое …». Принимает updateChatAction,
 * хранит активные действия с истечением по времени, отдаёт подпись для шапки.
 * Отправку собственного действия инициирует UI через [onUserActivity].
 */
object TypingTracker {
    private const val EXPIRY_MS = 6000L

    private data class Action(val userId: Long, val label: String, val at: Long)

    // chatId -> (userId -> действие)
    private val actions = ConcurrentHashMap<Long, ConcurrentHashMap<Long, Action>>()

    /** Вызывается при перерисовке шапки/строки чата, чтобы UI обновился. */
    @Volatile
    var onChanged: ((Long) -> Unit)? = null

    fun handleUpdate(obj: TdApi.Object) {
        if (obj !is TdApi.UpdateChatAction) return
        val userId = (obj.senderId as? TdApi.MessageSenderUser)?.userId ?: 0L
        val map = actions.getOrPut(obj.chatId) { ConcurrentHashMap() }
        if (obj.action is TdApi.ChatActionCancel) {
            map.remove(userId)
        } else {
            map[userId] = Action(userId, labelFor(obj.action), System.currentTimeMillis())
        }
        onChanged?.invoke(obj.chatId)
    }

    /** Подпись для шапки чата, либо null если никто не печатает. */
    fun label(chatId: Long, isGroup: Boolean): String? {
        val map = actions[chatId] ?: return null
        val now = System.currentTimeMillis()
        val active = map.values.filter { now - it.at < EXPIRY_MS }
        // Чистим протухшие.
        map.entries.removeAll { now - it.value.at >= EXPIRY_MS }
        if (active.isEmpty()) return null
        return if (!isGroup) {
            active.first().label
        } else {
            val names = active.mapNotNull { UserCache.firstName(it.userId) }
            when {
                names.isEmpty() -> active.first().label
                names.size == 1 -> "${names[0]} ${active.first().label}"
                else -> "${names.take(2).joinToString(", ")} печатают…"
            }
        }
    }

    private fun labelFor(action: TdApi.ChatAction): String = when (action) {
        is TdApi.ChatActionRecordingVoiceNote -> "записывает голосовое…"
        is TdApi.ChatActionRecordingVideoNote, is TdApi.ChatActionRecordingVideo -> "записывает видео…"
        is TdApi.ChatActionUploadingPhoto -> "отправляет фото…"
        is TdApi.ChatActionUploadingVideo, is TdApi.ChatActionUploadingVideoNote -> "отправляет видео…"
        is TdApi.ChatActionUploadingDocument -> "отправляет файл…"
        is TdApi.ChatActionChoosingSticker -> "выбирает стикер…"
        else -> "печатает…"
    }

    /** UI зовёт при наборе текста — шлём «печатает» серверу (не чаще раза в ~4 c). */
    private val lastSent = ConcurrentHashMap<Long, Long>()
    fun onUserActivity(chatId: Long) {
        val now = System.currentTimeMillis()
        if (now - (lastSent[chatId] ?: 0) < 4000) return
        lastSent[chatId] = now
        TdClient.send(TdApi.SendChatAction(chatId, null, null, TdApi.ChatActionTyping()))
    }
}
