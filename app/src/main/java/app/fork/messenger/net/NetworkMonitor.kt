package app.fork.messenger.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import app.fork.messenger.TdClient
import org.drinkless.tdlib.TdApi

/**
 * Следит за сетью устройства и сообщает TDLib её тип. По документации TDLib
 * вызов setNetworkType принудительно переоткрывает все соединения — это
 * убирает 30-секундный таймаут и экспоненциальный бэкоф переподключения
 * при смене сети (Wi-Fi ⇄ мобильная) и после простоя. Официальный Telegram
 * делает то же самое — поэтому он подключается мгновенно.
 */
object NetworkMonitor {
    private const val TAG = "NetworkMonitor"

    @Volatile
    private var connectivity: ConnectivityManager? = null

    fun init(context: Context) {
        if (connectivity != null) return
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivity = cm
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = push("available")
                override fun onLost(network: Network) {
                    Log.i(TAG, "network lost")
                    TdClient.send(TdApi.SetNetworkType(TdApi.NetworkTypeNone()))
                }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = push("changed")
            })
        }.onFailure { Log.w(TAG, "network callback unavailable: ${it.message}") }
        push("init")
    }

    /** Текущий тип сети для TDLib. */
    fun current(): TdApi.NetworkType {
        val cm = connectivity ?: return TdApi.NetworkTypeOther()
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            ?: return TdApi.NetworkTypeNone()
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TdApi.NetworkTypeWiFi()
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TdApi.NetworkTypeMobile()
            else -> TdApi.NetworkTypeOther()
        }
    }

    /** Сообщает TDLib актуальный тип сети (форсирует переоткрытие соединений). */
    fun push(reason: String) {
        val type = current()
        Log.i(TAG, "network -> ${type.javaClass.simpleName} ($reason)")
        TdClient.send(TdApi.SetNetworkType(type))
    }
}
