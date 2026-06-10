package app.fork.messenger.update

import android.content.Context
import app.fork.messenger.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Информация о доступном релизе с GitHub. */
data class ReleaseInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

/** Состояние процесса обновления для UI. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data object NeedPermission : UpdateState
    data object Installing : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Самообновление из GitHub Releases. Ходит на GitHub НАПРЯМУЮ (не через прокси —
 * прокси только для Telegram). Репозиторий публичный, поэтому токен не нужен.
 */
object UpdateManager {
    private const val API = "https://api.github.com/repos/%s/releases/latest"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    @Volatile
    private var working = false

    /** Тихая проверка на старте: молчит, если обновления нет или нет сети. */
    fun checkSilently(context: Context) = check(context, silent = true)

    /** Проверка по кнопке: показывает «актуальная версия» и ошибки. */
    fun checkExplicit(context: Context) = check(context, silent = false)

    private fun check(context: Context, silent: Boolean) {
        if (working) return
        val repo = BuildConfig.UPDATE_REPO
        if (repo.isBlank() || repo.startsWith("PLACEHOLDER")) return
        working = true
        if (!silent) _state.value = UpdateState.Checking

        Thread {
            try {
                val release = fetchLatest(repo)
                _state.value = when {
                    release == null -> if (silent) UpdateState.Idle else UpdateState.UpToDate
                    isNewer(release.version, BuildConfig.VERSION_NAME) -> UpdateState.Available(release)
                    else -> if (silent) UpdateState.Idle else UpdateState.UpToDate
                }
            } catch (e: Exception) {
                _state.value = if (silent) UpdateState.Idle else UpdateState.Failed(reason(e))
            } finally {
                working = false
            }
        }.start()
    }

    /** Скачивает APK последнего найденного релиза и запускает установку. */
    fun downloadAndInstall(context: Context) {
        val release = (_state.value as? UpdateState.Available)?.release ?: return
        if (working) return
        working = true
        _state.value = UpdateState.Downloading(0)

        Thread {
            try {
                val apk = download(context, release)
                _state.value = UpdateState.Installing
                ApkInstaller.install(context.applicationContext, apk)
                // Дальше состояние ведёт InstallResultReceiver.
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(reason(e))
            } finally {
                working = false
            }
        }.start()
    }

    fun reset() {
        _state.value = UpdateState.Idle
    }

    internal fun onState(state: UpdateState) {
        _state.value = state
    }

    private fun fetchLatest(repo: String): ReleaseInfo? {
        val conn = (URL(API.format(repo)).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Fork-Updater")
            connectTimeout = 15000
            readTimeout = 15000
        }
        conn.inputStream.use { stream ->
            val json = JSONObject(stream.bufferedReader().readText())
            val tag = json.optString("tag_name").ifBlank { json.optString("name") }
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    return ReleaseInfo(
                        version = tag,
                        notes = json.optString("body").trim(),
                        apkUrl = asset.getString("browser_download_url"),
                        sizeBytes = asset.optLong("size"),
                    )
                }
            }
        }
        return null
    }

    private fun download(context: Context, release: ReleaseInfo): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Чистим старые скачанные APK.
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "fork-${sanitize(release.version)}.apk")

        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Fork-Updater")
            connectTimeout = 15000
            readTimeout = 30000
        }
        val total = if (release.sizeBytes > 0) release.sizeBytes else conn.contentLengthLong
        conn.inputStream.use { input ->
            out.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var downloaded = 0L
                var lastPercent = -1
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0) {
                        val percent = ((downloaded * 100) / total).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _state.value = UpdateState.Downloading(percent)
                        }
                    }
                }
            }
        }
        return out
    }

    private fun sanitize(version: String): String = version.filter { it.isLetterOrDigit() || it == '.' }

    /** Сравнение семантических версий: "v0.3.0" новее "0.2.0". */
    private fun isNewer(remote: String, current: String): Boolean {
        val r = parse(remote)
        val c = parse(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parse(version: String): List<Int> =
        version.trimStart('v', 'V').split('.', '-')
            .mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }

    private fun reason(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "Нет связи с GitHub"
        is java.net.SocketTimeoutException -> "GitHub не отвечает"
        is java.io.FileNotFoundException -> "Релиз не найден"
        else -> e.message ?: "Ошибка обновления"
    }
}
