package app.fork.messenger.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi

/**
 * Sticker rendering, performance-tuned for sticker-heavy chats.
 *
 * Performance model:
 *  - A static thumbnail (Coil) is ALWAYS the base layer, so a sticker is never
 *    invisible and the cheap path is taken while scrolling.
 *  - The expensive animated layer (Lottie for TGS, ExoPlayer for WEBM) is only
 *    composed when [play] is true. Callers pass play=false during fling, so no
 *    players/compositions are created while the list is moving.
 *  - A global semaphore caps concurrent animations (MAX_CONCURRENT). Stickers
 *    over the cap fall back to the static thumbnail until a slot frees up.
 *  - Gunzipped TGS JSON is cached by file path to avoid re-reading/inflating on
 *    every scroll-back.
 */

// Cap simultaneous WEBM players. Each is an ExoPlayer (VP9 decoder) rendering into a
// TextureView. Profiling showed that decoding several VP9 video stickers live WHILE the
// list flings spikes the UI thread (58% janky) no matter the surface type — the cost is
// the decode + frame upload, not the surface. So WEBM only plays once the list is at
// rest (like Telegram on weak devices); the TextureView still fixes the old SurfaceView
// "pops to a square for a frame" glitch and renders sticker transparency correctly.
private const val MAX_CONCURRENT_ANIMATIONS = 4
private val activeAnimations = AtomicInteger(0)

// A WEBM player starts shortly after scrolling settles. Kept low so playback feels
// near-instant (was 400 ms — felt like a half-second lag), but non-zero so a quick
// flick-through doesn't spin up VP9 decoders on every micro-pause.
private const val WEBM_IDLE_DELAY_MS = 130L

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

    // WEBM "goes live" only after the list settles (debounce) so flinging never spins up
    // VP9 decoders mid-scroll. TGS (rlottie) animates always; WEBM waits for rest.
    var webmReady by remember { mutableStateOf(false) }
    if (isWebm) {
        LaunchedEffect(play) {
            webmReady = if (play) {
                kotlinx.coroutines.delay(WEBM_IDLE_DELAY_MS)
                true
            } else {
                false
            }
        }
    }

    // Concurrency slot only for heavy WEBM (ExoPlayer). rlottie is bounded by its own
    // render pool, so TGS doesn't need a slot.
    var hasSlot by remember { mutableStateOf(false) }
    DisposableEffect(isWebm, webmReady, path) {
        if (isWebm && webmReady && path != null && !hasSlot &&
            activeAnimations.get() < MAX_CONCURRENT_ANIMATIONS
        ) {
            activeAnimations.incrementAndGet()
            hasSlot = true
        }
        onDispose {
            if (hasSlot) {
                activeAnimations.decrementAndGet()
                hasSlot = false
            }
        }
    }

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
            // TGS animates ALWAYS via rlottie (cheap, draw-phase only — no SurfaceView).
            isTgs && path != null -> TgsView(path, play = true)
            // WEBM via ExoPlayer into a TextureView (no SurfaceView jank/pop), but only
            // once the list is at rest ([webmReady]) and within the concurrency cap.
            isWebm && webmReady && path != null && hasSlot -> WebmView(path)
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

@OptIn(UnstableApi::class)
@Composable
private fun WebmView(path: String) {
    val context = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(path))))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    // TextureView (not SurfaceView): composites in-hierarchy, supports transparency, and
    // respects the composable's bounds — so video stickers don't flash a square frame or
    // jank the scroll. ExoPlayer renders straight into it.
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            android.view.TextureView(ctx).also { tv ->
                tv.isOpaque = false
                player.setVideoTextureView(tv)
            }
        },
    )
}
