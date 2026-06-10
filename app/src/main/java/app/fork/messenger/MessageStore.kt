package app.fork.messenger

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val replyText: String?,
    val canDelete: Boolean,
    val canDeleteForAll: Boolean,
)

/** Черновик ответа: на какое сообщение отвечаем и как его показать в превью. */
data class ReplyDraft(val messageId: Long, val preview: String, val sender: String?)

/**
 * Состояние открытого чата: история (по возрастанию id), отправка, realtime-апдейты.
 */
object MessageStore {
    private val lock = Any()
    private var chatId: Long = 0
    private var isGroup = false
    private var chatCanDeleteForSelf = false
    private var chatCanDeleteForAll = false
    private val msgs = ArrayList<TdApi.Message>() // отсортированы по возрастанию id

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _loadingHistory = MutableStateFlow(false)
    val loadingHistory: StateFlow<Boolean> = _loadingHistory.asStateFlow()

    private val _reply = MutableStateFlow<ReplyDraft?>(null)
    val reply: StateFlow<ReplyDraft?> = _reply.asStateFlow()

    private var historyEnded = false

    fun open(id: Long) {
        synchronized(lock) {
            chatId = id
            msgs.clear()
            historyEnded = false
        }
        _messages.value = emptyList()

        val chat = ChatStore.chat(id)
        _title.value = chat?.title ?: ""
        val type = chat?.type
        isGroup = type is TdApi.ChatTypeBasicGroup ||
            (type is TdApi.ChatTypeSupergroup && !type.isChannel)
        chatCanDeleteForSelf = chat?.canBeDeletedOnlyForSelf == true
        chatCanDeleteForAll = chat?.canBeDeletedForAllUsers == true

        app.fork.messenger.notify.NotificationsCenter.clearForChat(id)
        TdClient.send(TdApi.OpenChat(id))
        loadMore()
    }

    fun isViewing(id: Long): Boolean = synchronized(lock) { chatId == id }

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

    fun sendText(text: String) {
        val id = synchronized(lock) { chatId }
        if (id == 0L || text.isBlank()) return
        val message = TdApi.SendMessage().apply {
            chatId = id
            inputMessageContent = TdApi.InputMessageText(
                TdApi.FormattedText(text.trim(), null),
                null,
                true,
            )
        }
        TdClient.send(message)
        // Сообщение появится через updateNewMessage со статусом отправки.
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

    fun deleteMessage(messageId: Long, forEveryone: Boolean) {
        val id = synchronized(lock) { chatId }
        if (id == 0L) return
        TdClient.send(TdApi.DeleteMessages(id, longArrayOf(messageId), forEveryone))
    }

    fun handleUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateNewMessage -> ifCurrent(obj.message.chatId) {
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
        val snapshot = synchronized(lock) { msgs.toList() }
        _messages.value = snapshot.mapIndexed { i, m ->
            val senderId = (m.senderId as? TdApi.MessageSenderUser)?.userId ?: 0L
            val prev = snapshot.getOrNull(i - 1)
            val prevSenderId = (prev?.senderId as? TdApi.MessageSenderUser)?.userId ?: -1L
            val firstOfGroup = prev == null || prevSenderId != senderId || prev.isOutgoing != m.isOutgoing
            val replyTo = m.replyTo as? TdApi.MessageReplyToMessage
            UiMessage(
                id = m.id,
                text = MessageFormat.contentText(m.content),
                content = m.content,
                time = MessageFormat.bubbleTime(m.date),
                isMine = m.isOutgoing,
                senderName = if (isGroup && !m.isOutgoing && senderId != 0L) {
                    UserCache.firstName(senderId)
                } else null,
                senderSeed = senderId,
                showSender = isGroup && !m.isOutgoing && firstOfGroup,
                isFirstOfGroup = firstOfGroup,
                replyText = replyTo?.content?.let { MessageFormat.contentText(it) }
                    ?: if (replyTo != null) "Сообщение" else null,
                canDelete = chatCanDeleteForSelf || chatCanDeleteForAll || m.isOutgoing,
                canDeleteForAll = chatCanDeleteForAll || m.isOutgoing,
            )
        }
    }
}
