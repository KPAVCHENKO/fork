package app.fork.messenger

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/**
 * Поиск: по уже известным чатам (мгновенно, оффлайн) и публичным чатам/каналам
 * на сервере, плюс открытие по ссылке-приглашению или @юзернейму.
 */
object SearchStore {
    private var localIds: List<Long> = emptyList()
    private var globalIds: List<Long> = emptyList()
    private var generation = 0

    private val _results = MutableStateFlow<List<Long>>(emptyList())
    val results: StateFlow<List<Long>> = _results.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun search(query: String) {
        val q = query.trim()
        val gen = ++generation
        if (q.isBlank()) {
            localIds = emptyList(); globalIds = emptyList(); _results.value = emptyList(); _status.value = null
            return
        }

        TdClient.send(TdApi.SearchChats(q, 50)) { result ->
            if (gen == generation && result is TdApi.Chats) {
                localIds = result.chatIds.toList()
                publish()
            }
        }
        // Глобальный поиск имеет смысл от 3+ символов (ограничение Telegram).
        if (q.length >= 3) {
            TdClient.send(TdApi.SearchPublicChats(q)) { result ->
                if (gen == generation && result is TdApi.Chats) {
                    globalIds = result.chatIds.toList()
                    publish()
                }
            }
        } else {
            globalIds = emptyList()
        }
    }

    private fun publish() {
        // Локальные сверху, затем глобальные без дублей.
        val seen = HashSet<Long>()
        val merged = (localIds + globalIds).filter { seen.add(it) }
        _results.value = merged
    }

    fun clear() {
        generation++
        localIds = emptyList(); globalIds = emptyList(); _results.value = emptyList(); _status.value = null
    }

    /** Открыть чат по @юзернейму или ссылке-приглашению (t.me/...). */
    fun resolveAndOpen(query: String, onOpen: (Long) -> Unit) {
        val q = query.trim()
        _status.value = "Поиск…"
        when {
            q.contains("/+") || q.contains("joinchat", ignoreCase = true) || q.startsWith("+") -> {
                val link = if (q.startsWith("http")) q else "https://t.me/${q.trimStart('/')}"
                TdClient.send(TdApi.JoinChatByInviteLink(link)) { result ->
                    if (result is TdApi.Chat) { _status.value = null; onOpen(result.id) }
                    else _status.value = "Не удалось вступить по ссылке"
                }
            }
            else -> {
                val username = q.substringAfterLast('/').removePrefix("@").trim()
                if (username.isEmpty()) { _status.value = "Введите @имя или ссылку"; return }
                TdClient.send(TdApi.SearchPublicChat(username)) { result ->
                    if (result is TdApi.Chat) { _status.value = null; onOpen(result.id) }
                    else _status.value = "Чат @$username не найден"
                }
            }
        }
    }
}
