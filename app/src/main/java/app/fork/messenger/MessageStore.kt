package app.fork.messenger

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

/** Готовое к показу сообщение. */
data class UiMessage(
    val id: Long,
    val text: String,
    val content: TdApi.MessageContent?,
    val time: String,
    val isMine: Boolean,
    val senderName: String?,
    val senderSeed: Long,
    val showSender: Boolean,
    val isFirstOfGroup: Boolean,
    val isLastOfGroup: Boolean,
    val dateLabel: String,
    val replyText: String?,
    val canDelete: Boolean,
    val canDeleteForAll: Boolean,
    val outStatus: OutStatus,
    val isEdited: Boolean = false,
    val isPinned: Boolean = false,
    val reactions: List<UiReaction> = emptyList(),
    /** Источник пересланного сообщения («канал/имя»), иначе null. */
    val forwardFrom: String? = null,
    /** Медиа альбома (несколько фото/видео одним постом); null для одиночных. */
    val albumMedia: List<AlbumItem> = emptyList(),
)

/** Один элемент альбома: контент + id сообщения (для открытия просмотрщика). */
data class AlbumItem(val messageId: Long, val content: TdApi.MessageContent)

/** Статус исходящего сообщения для галочек. */
enum class OutStatus { NONE, SENDING, FAILED, SENT, READ }

/** Реакция на сообщение для отрисовки чипа. */
data class UiReaction(val emoji: String, val count: Int, val chosen: Boolean)

/** Черновик ответа: на какое сообщение отвечаем и как его показать в превью. */
data class ReplyDraft(val messageId: Long, val preview: String, val sender: String?)

/** Закреплённое сообщение для плашки в шапке чата. */
data class PinnedBar(val messageId: Long, val text: String)

/** Результат поиска по сообщениям внутри чата. */
data class ChatSearchHit(val messageId: Long, val title: String, val snippet: String, val time: String)

/** Режим редактирования своего сообщения. */
data class EditDraft(val messageId: Long, val text: String)

/** Шапка открытого чата. */
data class ChatHeader(
    val title: String,
    val subtitle: String,
    val avatarPath: String?,
    val initials: String,
    val colorSeed: Long,
    val canWrite: Boolean,
    val userId: Long, // для приватных чатов, иначе 0
)

/**
 * Состояние открытого чата: история (по возрастанию id), отправка, realtime-апдейты.
 */
object MessageStore {
    private val lock = Any()
    private var chatId: Long = 0

