package app.fork.messenger

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Запрос на пересылку: какие сообщения и из какого чата. */
data class ForwardRequest(val fromChatId: Long, val messageIds: LongArray) {
    override fun equals(other: Any?) = other is ForwardRequest &&
        fromChatId == other.fromChatId && messageIds.contentEquals(other.messageIds)
    override fun hashCode() = 31 * fromChatId.hashCode() + messageIds.contentHashCode()
}

/** Шина пересылки: сообщение инициирует пересылку, навигация показывает выбор чата. */
object ForwardBus {
    private val _request = MutableStateFlow<ForwardRequest?>(null)
    val request: StateFlow<ForwardRequest?> = _request.asStateFlow()

    fun start(fromChatId: Long, messageIds: LongArray) {
        if (fromChatId == 0L || messageIds.isEmpty()) return
        _request.value = ForwardRequest(fromChatId, messageIds)
    }

    fun clear() {
        _request.value = null
    }
}
