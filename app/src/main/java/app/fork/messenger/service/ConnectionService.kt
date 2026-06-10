package app.fork.messenger.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.fork.messenger.R
import app.fork.messenger.notify.NotificationsCenter

/**
 * Лёгкий foreground-сервис: держит процесс (а с ним и соединение TDLib через прокси)
 * живым, чтобы новые сообщения приходили мгновенно, как у Telegram. Показывает
 * ненавязчивое постоянное уведомление низкого приоритета.
 */
class ConnectionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, NotificationsCenter.CHANNEL_SERVICE)
            .setContentTitle("Fork на связи")
            .setContentText("Поддерживает соединение для мгновенных сообщений")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SERVICE_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    companion object {
        private const val SERVICE_NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, ConnectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
