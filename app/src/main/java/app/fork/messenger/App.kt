package app.fork.messenger

import android.app.Application
import app.fork.messenger.notify.NotificationsCenter
import app.fork.messenger.update.UpdateManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        NotificationsCenter.init(this)
        TdClient.start(this)
        // Тихо проверяем обновления и актуальный адрес прокси на GitHub (не через прокси).
        UpdateManager.checkSilently(this)
        app.fork.messenger.net.ProxyConfig.fetchAndApply(this)
    }
}
