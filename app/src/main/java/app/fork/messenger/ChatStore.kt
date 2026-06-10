package app.fork.messenger

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/** Готовая к показу строка списка чатов. */
data class UiChat(
    val id: Long,
    val title: String,
    val preview: String,
    val time: String,
    val unread: Int,
    val isPinned: Boolean,
    val isMuted: Boolean,
    val isChannel: Boolean,
    val avatarPath: String?,
    val initials: String,
    val colorSeed: Long,
)

/**
 * Держит все чаты основного списка и собирает из них отсортированный список для UI.
 * Все мутации — под общим замком, наружу уходит только неизменяемый снимок.
 */
object ChatStore {
    private val lock = Any()
    private val chats = HashMap<Long, TdApi.Chat>()
    private val requestedAvatars = HashSet<Int>()

    private val _chatList = MutableStateFlow<List<UiChat>>(emptyList())
    val chatList: StateFlow<List<UiChat>> = _chatList.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Счётчик изменений — растёт при любом обновлении, чтобы поиск перерисовывал результаты. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** Загружает основной список чатов до конца (TDLib отвечает 404, когда чаты закончились). */
    fun loadChats() {
        TdClient.send(TdApi.LoadChats(TdApi.ChatListMain(), 50)) { result ->
            when {
                result is TdApi.Ok -> loadChats()
                else -> _loading.value = false // 404 = всё загружено
            }
        }
    }

    fun chat(chatId: Long): TdApi.Chat? = synchronized(lock) { chats[chatId] }

    /** Готовая строка для UI по id чата (например, для результатов поиска). */
    fun uiFor(chatId: Long): UiChat? = synchronized(lock) { chats[chatId] }?.let { toUi(it) }

    /** Пересобрать список (например, когда подтянулись имена пользователей). */
    fun invalidate() = rebuild()

    fun handleUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateNewChat -> {
                synchronized(lock) { chats[obj.chat.id] = obj.chat }
                rebuild()
            }

            is TdApi.UpdateChatTitle -> mutate(obj.chatId) { it.title = obj.title }

            is TdApi.UpdateChatPhoto -> mutate(obj.chatId) { it.photo = obj.photo }

            is TdApi.UpdateChatLastMessage -> mutate(obj.chatId) {
                it.lastMessage = obj.lastMessage
                if (obj.positions.isNotEmpty()) it.positions = obj.positions
            }

            is TdApi.UpdateChatPosition -> mutate(obj.chatId) { chat ->
                val rest = chat.positions.filterNot { p -> p.list.javaClass == obj.position.list.javaClass }
                chat.positions = (rest + obj.position).toTypedArray()
            }

            is TdApi.UpdateChatReadInbox -> mutate(obj.chatId) {
                it.unreadCount = obj.unreadCount
                it.lastReadInboxMessageId = obj.lastReadInboxMessageId
            }

            is TdApi.UpdateChatNotificationSettings -> mutate(obj.chatId) {
                it.notificationSettings = obj.notificationSettings
            }

            is TdApi.UpdateFile -> onFile(obj.file)
        }
    }

    private inline fun mutate(chatId: Long, block: (TdApi.Chat) -> Unit) {
        val known = synchronized(lock) {
            chats[chatId]?.also(block) != null
        }
        if (known) rebuild()
    }

    /** Файл докачался — если это чья-то аватарка, обновляем чат. */
    private fun onFile(file: TdApi.File) {
        if (!file.local.isDownloadingCompleted) return
        var touched = false
        synchronized(lock) {
            for (chat in chats.values) {
                val small = chat.photo?.small ?: continue
                if (small.id == file.id) {
                    chat.photo?.small = file
                    touched = true
                }
            }
        }
        if (touched) rebuild()
    }

    private fun mainOrder(chat: TdApi.Chat): Long =
        chat.positions.firstOrNull { it.list is TdApi.ChatListMain }?.order ?: 0L

    private fun rebuild() {
        val snapshot = synchronized(lock) {
            chats.values
                .filter { mainOrder(it) != 0L }
                .sortedByDescending { mainOrder(it) }
                .map { toUi(it) }
        }
        _chatList.value = snapshot
        _revision.value++
    }

    private fun toUi(chat: TdApi.Chat): UiChat {
        val small = chat.photo?.small
        val avatarPath = small?.local?.takeIf { it.isDownloadingCompleted }?.path
        if (small != null && avatarPath == null && requestedAvatars.add(small.id)) {
            TdClient.send(TdApi.DownloadFile(small.id, 1, 0, 0, false))
        }

        val type = chat.type
        val isChannel = type is TdApi.ChatTypeSupergroup && type.isChannel
        val isGroup = type is TdApi.ChatTypeBasicGroup || (type is TdApi.ChatTypeSupergroup && !type.isChannel)

        val last = chat.lastMessage
        val senderPrefix = when {
            last == null || isChannel -> ""
            last.isOutgoing -> "Вы: "
            isGroup -> {
                val senderId = (last.senderId as? TdApi.MessageSenderUser)?.userId
                senderId?.let { UserCache.firstName(it)?.plus(": ") } ?: ""
            }
            else -> ""
        }

        return UiChat(
            id = chat.id,
            title = chat.title.ifBlank { "Без названия" },
            preview = senderPrefix + MessageFormat.contentText(last?.content),
            time = MessageFormat.listTime(last?.date ?: 0),
            unread = chat.unreadCount,
            isPinned = chat.positions.any { it.list is TdApi.ChatListMain && it.isPinned },
            isMuted = (chat.notificationSettings?.takeIf { !it.useDefaultMuteFor }?.muteFor ?: 0) > 0,
            isChannel = isChannel,
            avatarPath = avatarPath,
            initials = MessageFormat.initials(chat.title),
            colorSeed = chat.id,
        )
    }
}
