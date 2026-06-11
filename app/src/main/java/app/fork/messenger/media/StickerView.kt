package app.fork.messenger.media

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi

/**
 * Универсальный рендер стикера по его формату:
 * - WEBP — статичная картинка (Coil),
 * - TGS — gzip-сжатый Lottie JSON (анимируется),
 * - WEBM — видео-стикер (ExoPlayer, зацикленный, без звука).
 * Используется и в сообщениях, и в панели стикеров.
 */
/**
 * Лёгкое СТАТИЧНОЕ превью стикера для сеток (панель стикеров): только картинка-
 * миниатюра через Coil, без ExoPlayer/Lottie. Так панель из сотен стикеров не
 * плодит плееры и не вешает систему (ANR). Анимация — только в сообщении.
 */
@Composable
fun StickerThumb(sticker: TdApi.Sticker, modifier: Modifier = Modifier) {
    // У статичных (WEBP) показываем сам стикер; у TGS/WEBM — статичную миниатюру.
    val file = if (sticker.format is TdApi.StickerFormatWebp) sticker.sticker
    else (sticker.thumbnail?.file ?: sticker.sticker)
    val state = rememberFileState(file, autoDownload = true, priority = 14)
    val path = state.path
    Box(modifier, contentAlignment = Alignment.Center) {
        if (path != null) {
            AsyncImage(model = File(path), contentDescription = sticker.emoji)
        }
    }
}

@Composable
fun StickerView(sticker: TdApi.Sticker, modifier: Modifier = Modifier, play: Boolean = true) {
    val state = rememberFileState(sticker.sticker, autoDownload = true, priority = 24)
    val path = state.path

    // Статичная миниатюра ВСЕГДА под анимацией — так стикер виден сразу и не пропадает,
    // даже если анимированный формат (премиум/видео-стикер) не успел/не смог декодироваться.
    val thumb = sticker.thumbnail?.file
    val thumbPath = if (thumb != null) rememberFileState(thumb, true, 20).path else null

    Box(modifier, contentAlignment = Alignment.Center) {
        if (thumbPath != null) {
            AsyncImage(model = File(thumbPath), contentDescription = sticker.emoji)
        } else if (sticker.format is TdApi.StickerFormatWebp && path != null) {
            AsyncImage(model = File(path), contentDescription = sticker.emoji)
        }
        if (path != null) {
            when (sticker.format) {
                is TdApi.StickerFormatTgs -> TgsView(path, play)
                is TdApi.StickerFormatWebm -> WebmView(path, play)
                else -> AsyncImage(model = File(path), contentDescription = sticker.emoji)
            }
        }
    }
}

@Composable
private fun TgsView(path: String, play: Boolean) {
    val json by produceState<String?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                java.util.zip.GZIPInputStream(File(path).inputStream())
                    .bufferedReader().use { it.readText() }
            }.getOrNull()
        }
    }
    val data = json ?: return
    val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(data))
    LottieAnimation(
        composition = composition,
        iterations = if (play) LottieConstants.IterateForever else 1,
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun WebmView(path: String, play: Boolean) {
    val context = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(path))))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            playWhenReady = play
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(factory = { ctx ->
        PlayerView(ctx).apply {
            this.player = player
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    })
}
