package app.fork.messenger.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.fork.messenger.ChatStore
import app.fork.messenger.MainActivity
import app.fork.messenger.MessageFormat
import app.fork.messenger.MessageStore
import app.fork.messenger.R
import app.fork.messenger.UserCache
import org.drinkless.tdlib.TdApi

/**
 * Локальные уведомления о новых сообщениях (без FCM). Слушает updateNewMessage,
 * фильтрует свои/открытые/беззвучные чаты и показывает уведомление по чату.
 */
object NotificationsCenter {
    const val CHANNEL_SERVICE = "fork_service"
    const val CHANNEL_MESSAGES = "fork_messages"
    const val EXTRA_CHAT_ID = "chat_id"

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_MESSAGES, "Сообщения", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                },
            )
            // Канал старого фонового сервиса больше не нужен (перешли на FCM-пуши).
            runCatching { manager.deleteNotificationChannel(CHANNEL_SERVICE) }
        }
    }

    fun handleUpdate(obj: TdApi.Object) {
        if (obj is TdApi.UpdateNewMessage) onNewMessage(obj.message)
    }

    private fun onNewMessage(message: TdApi.Message) {
        val context = appContext ?: return
        if (!app.fork.messenger.SettingsStore.notificationsEnabled.value) return
        if (message.isOutgoing) return
        // Не шумим про чат, который сейчас открыт.
        if (MessageStore.isViewing(message.chatId)) return

        val chat = ChatStore.chat(message.chatId) ?: return
        // Уважаем «без звука».
        val muted = (chat.notificationSettings?.takeIf { !it.useDefaultMuteFor }?.muteFor ?: 0) > 0
        if (muted) return

        val isGroup = chat.type is TdApi.ChatTypeBasicGroup ||
            (chat.type is TdApi.ChatTypeSupergroup && !(chat.type as TdApi.ChatTypeSupergroup).isChannel)

        val body = MessageFormat.contentText(message.content)
        val text = if (isGroup) {
            val senderId = (message.senderId as? TdApi.MessageSenderUser)?.userId
            val name = senderId?.let { UserCache.firstName(it) }
            if (name != null) "$name: $body" else body
        } else body

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CHAT_ID, message.chatId)
        }
        val pending = PendingIntent.getActivity(
            context,
            message.chatId.toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setContentTitle(chat.title.ifBlank { "Сообщение" })
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(message.chatId.toInt(), notification)
        }
    }

    /** Убирает уведомления чата, когда пользователь его открыл. */
    fun clearForChat(chatId: Long) {
        val context = appContext ?: return
        NotificationManagerCompat.from(context).cancel(chatId.toInt())
    }
}
