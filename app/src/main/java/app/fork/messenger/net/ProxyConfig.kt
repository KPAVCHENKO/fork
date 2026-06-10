package app.fork.messenger.net

import android.content.Context
import android.util.Log
import app.fork.messenger.BuildConfig
import app.fork.messenger.TdClient
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Динамическая конфигурация прокси: при старте приложение проверяет proxy.json
 * в GitHub-репозитории (напрямую, мимо прокси). Если прокси переехал — все
 * установленные приложения подхватят новый адрес без переустановки.
 */
object ProxyConfig {
    data class Endpoint(val host: String, val port: Int, val secret: String)

    private const val PREFS = "proxy_config"

    /** Актуальные параметры: сохранённые с GitHub или зашитые при сборке. */
    fun current(context: Context): Endpoint {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val host = prefs.getString("host", null)
        val port = prefs.getInt("port", 0)
        val secret = prefs.getString("secret", null)
        return if (!host.isNullOrBlank() && port != 0 && !secret.isNullOrBlank()) {
            Endpoint(host, port, secret)
        } else {
            Endpoint(BuildConfig.PROXY_HOST, BuildConfig.PROXY_PORT, BuildConfig.PROXY_SECRET)
        }
    }

    /** Тихо проверяет proxy.json в репозитории и применяет, если изменился. */
    fun fetchAndApply(context: Context) {
        val repo = BuildConfig.UPDATE_REPO
        if (repo.isBlank() || repo.startsWith("PLACEHOLDER")) return
        Thread {
            runCatching {
                val url = "https://raw.githubusercontent.com/$repo/main/proxy.json"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("User-Agent", "Fork-ProxyConfig")
                }
                val json = conn.inputStream.use { JSONObject(it.bufferedReader().readText()) }
                val fresh = Endpoint(
                    host = json.getString("host"),
                    port = json.getInt("port"),
                    secret = json.getString("secret"),
                )
                if (fresh.host.isBlank() || fresh.port == 0 || fresh.secret.isBlank()) return@runCatching

                val old = current(context)
                if (fresh != old) {
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString("host", fresh.host)
                        .putInt("port", fresh.port)
                        .putString("secret", fresh.secret)
                        .apply()
                    Log.i("ProxyConfig", "proxy endpoint updated from GitHub")
                    TdClient.reapplyProxy()
                }
            }.onFailure { Log.w("ProxyConfig", "fetch failed: ${it.message}") }
        }.start()
    }
}
