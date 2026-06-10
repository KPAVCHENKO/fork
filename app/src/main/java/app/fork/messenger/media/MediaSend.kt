package app.fork.messenger.media

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/** Помощники для отправки медиа: контент из галереи нужно скопировать в файл,
 * т.к. TDLib принимает путь к файлу (InputFileLocal), а не content:// URI. */
object MediaSend {

    /** Копирует выбранный из галереи файл во временную папку и возвращает путь. */
    fun copyToCache(context: Context, uri: Uri, extension: String): File? = runCatching {
        val dir = File(context.cacheDir, "outgoing").apply { mkdirs() }
        val file = File(dir, "send_${System.currentTimeMillis()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
        } ?: return null
        file
    }.getOrNull()

    /** Размеры изображения без загрузки в память целиком. */
    fun imageSize(path: String): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        return opts.outWidth.coerceAtLeast(0) to opts.outHeight.coerceAtLeast(0)
    }

    data class VideoInfo(val width: Int, val height: Int, val durationSeconds: Int)

    /** Ширина/высота/длительность видео через MediaMetadataRetriever. */
    fun videoInfo(path: String): VideoInfo = runCatching {
        android.media.MediaMetadataRetriever().use { mmr ->
            mmr.setDataSource(path)
            fun meta(key: Int) = mmr.extractMetadata(key)?.toIntOrNull() ?: 0
            val rotation = meta(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            var w = meta(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            var h = meta(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (rotation == 90 || rotation == 270) { val t = w; w = h; h = t }
            val durationMs = meta(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            VideoInfo(w, h, (durationMs / 1000).coerceAtLeast(1))
        }
    }.getOrDefault(VideoInfo(0, 0, 0))

    fun isVideo(context: Context, uri: Uri): Boolean =
        context.contentResolver.getType(uri)?.startsWith("video/") == true
}
