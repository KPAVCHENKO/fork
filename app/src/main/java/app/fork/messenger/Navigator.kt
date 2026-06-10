package app.fork.messenger

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Запросы навигации извне UI (например, тап по уведомлению открывает нужный чат). */
object Navigator {
    private val _pendingChat = MutableStateFlow<Long?>(null)
    val pendingChat: StateFlow<Long?> = _pendingChat.asStateFlow()

    fun requestOpenChat(chatId: Long) {
        _pendingChat.value = chatId
    }

    fun consume() {
        _pendingChat.value = null
    }
}
