package app.fork.messenger

import java.util.concurrent.ConcurrentHashMap
import org.drinkless.tdlib.TdApi

/** Кэш пользователей: TDLib присылает их через updateUser, недостающих дозапрашиваем. */
object UserCache {
    private val users = ConcurrentHashMap<Long, TdApi.User>()
    private val requested = ConcurrentHashMap.newKeySet<Long>()

    fun handleUpdate(obj: TdApi.Object) {
        if (obj is TdApi.UpdateUser) {
            users[obj.user.id] = obj.user
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
