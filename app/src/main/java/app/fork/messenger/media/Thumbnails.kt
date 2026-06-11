package app.fork.messenger.media

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi

/**
 * Декодер мини-эскизов (minithumbnail, размытый JPEG ~40px). Раньше декодировали
 * прямо в composition на главном потоке — при прокрутке это давало фризы. Теперь
 * декодирование на Dispatchers.Default + общий LRU-кэш (ключ — сам массив байт).
 */
object MiniThumbs {
    private val cache = LruCache<ByteArray, ImageBitmap>(160)

    /** Готовый эскиз из кэша без декодирования (для мгновенного первого кадра). */
    fun cached(data: ByteArray?): ImageBitmap? = data?.let { cache.get(it) }

    /** Декодирует (если ещё нет в кэше) и кладёт в кэш. Звать вне главного потока. */
    fun decode(data: ByteArray?): ImageBitmap? {
        data ?: return null
        cache.get(data)?.let { return it }
        val bmp = runCatching {
            BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
        }.getOrNull() ?: return null
        cache.put(data, bmp)
        return bmp
    }
}

/** Декодирует мини-эскиз вне главного потока; до готовности отдаёт кэш или null. */
@Composable
fun rememberMiniThumb(mini: TdApi.Minithumbnail?): ImageBitmap? {
    val data = mini?.data
    val bitmap by produceState<ImageBitmap?>(MiniThumbs.cached(data), data) {
        if (value == null && data != null) {
            value = withContext(Dispatchers.Default) { MiniThumbs.decode(data) }
        }
    }
    return bitmap
}
