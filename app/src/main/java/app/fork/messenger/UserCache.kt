package app.fork.messenger

import java.util.concurrent.ConcurrentHashMap
import org.drinkless.tdlib.TdApi

/** Кэш пользователей: TDLib присылает их через updateUser, недостающих дозапрашиваем. */
object UserCache {
    private val users = ConcurrentHashMap<Long, TdApi.User>()
    private val statuses = ConcurrentHashMap<Long, TdApi.UserStatus>()
    private val requested = ConcurrentHashMap.newKeySet<Long>()

    /** Колбэк для живого обновления статуса в открытом чате. */
    @Volatile
    var onStatusChanged: ((Long) -> Unit)? = null

    fun handleUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateUser -> users[obj.user.id] = obj.user
            is TdApi.UpdateUserStatus -> {
                statuses[obj.userId] = obj.status
                onStatusChanged?.invoke(obj.userId)
            }
        }
    }

    fun user(userId: Long): TdApi.User? = users[userId]

    /** Статус «онлайн / был(а) …» как в Telegram. */
    fun statusText(userId: Long): String {
        val status = statuses[userId] ?: users[userId]?.status
        return when (status) {
            is TdApi.UserStatusOnline -> "онлайн"
            is TdApi.UserStatusOffline -> {
                val mins = (System.currentTimeMillis() / 1000 - status.wasOnline) / 60
                when {
                    mins < 1 -> "был(а) только что"
                    mins < 60 -> "был(а) $mins мин назад"
                    mins < 60 * 24 -> "был(а) ${mins / 60} ч назад"
                    else -> "был(а) давно"
                }
            }
            is TdApi.UserStatusRecently -> "был(а) недавно"
            else -> "был(а) давно"
        }
    }

    /** Имя пользователя; если ещё не знаем — запрашиваем, имя появится со следующим обновлением списка. */
    fun firstName(userId: Long): String? {
        val user = users[userId]
        if (user == null && requested.add(userId)) {
            TdClient.send(TdApi.GetUser(userId)) { result ->
                if (result is TdApi.User) {
                    users[userId] = result
                    ChatStore.invalidate()
                }
            }
        }
        return user?.firstName?.takeIf { it.isNotBlank() }
    }
}
