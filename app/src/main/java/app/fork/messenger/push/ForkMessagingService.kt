package app.fork.messenger.push

import android.util.Log
import app.fork.messenger.TdClient
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject

/**
 * Пуши через Firebase: сервер Telegram сам присылает их (после registerDevice).
 * Пуш будит приложение, TDLib подключается через прокси и забирает сообщения —
 * локальные уведомления показывает NotificationsCenter.
 */
class ForkMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.i(TAG, "new FCM token")
        TdClient.start(applicationContext)
        TdClient.registerPushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.i(TAG, "push received")
        // Будим TDLib (если процесс только что поднят — start создаст клиента).
        TdClient.start(applicationContext)
        // Передаём полезную нагрузку TDLib — он сам поймёт, что в ней.
        if (message.data.isNotEmpty()) {
            val payload = JSONObject(message.data as Map<*, *>).toString()
            TdClient.processPushPayload(payload)
        }
    }

    private companion object {
        const val TAG = "ForkPush"
    }
}
