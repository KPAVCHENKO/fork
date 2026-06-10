package app.fork.messenger.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.fork.messenger.TdClient
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.drinkless.tdlib.TdApi

/**
 * Единая точка отслеживания файлов TDLib: прогресс докачки приходит через
 * updateFile, а UI подписывается на состояние нужного файла по его id.
 */
object FileHub {
    private val flows = ConcurrentHashMap<Int, MutableStateFlow<TdApi.File>>()

    fun handleUpdate(obj: TdApi.Object) {
        if (obj is TdApi.UpdateFile) {
            flows[obj.file.id]?.value = obj.file
        }
    }

    fun flow(file: TdApi.File): MutableStateFlow<TdApi.File> {
        val existing = flows[file.id]
        if (existing != null) {
            // Если пришёл более «полный» снимок файла — обновим.
            if (file.local.isDownloadingCompleted && !existing.value.local.isDownloadingCompleted) {
                existing.value = file
            }
            return existing
        }
        return flows.getOrPut(file.id) { MutableStateFlow(file) }
    }

    fun ensureDownloaded(file: TdApi.File, priority: Int) {
        if (!file.local.isDownloadingCompleted && !file.local.isDownloadingActive) {
            TdClient.send(TdApi.DownloadFile(file.id, priority, 0, 0, false))
        }
    }

    fun localPath(file: TdApi.File): String? =
        file.local.path.takeIf { file.local.isDownloadingCompleted && it.isNotEmpty() }
}

/** Текущее состояние файла для UI: путь (если скачан) и прогресс 0..1. */
data class FileState(val path: String?, val progress: Float, val downloading: Boolean)

/**
 * Возвращает живое состояние файла: подписывается на updateFile и при
 * необходимости запускает докачку. priority: 32 — на экране, 16 — впрок.
 */
@Composable
fun rememberFileState(file: TdApi.File?, autoDownload: Boolean = true, priority: Int = 24): FileState {
    if (file == null) return FileState(null, 0f, false)
    val flow = remember(file.id) { FileHub.flow(file) }
    val current by flow.collectAsStateWithLifecycle()

    LaunchedEffect(file.id, autoDownload) {
        if (autoDownload) FileHub.ensureDownloaded(current, priority)
    }

    val local = current.local
    val total = if (current.expectedSize > 0) current.expectedSize else current.size
    val progress = if (total > 0) (local.downloadedSize.toFloat() / total).coerceIn(0f, 1f) else 0f
    return FileState(
        path = FileHub.localPath(current),
        progress = progress,
        downloading = local.isDownloadingActive,
    )
}
