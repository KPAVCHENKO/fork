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
    @Volatile private var lastVpn = false

    fun init(context: Context) {
        if (connectivity != null) return
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivity = cm
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = onChange("available")
                override fun onLost(network: Network) {
                    Log.i(TAG, "network lost")
                    TdClient.send(TdApi.SetNetworkType(TdApi.NetworkTypeNone()))
                    TdClient.onNetworkChanged()
                }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = onChange("changed")
            })
        }.onFailure { Log.w(TAG, "network callback unavailable: ${it.message}") }
        lastVpn = isVpnActive()
        push("init")
    }

    /** Network changed: tell TDLib the new type, and let the proxy manager react
     *  (especially when a VPN was toggled — the set of working proxies changes). */
    private fun onChange(reason: String) {
        val vpn = isVpnActive()
        val vpnToggled = vpn != lastVpn
        lastVpn = vpn
        push(if (vpnToggled) "$reason/vpn=$vpn" else reason)
        if (vpnToggled) {
            // VPN on -> go direct (no proxy); VPN off -> re-enable proxy.
            TdClient.onVpnChanged(vpn)
        } else {
            TdClient.onNetworkChanged()
        }
    }

    /** True if the active network runs over a VPN. */
    fun isVpnActive(): Boolean {
        val cm = connectivity ?: return false
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
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
