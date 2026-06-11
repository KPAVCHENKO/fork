package app.fork.messenger

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

/** Тип чата (для текстов меню: «Удалить» vs «Покинуть»). */
enum class ChatKind { PRIVATE, GROUP, CHANNEL }

/** Папка чатов пользователя (настоящая папка Telegram). */
data class UiFolder(val id: Int, val title: String)

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
    val kind: ChatKind,
    val avatarPath: String?,
    val initials: String,
    val colorSeed: Long,
    val isOnline: Boolean,
    val previewThumb: ImageBitmap? = null,
)

/**
 * Держит все чаты и собирает из них списки для UI (основной, архив, папки).
 *
 * ВАЖНО: апдейты TDLib приходят на единственном потоке "TDLib thread" — на нём же
 * доставляются ответы на все запросы (открытие чата, история…). Поэтому здесь
 * мутации словаря мгновенные, а дорогая пересборка списков выполняется на фоновом
 * потоке и коалесцируется: шквал из тысяч апдейтов первичной синхронизации
 * превращается в считанные пересборки, и поток TDLib никогда не блокируется.
 */
object ChatStore {
    private val lock = Any()
    private val chats = HashMap<Long, TdApi.Chat>()
    private val requestedAvatars = HashSet<Int>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val rebuildRequests = Channel<Unit>(Channel.CONFLATED)

    private val _chatList = MutableStateFlow<List<UiChat>>(emptyList())
    val chatList: StateFlow<List<UiChat>> = _chatList.asStateFlow()

    private val _archiveList = MutableStateFlow<List<UiChat>>(emptyList())
    val archiveList: StateFlow<List<UiChat>> = _archiveList.asStateFlow()

    /** Папки пользователя из Telegram (вкладки списка чатов). */
    private val _folders = MutableStateFlow<List<UiFolder>>(emptyList())
    val folders: StateFlow<List<UiFolder>> = _folders.asStateFlow()

    /** Списки чатов по папкам: folderId -> чаты в порядке папки. */
    private val _folderChats = MutableStateFlow<Map<Int, List<UiChat>>>(emptyMap())
    val folderChats: StateFlow<Map<Int, List<UiChat>>> = _folderChats.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Счётчик изменений — растёт при любом обновлении, чтобы поиск перерисовывал результаты. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** Кэш декодированных мини-превью последних сообщений (ключ — id сообщения). */
    private val thumbCache = ConcurrentHashMap<Long, ImageBitmap>()

    init {
        scope.launch {
            for (unused in rebuildRequests) {
                doRebuild()
                // Окно коалесценции: при первичной синхронизации тысячи апдейтов
                // сливаются в ~15 пересборок в секунду вместо тысяч.
                delay(64)
            }
        }
    }

    /** Загружает основной список чатов до конца (TDLib отвечает 404, когда чаты закончились). */
    fun loadChats() {
        TdClient.send(TdApi.LoadChats(TdApi.ChatListMain(), 50)) { result ->
            when {
                result is TdApi.Ok -> loadChats()
                else -> _loading.value = false // 404 = всё загружено
            }
        }
    }

    /** Загружает архивный список чатов. */
    fun loadArchive() {
        TdClient.send(TdApi.LoadChats(TdApi.ChatListArchive(), 50)) { result ->
            if (result is TdApi.Ok) loadArchive()
        }
    }

    /** Загружает чаты конкретной папки (иначе у чатов нет позиций в ней). */
    private fun loadFolder(folderId: Int) {
        TdClient.send(TdApi.LoadChats(TdApi.ChatListFolder(folderId), 50)) { result ->
            if (result is TdApi.Ok) loadFolder(folderId)
        }
    }

    // ---------- Действия с чатом ----------

    fun archive(chatId: Long, archived: Boolean) {
        val list: TdApi.ChatList = if (archived) TdApi.ChatListArchive() else TdApi.ChatListMain()
        TdClient.send(TdApi.AddChatToList(chatId, list))
    }

    fun togglePin(chatId: Long) {
        val chat = chat(chatId) ?: return
        val pinned = chat.positions.any { it.list is TdApi.ChatListMain && it.isPinned }
        TdClient.send(TdApi.ToggleChatIsPinned(TdApi.ChatListMain(), chatId, !pinned))
    }

    fun toggleMute(chatId: Long) {
        val chat = chat(chatId) ?: return
        val muted = (chat.notificationSettings?.takeIf { !it.useDefaultMuteFor }?.muteFor ?: 0) > 0
        val settings = (chat.notificationSettings ?: TdApi.ChatNotificationSettings()).also {
            it.useDefaultMuteFor = false
            it.muteFor = if (muted) 0 else 500_000_000
        }
        TdClient.send(TdApi.SetChatNotificationSettings(chatId, settings))
    }

    fun deleteOrLeave(chatId: Long) {
        val chat = chat(chatId) ?: return
        when (chat.type) {
            is TdApi.ChatTypeBasicGroup, is TdApi.ChatTypeSupergroup -> TdClient.send(TdApi.LeaveChat(chatId))
            else -> TdClient.send(TdApi.DeleteChatHistory(chatId, true, false))
        }
    }

    fun chat(chatId: Long): TdApi.Chat? = synchronized(lock) { chats[chatId] }

    /** Готовая строка для UI по id чата (например, для результатов поиска). */
    fun uiFor(chatId: Long): UiChat? = synchronized(lock) { chats[chatId] }?.let { toUi(it) }

    /** Запросить пересборку списков (дёшево: складывается в conflated-канал). */
    fun invalidate() {
        rebuildRequests.trySend(Unit)
    }