    // Пересборка ленты — на фоне с коалесцией, чтобы шквал апдейтов
    // (реакции/прочтения в активной группе) не задерживал поток TDLib.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val rebuildRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (unused in rebuildRequests) {
                doRebuild()
                delay(32)
            }
        }
    }
    private var isGroup = false
    private var chatCanDeleteForSelf = false
    private var chatCanDeleteForAll = false
    private var lastReadOutbox = 0L
    private val msgs = ArrayList<TdApi.Message>() // отсортированы по возрастанию id

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    /** id чата, под который собрана текущая лента (фильтр от мелькания старого чата). */
    private val _openedChat = MutableStateFlow(0L)
    val openedChat: StateFlow<Long> = _openedChat.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _header = MutableStateFlow<ChatHeader?>(null)
    val header: StateFlow<ChatHeader?> = _header.asStateFlow()

    private val _loadingHistory = MutableStateFlow(false)
    val loadingHistory: StateFlow<Boolean> = _loadingHistory.asStateFlow()

    private val _editing = MutableStateFlow<EditDraft?>(null)
    val editing: StateFlow<EditDraft?> = _editing.asStateFlow()

    private val _reply = MutableStateFlow<ReplyDraft?>(null)
    val reply: StateFlow<ReplyDraft?> = _reply.asStateFlow()

    private val _pinned = MutableStateFlow<PinnedBar?>(null)
    val pinned: StateFlow<PinnedBar?> = _pinned.asStateFlow()

    /** Результаты поиска по текущему чату. */
    private val _searchHits = MutableStateFlow<List<ChatSearchHit>>(emptyList())
    val searchHits: StateFlow<List<ChatSearchHit>> = _searchHits.asStateFlow()

    /** id сообщения, к которому UI должен прокрутиться (после прыжка из поиска/закрепа). */
    private val _scrollTo = MutableStateFlow<Long?>(null)
    val scrollTo: StateFlow<Long?> = _scrollTo.asStateFlow()

    /** Черновик открытого чата (текст из Telegram, синхронизируется между устройствами). */
    private val _draftText = MutableStateFlow<String?>(null)
    val draftText: StateFlow<String?> = _draftText.asStateFlow()

    private var lastSavedDraft: String = ""

    /** true: показано «окно» истории не у самого низа (после прыжка к старому сообщению). */
    private val _detached = MutableStateFlow(false)
    val detached: StateFlow<Boolean> = _detached.asStateFlow()

    /** id первого непрочитанного сообщения (для разделителя «Непрочитанные»). */
    private val _firstUnread = MutableStateFlow(0L)
    val firstUnread: StateFlow<Long> = _firstUnread.asStateFlow()

    private var historyEnded = false

    fun open(id: Long) {
        synchronized(lock) {
            chatId = id
            msgs.clear()
            historyEnded = false
        }
        _messages.value = emptyList()
        _openedChat.value = id

        val chat = ChatStore.chat(id)
        _title.value = chat?.title ?: ""
        val type = chat?.type
        isGroup = type is TdApi.ChatTypeBasicGroup ||
            (type is TdApi.ChatTypeSupergroup && !type.isChannel)
        chatCanDeleteForSelf = chat?.canBeDeletedOnlyForSelf == true
        chatCanDeleteForAll = chat?.canBeDeletedForAllUsers == true
        lastReadOutbox = chat?.lastReadOutboxMessageId ?: 0L

        rebuildHeader()
        UserCache.onStatusChanged = { userId ->
            if (_header.value?.userId == userId) rebuildHeader()
        }
        TypingTracker.onChanged = { changedChat ->
            if (synchronized(lock) { chatId } == changedChat) rebuildHeader()
        }

        app.fork.messenger.notify.NotificationsCenter.clearForChat(id)
        TdClient.send(TdApi.OpenChat(id))
        _pinned.value = null
        _searchHits.value = emptyList()
        _scrollTo.value = null
        _detached.value = false
        // Reply/edit drafts are per-open: clear them so a swipe-to-reply in one chat
        // doesn't leak its reply bar into the next chat opened.
        _reply.value = null
        _editing.value = null
        val draft = ((chat?.draftMessage?.inputMessageText) as? TdApi.InputMessageText)?.text?.text
        _draftText.value = draft?.takeIf { it.isNotBlank() }
        lastSavedDraft = draft.orEmpty()
        refreshPinned(id)

        // Open at the FIRST UNREAD message (like Telegram), not at the very bottom.
        _firstUnread.value = 0L
        val unread = chat?.unreadCount ?: 0
        val lastReadInbox = chat?.lastReadInboxMessageId ?: 0L
        if (unread > 0 && lastReadInbox != 0L) {
            openAtUnread(id, lastReadInbox)
        } else {
            loadMore()
        }
    }

    /** Loads a window of history around the last-read boundary and scrolls to the
     *  first unread message (id greater than [lastReadInbox]). */
    private fun openAtUnread(id: Long, lastReadInbox: Long) {
        _detached.value = true
        _loadingHistory.value = true
        TdClient.send(TdApi.GetChatHistory(id, lastReadInbox, -20, 40, false)) { result ->
            _loadingHistory.value = false
            if (synchronized(lock) { chatId } != id) return@send
            val incoming = (result as? TdApi.Messages)?.messages.orEmpty().filterNotNull()
            if (incoming.isEmpty()) {
                _detached.value = false
                loadMore()
                return@send
            }
            synchronized(lock) {
                msgs.clear()
                msgs.addAll(incoming.sortedBy { it.id })
            }
            rebuild()
            val firstUnread = synchronized(lock) { msgs.firstOrNull { it.id > lastReadInbox }?.id }
            _firstUnread.value = firstUnread ?: 0L
            _scrollTo.value = firstUnread ?: synchronized(lock) { msgs.lastOrNull()?.id }
        }
    }

    /** Сохраняет черновик в Telegram (пустой текст очищает черновик). */
    fun saveDraft(text: String) {
        val id = synchronized(lock) { chatId }
        if (id == 0L || text == lastSavedDraft) return
        lastSavedDraft = text
        val draft = text.takeIf { it.isNotBlank() }?.let {
            TdApi.DraftMessage(
                null,
                (System.currentTimeMillis() / 1000).toInt(),
                TdApi.InputMessageText(TdApi.FormattedText(it, null), null, false),
                0,
                null,
            )
        }
        TdClient.send(TdApi.SetChatDraftMessage(id, null, draft))
    }

    // ---------- Поиск по чату и прыжок к сообщению ----------

    /** Ищет сообщения в текущем чате (серверный поиск TDLib). */
    fun searchInChat(query: String) {
        val id = synchronized(lock) { chatId }
        if (id == 0L || query.isBlank()) {
            _searchHits.value = emptyList()
            return
        }
        TdClient.send(TdApi.SearchChatMessages(id, null, query, null, 0, 0, 30, null)) { result ->
            if (synchronized(lock) { chatId } != id) return@send
            val found = (result as? TdApi.FoundChatMessages)?.messages.orEmpty().filterNotNull()
            _searchHits.value = found.map { m ->
                val senderId = (m.senderId as? TdApi.MessageSenderUser)?.userId ?: 0L
                ChatSearchHit(
                    messageId = m.id,
                    title = when {
                        m.isOutgoing -> "Вы"
                        senderId != 0L -> UserCache.firstName(senderId) ?: _title.value
                        else -> _title.value
                    },
                    snippet = MessageFormat.contentText(m.content),
                    time = MessageFormat.listTime(m.date),
                )
            }
        }
    }

    fun clearChatSearch() {
        _searchHits.value = emptyList()
    }

    /** UI прокрутился к цели — сбрасываем запрос. */
    fun consumeScrollTarget() {
        _scrollTo.value = null
    }

    /**
     * Прыжок к сообщению: если оно уже загружено — просто прокрутка,
     * иначе перечитываем «окно» истории вокруг него.
     */
    fun jumpTo(messageId: Long) {
        val id = synchronized(lock) { chatId }
        if (id == 0L) return
        val loaded = synchronized(lock) { msgs.any { it.id == messageId } }
        if (loaded) {
            _scrollTo.value = messageId
            return
        }
        synchronized(lock) {
            msgs.clear()
            historyEnded = false
        }
        _messages.value = emptyList()
        _detached.value = true
        _loadingHistory.value = true
        TdClient.send(TdApi.GetChatHistory(id, messageId, -15, 40, false)) { result ->
            _loadingHistory.value = false
            if (synchronized(lock) { chatId } != id) return@send
            val incoming = (result as? TdApi.Messages)?.messages.orEmpty().filterNotNull()
            synchronized(lock) {
                msgs.clear()
                msgs.addAll(incoming.sortedBy { it.id })
            }
            rebuild()
            _scrollTo.value = messageId
        }
    }

    /** Догружает более новые сообщения, когда показано «окно» истории. */
    fun loadNewer() {
        val id = synchronized(lock) { chatId }
        if (id == 0L || !_detached.value || _loadingHistory.value) return
        val from = synchronized(lock) { msgs.lastOrNull()?.id } ?: return
        _loadingHistory.value = true
        TdClient.send(TdApi.GetChatHistory(id, from, -30, 31, false)) { result ->
            _loadingHistory.value = false
            if (synchronized(lock) { chatId } != id) return@send
            val incoming = (result as? TdApi.Messages)?.messages.orEmpty()
                .filterNotNull()
                .filter { it.id > from }
            synchronized(lock) {
                val known = msgs.mapTo(HashSet()) { it.id }
                msgs.addAll(incoming.filter { it.id !in known })
                msgs.sortBy { it.id }
            }
            // Дошли до последнего сообщения чата — окно сомкнулось с настоящим.
            val lastId = ChatStore.chat(id)?.lastMessage?.id
            if (incoming.isEmpty() || (lastId != null && synchronized(lock) { msgs.lastOrNull()?.id } == lastId)) {
                _detached.value = false
            }
            rebuild()
        }
    }

    /** Возврат к самым свежим сообщениям (кнопка «вниз» после прыжка). */
    fun returnToLatest() {
        val id = synchronized(lock) { chatId }
        if (id == 0L) return
        _detached.value = false
        synchronized(lock) {
            msgs.clear()
            historyEnded = false
        }
        _messages.value = emptyList()
        loadMore()
    }

    /** Подтягивает последнее закреплённое сообщение чата (404 = закрепа нет). */
    private fun refreshPinned(id: Long) {
        TdClient.send(TdApi.GetChatPinnedMessage(id)) { result ->
            if (synchronized(lock) { chatId } != id) return@send
            _pinned.value = (result as? TdApi.Message)?.let {
                PinnedBar(it.id, MessageFormat.contentText(it.content))
            }
        }
    }

    /** Закрепляет/открепляет сообщение в текущем чате. */
    fun togglePinMessage(messageId: Long, pin: Boolean) {
        val id = synchronized(lock) { chatId }
        if (id == 0L) return
        if (pin) {
            TdClient.send(TdApi.PinChatMessage(id, messageId, true, false))
        } else {
            TdClient.send(TdApi.UnpinChatMessage(id, messageId))
        }
    }

    fun isViewing(id: Long): Boolean = synchronized(lock) { chatId == id }

    /** Отправляет текст в произвольный чат (для быстрого ответа из уведомления). */
    fun sendTextTo(chatId: Long, text: String) {
        if (chatId == 0L || text.isBlank()) return
        TdClient.send(
            TdApi.SendMessage().apply {
                this.chatId = chatId
                inputMessageContent = TdApi.InputMessageText(
                    TdApi.FormattedText(text.trim(), null), null, true,
                )
            },
        )
    }

    /** Помечает чат прочитанным по последнему сообщению (для действия в уведомлении). */
    fun markChatRead(chatId: Long) {
        val last = ChatStore.chat(chatId)?.lastMessage?.id ?: return
        TdClient.send(TdApi.ViewMessages(chatId, longArrayOf(last), null, true))
    }

    /** Очищает историю чата (у себя). */
    fun clearHistory(chatId: Long) {
        if (chatId == 0L) return
        TdClient.send(TdApi.DeleteChatHistory(chatId, false, false))
    }

    /** Включает/выключает звук чата (бессрочно). */
    fun toggleMute(chatId: Long, mute: Boolean) {
        if (chatId == 0L) return
        val cur = ChatStore.chat(chatId)?.notificationSettings ?: TdApi.ChatNotificationSettings()
        cur.useDefaultMuteFor = false
        cur.muteFor = if (mute) Int.MAX_VALUE else 0
        TdClient.send(TdApi.SetChatNotificationSettings(chatId, cur))
    }

    /** Помечает чат (не)прочитанным вручную. */
    fun toggleUnread(chatId: Long, unread: Boolean) {
        if (chatId == 0L) return
        TdClient.send(TdApi.ToggleChatIsMarkedAsUnread(chatId, unread))
    }

    /** Текущее состояние «без звука» для чата. */
    fun isMuted(chatId: Long): Boolean {
        val s = ChatStore.chat(chatId)?.notificationSettings ?: return false
        return !s.useDefaultMuteFor && s.muteFor > 0
    }

    /**
     * Marks messages up to [messageId] as read (forceRead), as the user scrolls them
     * into view. This is what clears the unread counter — opening at the unread anchor
     * no longer leaves the chat unread once you scroll through it.
     */
    fun markViewed(messageId: Long) {
        val id = synchronized(lock) { chatId }
        if (id == 0L || messageId == 0L) return
        TdClient.send(TdApi.ViewMessages(id, longArrayOf(messageId), null, true))
    }

    /** Пересобирает шапку чата (заголовок, статус, аватар, право писать). */
    fun rebuildHeader() {
        val id = synchronized(lock) { chatId }
        if (id == 0L) return
        val chat = ChatStore.chat(id) ?: return
        val type = chat.type

        val userId = (type as? TdApi.ChatTypePrivate)?.userId ?: 0L
        val isChannel = type is TdApi.ChatTypeSupergroup && type.isChannel
        val isGroupChat = type is TdApi.ChatTypeBasicGroup ||
            (type is TdApi.ChatTypeSupergroup && !type.isChannel)

        // «печатает…» имеет приоритет над обычным статусом (как в Telegram).
        val subtitle = TypingTracker.label(id, isGroupChat) ?: when {
            userId != 0L -> UserCache.statusText(userId)
            isChannel -> "канал"
            else -> "группа"
        }
        val canWrite = when {
            isChannel -> false // постинг в каналы доделаем для админов позже
            type is TdApi.ChatTypeBasicGroup || type is TdApi.ChatTypeSupergroup ->
                chat.permissions?.canSendBasicMessages != false
            else -> true
        }
        _header.value = ChatHeader(
            title = chat.title.ifBlank { "Без названия" },
            subtitle = subtitle,
            avatarPath = chat.photo?.small?.local?.takeIf { it.isDownloadingCompleted }?.path,
            initials = MessageFormat.initials(chat.title),
            colorSeed = chat.id,
            canWrite = canWrite,
            userId = userId,
        )
    }

    fun close() {
        val id = synchronized(lock) { chatId.also { chatId = 0 } }
        if (id != 0L) TdClient.send(TdApi.CloseChat(id))
    }

    /** Подгружает страницу истории сверху. TDLib может отдавать страницы меньше limit. */
    fun loadMore() {
        val id = synchronized(lock) { chatId }
        if (id == 0L || historyEnded || _loadingHistory.value) return
        _loadingHistory.value = true
        val from = synchronized(lock) { msgs.firstOrNull()?.id ?: 0L }

        TdClient.send(TdApi.GetChatHistory(id, from, 0, 40, false)) { result ->
            _loadingHistory.value = false
            if (result !is TdApi.Messages) return@send
            val incoming = result.messages.orEmpty().filterNotNull()
            if (incoming.isEmpty()) {
                historyEnded = true
                return@send
            }
            var total: Int
            synchronized(lock) {
                if (chatId != id) return@send
                val known = msgs.mapTo(HashSet()) { it.id }
                msgs.addAll(0, incoming.reversed().filter { it.id !in known })
                total = msgs.size
            }
            rebuild()
            markNewestRead()
            // Первая страница из локальной БД бывает короткой — добираем до полного экрана.
            if (total < 25) loadMore()
        }
    }

    /**
     * Отправка текста. silent — без звука у получателя;
     * scheduleAtUnix > 0 — отложенная отправка в указанное время.
     */
    fun sendText(
        text: String,
        silent: Boolean = false,
        scheduleAtUnix: Int = 0,
        sendWhenOnline: Boolean = false,
    ) {
        val id = synchronized(lock) { chatId }
        if (id == 0L || text.isBlank()) return
        val message = TdApi.SendMessage().apply {
            chatId = id
            inputMessageContent = TdApi.InputMessageText(
                TdApi.FormattedText(text.trim(), null),
                null,
                true,
            )
            if (silent || scheduleAtUnix > 0 || sendWhenOnline) {
                options = TdApi.MessageSendOptions().apply {
                    disableNotification = silent
                    schedulingState = when {
                        sendWhenOnline -> TdApi.MessageSchedulingStateSendWhenOnline()
                        scheduleAtUnix > 0 -> TdApi.MessageSchedulingStateSendAtDate(scheduleAtUnix, 0)
                        else -> null
                    }
                }
            }
        }
        TdClient.send(message)
        // Сообщение появится через updateNewMessage со статусом отправки.
    }

    // ---------- Перевод сообщений ----------

    data class Translation(val messageId: Long, val text: String?, val loading: Boolean)

    private val _translation = MutableStateFlow<Translation?>(null)
    val translation: StateFlow<Translation?> = _translation.asStateFlow()

    /** Переводит текст сообщения на язык интерфейса (TDLib TranslateMessageText). */
    fun translateMessage(messageId: Long) {
        val id = synchronized(lock) { chatId }
        if (id == 0L) return
        _translation.value = Translation(messageId, null, loading = true)
        val lang = java.util.Locale.getDefault().language.ifBlank { "ru" }
        TdClient.send(TdApi.TranslateMessageText(id, messageId, lang, "")) { res ->
            _translation.value = when (res) {
                is TdApi.FormattedText -> Translation(messageId, res.text, false)
                is TdApi.Error -> Translation(messageId, "Не удалось перевести: ${res.message}", false)
                else -> Translation(messageId, "Не удалось перевести", false)
            }
        }
    }

    fun clearTranslation() {
        _translation.value = null
    }

    fun sendPhoto(path: String, width: Int, height: Int, caption: String = "") {
        send(
            TdApi.InputMessagePhoto(
                TdApi.InputFileLocal(path),
                null,
                null,
                IntArray(0),
                width,
                height,
                if (caption.isBlank()) null else TdApi.FormattedText(caption, null),
                false,
                null,
                false,
            )
        )
    }

    fun sendVideo(path: String, width: Int, height: Int, duration: Int, caption: String = "") {
        send(
            TdApi.InputMessageVideo(
                TdApi.InputFileLocal(path),
                null,
                null,
                0,
                IntArray(0),
                duration,
                width,
                height,
                true,
                if (caption.isBlank()) null else TdApi.FormattedText(caption, null),
                false,
                null,
                false,
            )
        )
    }

    /** Альбом из нескольких фото/видео (до 10, как в TG). Подпись — на первом элементе. */
    fun sendAlbum(items: List<PendingMedia>, caption: String = "") {
        val id = synchronized(lock) { chatId }
        if (id == 0L || items.isEmpty()) return
        val replyTo = _reply.value?.let {
            TdApi.InputMessageReplyToMessage(it.messageId, null, 0, null)
        }
        val contents = items.mapIndexed { i, m ->
            val cap = if (i == 0 && caption.isNotBlank()) TdApi.FormattedText(caption, null) else null
            if (m.isVideo) {
                TdApi.InputMessageVideo(
                    TdApi.InputFileLocal(m.path), null, null, 0, IntArray(0),
                    m.duration, m.width, m.height, true, cap, false, null, false,
                )
            } else {
                TdApi.InputMessagePhoto(
                    TdApi.InputFileLocal(m.path), null, null, IntArray(0),
                    m.width, m.height, cap, false, null, false,
                )
            }
        }.toTypedArray<TdApi.InputMessageContent>()
        TdClient.send(TdApi.SendMessageAlbum(id, null, replyTo, null, contents))
        _reply.value = null
    }

    fun sendSticker(sticker: TdApi.Sticker) {
        val remoteId = sticker.sticker.remote?.id
        if (remoteId.isNullOrBlank()) return
        send(
            TdApi.InputMessageSticker(
                TdApi.InputFileRemote(remoteId),
                null,
                sticker.width,
                sticker.height,
                sticker.emoji,
            )
        )
    }

    fun sendGif(animation: TdApi.Animation) {
        val remoteId = animation.animation.remote?.id
        if (remoteId.isNullOrBlank()) return
        send(
            TdApi.InputMessageAnimation(
                TdApi.InputFileRemote(remoteId),
                null,
                IntArray(0),
                animation.duration,
                animation.width,
                animation.height,
                null,
                false,
                false,
            )
        )
    }

    fun sendVoice(path: String, durationSeconds: Int, waveform: ByteArray) {
        send(
            TdApi.InputMessageVoiceNote(
                TdApi.InputFileLocal(path),
                durationSeconds,
                waveform,
                null,
                null,
            )
        )
    }

    private fun send(content: TdApi.InputMessageContent) {
        val id = synchronized(lock) { chatId }
        if (id == 0L) return
        val replyTo = _reply.value?.let {
            TdApi.InputMessageReplyToMessage(it.messageId, null, 0, null)
        }
        val message = TdApi.SendMessage().apply {
            chatId = id
            this.replyTo = replyTo
            inputMessageContent = content
        }
        TdClient.send(message)
        _reply.value = null
    }

    // ---------- Реакции / редактирование / пересылка (для UI-биндинга) ----------

    /** Ставит/снимает «лайк» (👍) или заданное эмодзи на сообщение. */
    fun toggleReaction(messageId: Long, emoji: String = "👍") {
        val id = synchronized(lock) { chatId }
        if (id == 0L) return
        val msg = synchronized(lock) { msgs.firstOrNull { it.id == messageId } }
        val reactionType = TdApi.ReactionTypeEmoji(emoji)
        val already = msg?.interactionInfo?.reactions?.reactions?.any {
            it.isChosen && (it.type as? TdApi.ReactionTypeEmoji)?.emoji == emoji
        } == true
        if (already) {
            TdClient.send(TdApi.RemoveMessageReaction(id, messageId, reactionType))
        } else {
            TdClient.send(TdApi.AddMessageReaction(id, messageId, reactionType, false, true))
        }
    }

    /** Редактирует текст своего сообщения. */
    fun editMessageText(messageId: Long, newText: String) {
        val id = synchronized(lock) { chatId }
        if (id == 0L || newText.isBlank()) return
        TdClient.send(
            TdApi.EditMessageText(
                id, messageId, null,
                TdApi.InputMessageText(TdApi.FormattedText(newText.trim(), null), null, false),
            )
        )
    }

    /** Текущий открытый чат (для пересылки и пр.). */
    fun currentChatId(): Long = synchronized(lock) { chatId }

    /** Голос в опросе (optionIds — выбранные варианты, пустой массив отзывает голос). */
    fun votePoll(messageId: Long, optionIds: IntArray) {
        val id = synchronized(lock) { chatId }
        if (id == 0L) return
        TdClient.send(TdApi.SetPollAnswer(id, messageId, optionIds))
    }

    /** Пересылка сообщений между чатами (не зависит от открытого). */
    fun forwardMessages(fromChatId: Long, toChatId: Long, messageIds: LongArray) {
        if (fromChatId == 0L || toChatId == 0L || messageIds.isEmpty()) return
        TdClient.send(
            TdApi.ForwardMessages(toChatId, null, fromChatId, messageIds, null, false, false),
        )
    }

    // ---------- Режим редактирования ----------

    fun startEdit(messageId: Long) {
        val m = synchronized(lock) { msgs.firstOrNull { it.id == messageId } } ?: return
        if (!m.isOutgoing) return
        val text = (m.content as? TdApi.MessageText)?.text?.text
            ?: (m.content as? TdApi.MessagePhoto)?.caption?.text
            ?: (m.content as? TdApi.MessageVideo)?.caption?.text
            ?: (m.content as? TdApi.MessageAnimation)?.caption?.text
            ?: (m.content as? TdApi.MessageDocument)?.caption?.text
            ?: return
        _reply.value = null
        _editing.value = EditDraft(messageId, text)
    }

    fun clearEdit() {
        _editing.value = null
    }

    fun submitEdit(newText: String) {
        val draft = _editing.value ?: return
        val id = synchronized(lock) { chatId }
        val m = synchronized(lock) { msgs.firstOrNull { it.id == draft.messageId } }
        val isMedia = m?.content is TdApi.MessagePhoto || m?.content is TdApi.MessageVideo ||
            m?.content is TdApi.MessageAnimation || m?.content is TdApi.MessageDocument
        if (isMedia && id != 0L) {
            // Медиа редактируется через подпись (EditMessageText на фото/видео падает).
            TdClient.send(
                TdApi.EditMessageCaption(
                    id, draft.messageId, null,
                    TdApi.FormattedText(newText.trim(), null), false,
                ),
            )
        } else {
            editMessageText(draft.messageId, newText)
        }
        _editing.value = null
    }

    // ---------- v0.27 фичи: дата, автоудаление, закреп чата, ссылка, избранные стикеры/GIF ----------

    /** Переход к сообщению по дате (календарь). */
    fun jumpToDate(dateUnix: Int) {
        val id = synchronized(lock) { chatId }
        if (id == 0L) return
        TdClient.send(TdApi.GetChatMessageByDate(id, dateUnix)) { res ->
            (res as? TdApi.Message)?.let { jumpTo(it.id) }
        }
    }

    /** Таймер автоудаления чата (0 = выкл). */
    fun setAutoDelete(chatId: Long, seconds: Int) {
        if (chatId == 0L) return
        TdClient.send(TdApi.SetChatMessageAutoDeleteTime(chatId, seconds))
    }

    /** Текущий таймер автоудаления (сек). */
    fun autoDeleteTime(chatId: Long): Int = ChatStore.chat(chatId)?.messageAutoDeleteTime ?: 0

    fun togglePinChat(chatId: Long, pin: Boolean) {
        if (chatId == 0L) return
        TdClient.send(TdApi.ToggleChatIsPinned(TdApi.ChatListMain(), chatId, pin))
    }

    fun isPinnedChat(chatId: Long): Boolean {
        val chat = ChatStore.chat(chatId) ?: return false
        return chat.positions?.any { it.list is TdApi.ChatListMain && it.isPinned } == true
    }

    /** Получает ссылку на сообщение (для супергрупп/каналов) и отдаёт в колбэк. */
    fun messageLink(messageId: Long, onResult: (String?) -> Unit) {
        val id = synchronized(lock) { chatId }
        if (id == 0L) { onResult(null); return }
        TdClient.send(TdApi.GetMessageLink(id, messageId, 0, 0, "", false, false)) { res ->
            onResult((res as? TdApi.MessageLink)?.link)
        }
    }

    fun favoriteSticker(sticker: TdApi.Sticker) {
        val remoteId = sticker.sticker.remote?.id ?: return
        TdClient.send(TdApi.AddFavoriteSticker(TdApi.InputFileRemote(remoteId)))
    }

    fun saveAnimation(animation: TdApi.Animation) {
        val remoteId = animation.animation.remote?.id ?: return
        TdClient.send(TdApi.AddSavedAnimation(TdApi.InputFileRemote(remoteId)))
    }

    // ---------- Ответы и удаление ----------

    fun startReply(messageId: Long) {
        val m = synchronized(lock) { msgs.firstOrNull { it.id == messageId } } ?: return
        val senderId = (m.senderId as? TdApi.MessageSenderUser)?.userId ?: 0L
        val sender = when {
            m.isOutgoing -> "Вы"
            senderId != 0L -> UserCache.firstName(senderId)
            else -> null
        }
        _reply.value = ReplyDraft(messageId, MessageFormat.contentText(m.content), sender)
    }

    fun clearReply() {
        _reply.value = null
    }

    fun deleteMessage(messageId: Long, forEveryone: Boolean) =
        deleteMessages(longArrayOf(messageId), forEveryone)

    /** Удаление нескольких сообщений (мультивыбор). */
    fun deleteMessages(messageIds: LongArray, forEveryone: Boolean) {
        val id = synchronized(lock) { chatId }
        if (id == 0L || messageIds.isEmpty()) return
        TdClient.send(TdApi.DeleteMessages(id, messageIds, forEveryone))
    }

    fun handleUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateNewMessage -> ifCurrent(obj.message.chatId) {
                // Пока показано «окно» старой истории, живые сообщения не вклеиваем:
                // их добирает loadNewer()/returnToLatest(), иначе список станет дырявым.
                if (_detached.value) return
                synchronized(lock) {
                    if (msgs.none { it.id == obj.message.id }) msgs.add(obj.message)
                }
                rebuild()
                markNewestRead()
            }

            is TdApi.UpdateMessageSendSucceeded -> ifCurrent(obj.message.chatId) {
                synchronized(lock) {
                    val i = msgs.indexOfFirst { it.id == obj.oldMessageId }
                    if (i >= 0) msgs[i] = obj.message else msgs.add(obj.message)
                    msgs.sortBy { it.id }
                }
                rebuild()
            }

            is TdApi.UpdateMessageContent -> ifCurrent(obj.chatId) {
                synchronized(lock) {
                    msgs.firstOrNull { it.id == obj.messageId }?.content = obj.newContent
                }
                rebuild()
            }

            is TdApi.UpdateDeleteMessages -> {
                if (!obj.isPermanent) return
                ifCurrent(obj.chatId) {
                    val ids = obj.messageIds.toHashSet()
                    synchronized(lock) { msgs.removeAll { it.id in ids } }
                    rebuild()
                }
            }

            is TdApi.UpdateChatReadOutbox -> ifCurrent(obj.chatId) {
                lastReadOutbox = obj.lastReadOutboxMessageId
                rebuild()
            }

            is TdApi.UpdateMessageInteractionInfo -> ifCurrent(obj.chatId) {
                synchronized(lock) {
                    msgs.firstOrNull { it.id == obj.messageId }?.interactionInfo = obj.interactionInfo
                }
                rebuild()
            }

            is TdApi.UpdateMessageEdited -> ifCurrent(obj.chatId) {
                synchronized(lock) {
                    msgs.firstOrNull { it.id == obj.messageId }?.editDate = obj.editDate
                }
                rebuild()
            }

            is TdApi.UpdateMessageIsPinned -> ifCurrent(obj.chatId) {
                synchronized(lock) {
                    msgs.firstOrNull { it.id == obj.messageId }?.isPinned = obj.isPinned
                }
                refreshPinned(obj.chatId)
                rebuild()
            }
        }
    }

    private inline fun ifCurrent(id: Long, block: () -> Unit) {
        if (synchronized(lock) { chatId } == id) block()
    }

    private fun markNewestRead() {
        val (id, newest) = synchronized(lock) { chatId to msgs.lastOrNull()?.id }
        if (id != 0L && newest != null) {
            TdClient.send(TdApi.ViewMessages(id, longArrayOf(newest), null, true))
        }
    }

    private fun rebuild() {
        rebuildRequests.trySend(Unit)
    }

    private fun doRebuild() {
        val snapshot = synchronized(lock) { msgs.toList() }
        // Merge consecutive album members (same non-zero mediaAlbumId) into one unit.
        val units = ArrayList<List<TdApi.Message>>()
        var u = 0
        while (u < snapshot.size) {
            val m = snapshot[u]
            if (m.mediaAlbumId != 0L) {
                val group = ArrayList<TdApi.Message>().apply { add(m) }
                var j = u + 1
                while (j < snapshot.size && snapshot[j].mediaAlbumId == m.mediaAlbumId) {
                    group.add(snapshot[j]); j++
                }
                units.add(group); u = j
            } else {
                units.add(listOf(m)); u++
            }
        }

        _messages.value = units.mapIndexed { i, group ->
            val m = group.first()
            val album = group.size > 1
            // For albums the caption lives on whichever member has one.
            val captioned = group.firstOrNull { captionOf(it.content)?.isNotBlank() == true } ?: m
            val senderId = (m.senderId as? TdApi.MessageSenderUser)?.userId ?: 0L
            val prev = units.getOrNull(i - 1)?.first()
            val prevSenderId = (prev?.senderId as? TdApi.MessageSenderUser)?.userId ?: -1L
            val firstOfGroup = prev == null || prevSenderId != senderId || prev.isOutgoing != m.isOutgoing
            val next = units.getOrNull(i + 1)?.first()
            val nextSenderId = (next?.senderId as? TdApi.MessageSenderUser)?.userId ?: -1L
            val lastOfGroup = next == null || nextSenderId != senderId || next.isOutgoing != m.isOutgoing
            val replyTo = m.replyTo as? TdApi.MessageReplyToMessage
            UiMessage(
                id = m.id,
                text = if (album) captionOf(captioned.content).orEmpty()
                else MessageFormat.contentText(m.content),
                content = if (album) captioned.content else m.content,
                time = MessageFormat.bubbleTime(m.date),
                isMine = m.isOutgoing,
                senderName = if (isGroup && !m.isOutgoing && senderId != 0L) {
                    UserCache.firstName(senderId)
                } else null,
                senderSeed = senderId,
                showSender = isGroup && !m.isOutgoing && firstOfGroup,
                isFirstOfGroup = firstOfGroup,
                isLastOfGroup = lastOfGroup,
                dateLabel = MessageFormat.dayLabel(m.date),
                replyText = replyTo?.content?.let { MessageFormat.contentText(it) }
                    ?: if (replyTo != null) "Сообщение" else null,
                canDelete = chatCanDeleteForSelf || chatCanDeleteForAll || m.isOutgoing,
                canDeleteForAll = chatCanDeleteForAll || m.isOutgoing,
                outStatus = when {
                    !m.isOutgoing -> OutStatus.NONE
                    m.sendingState is TdApi.MessageSendingStateFailed -> OutStatus.FAILED
                    m.sendingState is TdApi.MessageSendingStatePending -> OutStatus.SENDING
                    m.id <= lastReadOutbox -> OutStatus.READ
                    else -> OutStatus.SENT
                },
                isEdited = m.editDate > 0,
                isPinned = m.isPinned,
                reactions = m.interactionInfo?.reactions?.reactions?.mapNotNull { r ->
                    val e = (r.type as? TdApi.ReactionTypeEmoji)?.emoji ?: return@mapNotNull null
                    UiReaction(e, r.totalCount, r.isChosen)
                } ?: emptyList(),
                forwardFrom = forwardLabel(m.forwardInfo),
                albumMedia = if (album) {
                    group.map { AlbumItem(it.id, it.content) }
                } else emptyList(),
            )
        }
    }

    /** Текст подписи у медийного контента (или null). */
    private fun captionOf(content: TdApi.MessageContent?): String? = when (content) {
        is TdApi.MessagePhoto -> content.caption?.text
        is TdApi.MessageVideo -> content.caption?.text
        is TdApi.MessageAnimation -> content.caption?.text
        is TdApi.MessageDocument -> content.caption?.text
        else -> null
    }

    /** Источник пересланного сообщения: «Канал», «Имя» или скрытый отправитель. */
    private fun forwardLabel(info: TdApi.MessageForwardInfo?): String? = when (val o = info?.origin) {
        is TdApi.MessageOriginChannel -> ChatStore.chat(o.chatId)?.title
        is TdApi.MessageOriginChat -> ChatStore.chat(o.senderChatId)?.title
        is TdApi.MessageOriginUser -> UserCache.firstName(o.senderUserId)
        is TdApi.MessageOriginHiddenUser -> o.senderName
        else -> null
    }
}
