package app.fork.messenger

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/** Контакт для экрана «Новый чат / Контакты». */
data class UiContact(
    val userId: Long,
    val name: String,
    val username: String?,
    val initials: String,
    val isOnline: Boolean,
)

/**
 * Список контактов из Telegram (getContacts) + открытие личного чата.
 * Самостоятельный стор: данные тянет из UserCache, недостающих дозапрашивает.
 */
object ContactsStore {
    private val _contacts = MutableStateFlow<List<UiContact>>(emptyList())
    val contacts: StateFlow<List<UiContact>> = _contacts.asStateFlow()

    private var userIds: LongArray = LongArray(0)

    fun load() {
        TdClient.send(TdApi.GetContacts()) { result ->
            if (result !is TdApi.Users) return@send
            userIds = result.userIds
            rebuild()
            // Дозапрашиваем неизвестных пользователей — список достроится по мере ответов.
            result.userIds.forEach { id ->
                if (UserCache.user(id) == null) {
                    TdClient.send(TdApi.GetUser(id)) { rebuild() }
                }
            }
        }
    }

    fun openChat(userId: Long, onOpen: (Long) -> Unit) {
        TdClient.send(TdApi.CreatePrivateChat(userId, false)) { result ->
            if (result is TdApi.Chat) onOpen(result.id)
        }
    }

    /** Открывает «Избранное» (чат с самим собой). */
    fun openSavedMessages(onOpen: (Long) -> Unit) {
        TdClient.send(TdApi.GetMe()) { me ->
            if (me is TdApi.User) openChat(me.id, onOpen)
        }
    }

    private fun rebuild() {
        _contacts.value = userIds.toList().mapNotNull { id ->
            val user = UserCache.user(id) ?: return@mapNotNull null
            val name = listOf(user.firstName, user.lastName)
                .filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Без имени" }
            UiContact(
                userId = id,
                name = name,
                username = user.usernames?.activeUsernames?.firstOrNull(),
                initials = MessageFormat.initials(name),
                isOnline = UserCache.isOnline(id),
            )
        }.sortedBy { it.name.lowercase() }
    }
}
