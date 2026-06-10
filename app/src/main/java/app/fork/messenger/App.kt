package app.fork.messenger

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        TdClient.start(this)
    }
}
