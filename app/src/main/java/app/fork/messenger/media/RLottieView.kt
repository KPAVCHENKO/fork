package app.fork.messenger.media

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val rlottieScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Renders a TGS sticker via the native rlottie engine (Telegram's renderer).
 *
 * Frames are rasterized on a background thread into double-buffered bitmaps and
 * published to Compose as an ImageBitmap — the UI thread only blits, so many
 * stickers animate smoothly even on weak devices. [rawJson] is the un-gzipped
 * Lottie JSON; [play] gates the animation (paused = last frame stays shown).
 */
@Composable
fun RLottieView(
    rawJson: String,
    modifier: Modifier = Modifier,
    play: Boolean = true,
    renderPx: Int = 320,
) {
    var frame by remember(rawJson) { mutableStateOf<ImageBitmap?>(null) }

    DisposableEffect(rawJson, renderPx) {
        val buffers = arrayOf(
            Bitmap.createBitmap(renderPx, renderPx, Bitmap.Config.ARGB_8888),
            Bitmap.createBitmap(renderPx, renderPx, Bitmap.Config.ARGB_8888),
        )
        val job = rlottieScope.launch {
            val ptr = RLottie.nativeLoad(rawJson)
            if (ptr == 0L) return@launch
            try {
                val count = RLottie.nativeFrameCount(ptr).coerceAtLeast(1)
                val fps = RLottie.nativeFrameRate(ptr).takeIf { it > 0 } ?: 60.0
                val frameDelay = (1000.0 / fps).toLong().coerceAtLeast(16)
                var f = 0
                var idx = 0
                // Render at least the first frame even when paused, so it isn't blank.
                while (isActive) {
                    val target = buffers[idx]
                    RLottie.nativeRender(ptr, f, target, renderPx, renderPx)
                    frame = target.asImageBitmap()
                    idx = 1 - idx
                    if (play) {
                        f = (f + 1) % count
                        delay(frameDelay)
                    } else {
                        delay(120) // idle: cheap poll until play resumes
                    }
                }
            } finally {
                RLottie.nativeDestroy(ptr)
            }
        }
        onDispose {
            job.cancel()
            rlottieScope.launch {
                delay(80)
                buffers.forEach { if (!it.isRecycled) it.recycle() }
            }
        }
    }

    Canvas(modifier) {
        frame?.let { img -> drawScaled(img) }
    }
}

/** Draws the square frame scaled to fit the canvas, centered. */
private fun DrawScope.drawScaled(img: ImageBitmap) {
    val side = minOf(size.width, size.height)
    val left = (size.width - side) / 2f
    val top = (size.height - side) / 2f
    drawImage(
        image = img,
        dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
        dstSize = androidx.compose.ui.unit.IntSize(side.toInt(), side.toInt()),
    )
}
