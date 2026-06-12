package app.fork.messenger.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Sticker rendering, performance-tuned for sticker-heavy chats.
 *
 * Performance model:
 *  - A static thumbnail (Coil) is ALWAYS the base layer, so a sticker is never
 *    invisible while an engine warms up.
 *  - TGS animates via native rlottie, WEBM via the VP9-with-alpha engine
 *    (WebmSticker.kt). BOTH render bitmaps in the Compose draw phase only —
 *    no AndroidView/SurfaceView — so they animate even while scrolling without
 *    janking the list (same model as Telegram).
 *  - A global cap bounds concurrent WEBM decoder pairs; stickers over the cap
 *    show the static thumbnail until a slot frees up.
 *  - Gunzipped TGS JSON is cached by file path to avoid re-reading/inflating on
 *    every scroll-back.
 */

/** path -> inflated Lottie JSON, so scrolling back doesn't re-inflate. */
private val tgsJsonCache = android.util.LruCache<String, String>(24)

/**
 * Static, animation-free sticker preview for grids (sticker panel, set sheet).
 * Never creates players — hundreds of cells stay cheap.
 */
@Composable
fun StickerThumb(sticker: TdApi.Sticker, modifier: Modifier = Modifier) {
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
    val isTgs = sticker.format is TdApi.StickerFormatTgs && RLottie.available
    val isWebm = sticker.format is TdApi.StickerFormatWebm

    // Static thumbnail base — always under the animation (same box/scale), so the
    // sticker is never blank while the engine warms up.
    val thumb = sticker.thumbnail?.file
    val thumbPath = if (thumb != null) rememberFileState(thumb, true, 20).path else null
    val state = rememberFileState(sticker.sticker, autoDownload = true, priority = 24)
    val path = state.path

    Box(modifier, contentAlignment = Alignment.Center) {
        val staticPath = thumbPath ?: path.takeIf { sticker.format is TdApi.StickerFormatWebp }
        if (staticPath != null) {
            AsyncImage(
                model = File(staticPath),
                contentDescription = sticker.emoji,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        when {
            // TGS animates ALWAYS via rlottie (cheap, draw-phase only).
            isTgs && path != null -> TgsView(path, play = true)
            // WEBM animates ALWAYS via the VP9-with-alpha engine (draw-phase only, real
            // transparency). One decoder per UNIQUE sticker is shared across all its
            // on-screen copies (WebmEnginePool) — copies stay in phase, fewer decoders.
            isWebm && path != null ->
                WebmAlphaView(path, modifier = Modifier.fillMaxSize())
            // static WEBP is already shown as the base image above.
        }
    }
}

@Composable
private fun TgsView(path: String, play: Boolean) {
    val json by produceState<String?>(tgsJsonCache.get(path), path) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    java.util.zip.GZIPInputStream(File(path).inputStream())
                        .bufferedReader().use { it.readText() }
                }.getOrNull()?.also { tgsJsonCache.put(path, it) }
            }
        }
    }
    val data = json ?: return
    if (RLottie.available) {
        // Native rlottie — the same engine Telegram uses; renders frames off the UI
        // thread for smooth playback on weak devices.
        RLottieView(rawJson = data, cacheKey = path, modifier = Modifier.fillMaxSize(), play = play)
    } else {
        // Fallback to lottie-compose if the native lib failed to load.
        val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(data))
        LottieAnimation(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
