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

    // Cached scope default mute (TDLib pushes these via UpdateScopeNotificationSettings
    // at startup and on change). Used when a chat defers to its scope default.
    @Volatile private var privateScopeMuteFor = 0
    @Volatile private var groupScopeMuteFor = 0
    @Volatile private var channelScopeMuteFor = 0

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
        when (obj) {
            is TdApi.UpdateNewMessage -> onNewMessage(obj.message)
            is TdApi.UpdateScopeNotificationSettings -> onScopeSettings(obj)
        }
    }

    /** Cache the per-scope default mute so default-deferring chats resolve correctly. */
    private fun onScopeSettings(update: TdApi.UpdateScopeNotificationSettings) {
        val muteFor = update.notificationSettings?.muteFor ?: 0
        when (update.scope) {
            is TdApi.NotificationSettingsScopePrivateChats -> privateScopeMuteFor = muteFor
            is TdApi.NotificationSettingsScopeGroupChats -> groupScopeMuteFor = muteFor
            is TdApi.NotificationSettingsScopeChannelChats -> channelScopeMuteFor = muteFor
        }
    }

    private fun scopeOf(chat: TdApi.Chat): NotificationPolicy.Scope = when (val t = chat.type) {
        is TdApi.ChatTypeSupergroup -> if (t.isChannel) NotificationPolicy.Scope.CHANNEL
        else NotificationPolicy.Scope.GROUP
        is TdApi.ChatTypeBasicGroup -> NotificationPolicy.Scope.GROUP
        else -> NotificationPolicy.Scope.PRIVATE
    }

    private fun scopeMuteFor(scope: NotificationPolicy.Scope): Int = when (scope) {
        NotificationPolicy.Scope.PRIVATE -> privateScopeMuteFor
        NotificationPolicy.Scope.GROUP -> groupScopeMuteFor
        NotificationPolicy.Scope.CHANNEL -> channelScopeMuteFor
    }

    private fun onNewMessage(message: TdApi.Message) {
        val context = appContext ?: return
        if (message.isOutgoing) return

        val chat = ChatStore.chat(message.chatId) ?: return

        // Effective mute: per-chat setting overrides scope default (see NotificationPolicy).
        val settings = chat.notificationSettings
        val scope = scopeOf(chat)
        val muted = NotificationPolicy.isMuted(
            useDefaultMute = settings?.useDefaultMuteFor ?: true,
            chatMuteFor = settings?.muteFor ?: 0,
            scopeMuteFor = scopeMuteFor(scope),
        )
        val notify = NotificationPolicy.shouldNotify(
            globalEnabled = app.fork.messenger.SettingsStore.notificationsEnabled.value,
            isOutgoing = message.isOutgoing,
            isViewingChat = MessageStore.isViewing(message.chatId),
            muted = muted,
        )
        if (!notify) return

        val isGroup = scope == NotificationPolicy.Scope.GROUP

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