    fun handleUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateNewChat -> {
                synchronized(lock) { chats[obj.chat.id] = obj.chat }
                invalidate()
            }

            is TdApi.UpdateChatFolders -> {
                val infos = obj.chatFolders.orEmpty().filterNotNull()
                _folders.value = infos.map { UiFolder(it.id, it.name?.text?.text ?: "Папка") }
                infos.forEach { loadFolder(it.id) }
                invalidate()
            }

            is TdApi.UpdateChatTitle -> mutate(obj.chatId) { it.title = obj.title }

            is TdApi.UpdateChatPhoto -> mutate(obj.chatId) { it.photo = obj.photo }

            is TdApi.UpdateChatLastMessage -> mutate(obj.chatId) {
                it.lastMessage = obj.lastMessage
                if (obj.positions.isNotEmpty()) it.positions = obj.positions
            }

            is TdApi.UpdateChatPosition -> mutate(obj.chatId) { chat ->
                // Заменяем позицию ТОЛЬКО того же списка: у папок один класс,
                // но разные id — сравнение по классу стирало позиции других папок.
                val rest = chat.positions.filterNot { p -> sameList(p.list, obj.position.list) }
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

    private fun sameList(a: TdApi.ChatList, b: TdApi.ChatList): Boolean = when (a) {
        is TdApi.ChatListMain -> b is TdApi.ChatListMain
        is TdApi.ChatListArchive -> b is TdApi.ChatListArchive
        is TdApi.ChatListFolder -> b is TdApi.ChatListFolder && a.chatFolderId == b.chatFolderId
        else -> false
    }

    private inline fun mutate(chatId: Long, block: (TdApi.Chat) -> Unit) {
        val known = synchronized(lock) {
            chats[chatId]?.also(block) != null
        }
        if (known) invalidate()
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
        if (touched) invalidate()
    }

    private fun mainOrder(chat: TdApi.Chat): Long =
        chat.positions.firstOrNull { it.list is TdApi.ChatListMain }?.order ?: 0L

    private fun archiveOrder(chat: TdApi.Chat): Long =
        chat.positions.firstOrNull { it.list is TdApi.ChatListArchive }?.order ?: 0L

    private fun folderOrder(chat: TdApi.Chat, folderId: Int): Long =
        chat.positions.firstOrNull { (it.list as? TdApi.ChatListFolder)?.chatFolderId == folderId }?.order ?: 0L

    /** Полная пересборка всех списков. Выполняется ТОЛЬКО на фоновом потоке scope. */
    private fun doRebuild() {
        val snapshot = synchronized(lock) { chats.values.toList() }
        // Каждый чат конвертируем один раз, даже если он в нескольких списках.
        val uiCache = HashMap<Long, UiChat>()
        fun ui(c: TdApi.Chat): UiChat = uiCache.getOrPut(c.id) { toUi(c) }

        val main = snapshot.filter { mainOrder(it) != 0L }
            .sortedByDescending { mainOrder(it) }
            .map { ui(it) }
        val archive = snapshot.filter { archiveOrder(it) != 0L }
            .sortedByDescending { archiveOrder(it) }
            .map { ui(it) }
        val folderMap = _folders.value.associate { folder ->
            folder.id to snapshot.filter { folderOrder(it, folder.id) != 0L }
                .sortedByDescending { folderOrder(it, folder.id) }
                .map { ui(it) }
        }

        _chatList.value = main
        _archiveList.value = archive
        _folderChats.value = folderMap
        _revision.value++
        MessageStore.rebuildHeader()
    }

    /** Мини-превью (размытый JPEG ~40px) фото/видео последнего сообщения. */
    private fun miniThumb(message: TdApi.Message?): ImageBitmap? {
        message ?: return null
        val mini = when (val c = message.content) {
            is TdApi.MessagePhoto -> c.photo?.minithumbnail
            is TdApi.MessageVideo -> c.video?.minithumbnail
            is TdApi.MessageAnimation -> c.animation?.minithumbnail
            is TdApi.MessageDocument -> c.document?.minithumbnail
            else -> null
        } ?: return null
        val data = mini.data ?: return null
        thumbCache[message.id]?.let { return it }
        val bmp = runCatching {
            BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
        }.getOrNull() ?: return null
        if (thumbCache.size > 400) thumbCache.clear()
        thumbCache[message.id] = bmp
        return bmp
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
        val kind = when {
            isChannel -> ChatKind.CHANNEL
            isGroup -> ChatKind.GROUP
            else -> ChatKind.PRIVATE
        }

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

        // Если есть мини-эскиз, эмодзи-плейсхолдер не нужен — как в Telegram.
        val thumb = miniThumb(last)
        var previewText = MessageFormat.contentText(last?.content)
        if (thumb != null) {
            previewText = previewText
                .removePrefix("🖼 ").removePrefix("🎬 ").removePrefix("🎞 ")
        }

        return UiChat(
            id = chat.id,
            title = chat.title.ifBlank { "Без названия" },
            preview = senderPrefix + previewText,
            time = MessageFormat.listTime(last?.date ?: 0),
            unread = chat.unreadCount,
            isPinned = chat.positions.any { it.list is TdApi.ChatListMain && it.isPinned },
            isMuted = (chat.notificationSettings?.takeIf { !it.useDefaultMuteFor }?.muteFor ?: 0) > 0,
            isChannel = isChannel,
            kind = kind,
            avatarPath = avatarPath,
            initials = MessageFormat.initials(chat.title),
            colorSeed = chat.id,
            isOnline = (type as? TdApi.ChatTypePrivate)?.let { UserCache.isOnline(it.userId) } == true,
            previewThumb = thumb,
        )
    }
}
