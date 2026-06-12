package app.fork.messenger.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import app.fork.messenger.ChatStore
import app.fork.messenger.MainActivity
import app.fork.messenger.MessageFormat
import app.fork.messenger.MessageStore
import app.fork.messenger.R
import app.fork.messenger.TdClient
import app.fork.messenger.UserCache
import java.util.concurrent.ConcurrentHashMap
import org.drinkless.tdlib.TdApi

/**
 * Локальные уведомления о новых сообщениях (без FCM). Слушает updateNewMessage,
 * фильтрует свои/открытые/беззвучные чаты и показывает уведомление по чату.
 */
object NotificationsCenter {
    const val CHANNEL_SERVICE = "fork_service"
    const val CHANNEL_MESSAGES = "fork_messages"
    const val EXTRA_CHAT_ID = "chat_id"
    // Группировка уведомлений (как в TG): все чаты под одной сводкой.
    private const val GROUP_KEY = "fork_messages_group"
    private const val SUMMARY_ID = 1
    const val ACTION_REPLY = "app.fork.messenger.REPLY"
    const val ACTION_MARK_READ = "app.fork.messenger.MARK_READ"
    const val KEY_REPLY_TEXT = "reply_text"

    private var appContext: Context? = null

    /** Накопленные сообщения по чату — чтобы уведомление показывало ВСЕ (MessagingStyle),
     *  как в Telegram, а не только последнее. */
    private data class NotifLine(val sender: String?, val text: String, val timeMs: Long)
    private val conversations = ConcurrentHashMap<Long, ArrayDeque<NotifLine>>()
    private const val MAX_LINES = 8

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
        val senderName = (message.senderId as? TdApi.MessageSenderUser)?.userId
            ?.let { UserCache.firstName(it) }
        // В 1:1 отправитель — собеседник (заголовок чата); в группе — реальный автор.
        val displaySender = if (isGroup) (senderName ?: chat.title) else chat.title

        val lines = conversations.getOrPut(message.chatId) { ArrayDeque() }
        synchronized(lines) {
            lines.addLast(NotifLine(displaySender, body, System.currentTimeMillis()))
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
        showConversation(context, chat, isGroup)
    }

    /**
     * Строит уведомление-беседу в стиле Telegram: MessagingStyle со всеми накопленными
     * сообщениями, аватар чата большой иконкой, действия «Ответить» (быстрый ввод) и
     * «Прочитано».
     */
    private fun showConversation(context: Context, chat: TdApi.Chat, isGroup: Boolean) {
        val buffer = conversations[chat.id] ?: return
        val snapshot = synchronized(buffer) { buffer.toList() }
        if (snapshot.isEmpty()) return

        val me = Person.Builder().setName("Вы").build()
        var style = NotificationCompat.MessagingStyle(me)
        if (isGroup) {
            style = style.setConversationTitle(chat.title).setGroupConversation(true)
        }
        snapshot.forEach { line ->
            // sender == null → это мой ответ (показывается как исходящее).
            val person = line.sender?.let { Person.Builder().setName(it).build() }
            style.addMessage(line.text, line.timeMs, person)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setStyle(style)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(avatarBitmap(chat))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_KEY)
            .setContentIntent(openChatIntent(context, chat.id))
            .addAction(replyAction(context, chat.id))
            .addAction(markReadAction(context, chat.id))
            .build()

        // Сводка группы: без неё Android 7+ не схлопывает уведомления разных чатов.
        val summary = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .build()

        runCatching {
            val nm = NotificationManagerCompat.from(context)
            nm.notify(chat.id.toInt(), notification)
            nm.notify(SUMMARY_ID, summary)
        }
    }

    /** Аватар чата большой иконкой (если уже скачан); иначе просим TDLib скачать. */
    private fun avatarBitmap(chat: TdApi.Chat): android.graphics.Bitmap? {
        val file = chat.photo?.small ?: return null
        val path = file.local?.path
        if (file.local?.isDownloadingCompleted == true && !path.isNullOrBlank()) {
            return runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }
        runCatching { TdClient.send(TdApi.DownloadFile(file.id, 16, 0, 0, false)) }
        return null
    }

    private fun openChatIntent(context: Context, chatId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CHAT_ID, chatId)
        }
        return PendingIntent.getActivity(
            context, chatId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun replyAction(context: Context, chatId: Long): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_REPLY_TEXT).setLabel("Сообщение…").build()
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_CHAT_ID, chatId)
        }
        val pending = PendingIntent.getBroadcast(
            context, chatId.toInt() * 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, R.drawable.ic_launcher_foreground), "Ответить", pending,
        ).addRemoteInput(remoteInput).setAllowGeneratedReplies(true).build()
    }

    private fun markReadAction(context: Context, chatId: Long): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_READ
            putExtra(EXTRA_CHAT_ID, chatId)
        }
        val pending = PendingIntent.getBroadcast(
            context, chatId.toInt() * 2 + 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, R.drawable.ic_launcher_foreground), "Прочитано", pending,
        ).build()
    }

    /** Быстрый ответ отправлен — показываем его в беседе как исходящее. */
    fun onReplySent(chatId: Long, text: String) {
        val context = appContext ?: return
        val chat = ChatStore.chat(chatId) ?: return
        val buffer = conversations.getOrPut(chatId) { ArrayDeque() }
        synchronized(buffer) {
            buffer.addLast(NotifLine(null, text, System.currentTimeMillis()))
            while (buffer.size > MAX_LINES) buffer.removeFirst()
        }
        showConversation(context, chat, scopeOf(chat) == NotificationPolicy.Scope.GROUP)
    }

    /** Убирает уведомления чата, когда пользователь его открыл. */
    fun clearForChat(chatId: Long) {
        conversations.remove(chatId)
        val context = appContext ?: return
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(chatId.toInt())
        if (conversations.isEmpty()) nm.cancel(SUMMARY_ID) // убрать сводку, если чатов больше нет
    }
}
