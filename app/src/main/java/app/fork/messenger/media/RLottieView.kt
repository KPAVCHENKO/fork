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

// Bounded render pool: cap concurrent native renders so animated stickers never
// monopolise the CPU and steal frames from scrolling. 2 threads handle a screenful
// of stickers fine (each render is a few ms and the loops mostly idle on delay()).
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private val rlottieDispatcher = Dispatchers.Default.limitedParallelism(2)
private val rlottieScope = CoroutineScope(SupervisorJob() + rlottieDispatcher)

/**
 * Renders a TGS sticker via native rlottie (Telegram's engine). Frames are
 * rasterized on a bounded background pool into double-buffered bitmaps and blitted
 * by the UI thread.
 *
 * [play] controls animation: when false the LAST rendered frame is held (not swapped
 * to a thumbnail), so fast scrolling stays smooth with no flicker and the sticker
 * resumes instantly when the list settles.
 */
@Composable
fun RLottieView(
    rawJson: String,
    modifier: Modifier = Modifier,
    play: Boolean = true,
    renderPx: Int = 256,
) {
    var frame by remember(rawJson) { mutableStateOf<ImageBitmap?>(null) }
    // Read latest play without restarting the render loop on every scroll toggle.
    val playState = remember(rawJson) { mutableStateOf(play) }
    playState.value = play

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
                var renderedFrame = -1
                while (isActive) {
                    val playing = playState.value
                    if (playing || renderedFrame != f) {
                        val target = buffers[idx]
                        RLottie.nativeRender(ptr, f, target, renderPx, renderPx)
                        frame = target.asImageBitmap()
                        idx = 1 - idx
                        renderedFrame = f
                    }
                    if (playing) {
                        f = (f + 1) % count
                        delay(frameDelay)
                    } else {
                        // Paused (scrolling): hold the current frame cheaply.
                        delay(120)
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
