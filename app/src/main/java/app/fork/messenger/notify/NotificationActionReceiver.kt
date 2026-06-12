package app.fork.messenger.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import app.fork.messenger.MessageStore

/**
 * Обрабатывает действия из уведомления (как в Telegram): быстрый ответ и «Прочитано».
 * Процесс приложения жив (TDLib работает), поэтому отправка идёт сразу.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getLongExtra(NotificationsCenter.EXTRA_CHAT_ID, 0L)
        if (chatId == 0L) return
        when (intent.action) {
            NotificationsCenter.ACTION_REPLY -> {
                val reply = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(NotificationsCenter.KEY_REPLY_TEXT)?.toString()?.trim()
                if (!reply.isNullOrBlank()) {
                    MessageStore.sendTextTo(chatId, reply)
                    NotificationsCenter.onReplySent(chatId, reply)
                }
                MessageStore.markChatRead(chatId)
            }
            NotificationsCenter.ACTION_MARK_READ -> {
                MessageStore.markChatRead(chatId)
                NotificationsCenter.clearForChat(chatId)
            }
        }
    }
}
