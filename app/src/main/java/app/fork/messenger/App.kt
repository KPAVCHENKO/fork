package app.fork.messenger

import android.app.Application
import app.fork.messenger.update.UpdateManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        TdClient.start(this)
        // Тихо проверяем обновления на GitHub при старте (не через прокси).
        UpdateManager.checkSilently(this)
    }
}
