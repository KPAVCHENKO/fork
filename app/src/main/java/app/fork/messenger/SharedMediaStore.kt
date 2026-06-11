package app.fork.messenger

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/**
 * Общие вложения чата для профиля: медиа / файлы / ссылки / голосовые.
 * Постраничная подгрузка через SearchChatMessages с серверными фильтрами.
 */
object SharedMediaStore {

    enum class Tab(val title: String) {
        MEDIA("Медиа"),
        FILES("Файлы"),
        LINKS("Ссылки"),
        VOICE("Голосовые"),
    }

    data class TabState(
        val messages: List<TdApi.Message> = emptyList(),
        val loading: Boolean = false,
        val ended: Boolean = false,
        val nextFrom: Long = 0L,
    )

    @Volatile
    private var chatId = 0L

    private val _tabs = MutableStateFlow<Map<Tab, TabState>>(emptyMap())
    val tabs: StateFlow<Map<Tab, TabState>> = _tabs.asStateFlow()

    /** Привязывает стор к чату; при смене чата всё сбрасывается. */
    fun open(id: Long) {
        if (chatId != id) {
            chatId = id
            _tabs.value = emptyMap()
        }
    }

    /** Подгружает следующую страницу вкладки (первый вызов — первую). */
    fun load(tab: Tab) {
        val id = chatId
        if (id == 0L) return
        val current = _tabs.value[tab] ?: TabState()
        if (current.loading || current.ended) return
        update(tab) { it.copy(loading = true) }

        val filter: TdApi.SearchMessagesFilter = when (tab) {
            Tab.MEDIA -> TdApi.SearchMessagesFilterPhotoAndVideo()
            Tab.FILES -> TdApi.SearchMessagesFilterDocument()
            Tab.LINKS -> TdApi.SearchMessagesFilterUrl()
            Tab.VOICE -> TdApi.SearchMessagesFilterVoiceNote()
        }
        TdClient.send(
            TdApi.SearchChatMessages(id, null, "", null, current.nextFrom, 0, 48, filter),
        ) { result ->
            if (chatId != id) return@send
            val found = result as? TdApi.FoundChatMessages
            val incoming = found?.messages.orEmpty().filterNotNull()
            update(tab) { state ->
                val known = state.messages.mapTo(HashSet()) { it.id }
                state.copy(
                    messages = state.messages + incoming.filter { it.id !in known },
                    loading = false,
                    ended = found == null || incoming.isEmpty() || found.nextFromMessageId == 0L,
                    nextFrom = found?.nextFromMessageId ?: state.nextFrom,
                )
            }
        }
    }

    private inline fun update(tab: Tab, block: (TabState) -> TabState) {
        val map = _tabs.value.toMutableMap()
        map[tab] = block(map[tab] ?: TabState())
        _tabs.value = map
    }
}
