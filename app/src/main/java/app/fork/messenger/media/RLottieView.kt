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

/**
 * TGS sticker rendering via native rlottie, tuned for Telegram-grade scroll
 * smoothness on weak devices.
 *
 * Jank avoidance (the parts that matter on a low-end phone):
 *  - ONE render thread for all stickers (limitedParallelism 1) — leaves the other
 *    cores entirely to the UI thread, so scrolling never competes with rendering.
 *  - Parsed animations are CACHED and ref-counted ([RLottieCache]) — scrolling a
 *    sticker out and back in does not re-parse its JSON.
 *  - Frame bitmaps come from a POOL ([StickerBitmapPool]) and are obtained inside
 *    the render coroutine, so the UI/composition thread never allocates 512 KB per
 *    sticker mid-fling (that was the freeze).
 *  - Frame updates are draw-phase only (Canvas reads the bitmap in its draw lambda)
 *    so a new frame triggers a repaint, never a recomposition or relayout.
 *  - Capped to ~30 fps with frame-skip (correct speed, half the work).
 */

// Single dedicated render thread for every sticker (mirrors Telegram's one rlottie
// DispatchQueue). Sequential rendering also makes shared cached handles safe.
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private val rlottieDispatcher = Dispatchers.Default.limitedParallelism(1)
private val rlottieScope = CoroutineScope(SupervisorJob() + rlottieDispatcher)

@Composable
fun RLottieView(
    rawJson: String,
    cacheKey: String,
    modifier: Modifier = Modifier,
    play: Boolean = true,
    renderPx: Int = 256,
) {
    var frame by remember(cacheKey) { mutableStateOf<ImageBitmap?>(null) }
    val playState = remember(cacheKey) { mutableStateOf(play) }
    playState.value = play

    DisposableEffect(cacheKey, renderPx) {
        // Nothing heavy here (runs on the composition thread): the coroutine does the
        // parse + bitmap allocation off the UI thread.
        val job = rlottieScope.launch {
            val ptr = RLottieCache.acquire(cacheKey, rawJson)
            if (ptr == 0L) return@launch
            val b0 = StickerBitmapPool.obtain(renderPx)
            val b1 = StickerBitmapPool.obtain(renderPx)
            try {
                val count = RLottie.nativeFrameCount(ptr).coerceAtLeast(1)
                val nativeFps = RLottie.nativeFrameRate(ptr).takeIf { it > 0 } ?: 60.0
                val step = (nativeFps / 30.0).toInt().coerceAtLeast(1)
                val frameDelay = (1000.0 * step / nativeFps).toLong().coerceAtLeast(20)
                var f = 0
                var useB0 = true
                var renderedFrame = -1
                while (isActive) {
                    val playing = playState.value
                    if (playing || renderedFrame != f) {
                        val target = if (useB0) b0 else b1
                        RLottie.nativeRender(ptr, f, target, renderPx, renderPx)
                        frame = target.asImageBitmap()
                        useB0 = !useB0
                        renderedFrame = f
                    }
                    if (playing) {
                        f = (f + step) % count
                        delay(frameDelay)
                    } else {
                        delay(120)
                    }
                }
            } finally {
                RLottieCache.release(cacheKey)
                StickerBitmapPool.recycle(b0)
                StickerBitmapPool.recycle(b1)
            }
        }
        onDispose { job.cancel() }
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

/**
 * Ref-counted cache of parsed rlottie animations keyed by sticker path. Released
 * handles go to a small idle LRU so scrolling back doesn't re-parse; evicted ones
 * are destroyed. All access is serialized; renders run on the single render thread.
 */
private object RLottieCache {
    private class Entry(val ptr: Long, var refs: Int)

    private val active = HashMap<String, Entry>()
    private val idle = LinkedHashMap<String, Long>() // LRU of parsed-but-unused
    private const val MAX_IDLE = 12

    @Synchronized
    fun acquire(key: String, json: String): Long {
        active[key]?.let { it.refs++; return it.ptr }
        idle.remove(key)?.let { ptr ->
            active[key] = Entry(ptr, 1)
            return ptr
        }
        val ptr = RLottie.nativeLoad(json)
        if (ptr == 0L) return 0L
        active[key] = Entry(ptr, 1)
        return ptr
    }

    @Synchronized
    fun release(key: String) {
        val e = active[key] ?: return
        e.refs--
        if (e.refs <= 0) {
            active.remove(key)
            idle[key] = e.ptr
            while (idle.size > MAX_IDLE) {
                val oldest = idle.entries.iterator().next()
                idle.remove(oldest.key)
                RLottie.nativeDestroy(oldest.value)
            }
        }
    }
}

/** Reuses ARGB_8888 bitmaps so scrolling doesn't churn 512 KB allocations per sticker. */
private object StickerBitmapPool {
    private val pool = ArrayDeque<Bitmap>()
    private const val MAX_POOL = 24

    @Synchronized
    fun obtain(size: Int): Bitmap {
        while (pool.isNotEmpty()) {
            val b = pool.removeLast()
            if (b.width == size && b.height == size && !b.isRecycled) {
                b.eraseColor(0)
                return b
            }
            if (!b.isRecycled) b.recycle()
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    }

    @Synchronized
    fun recycle(b: Bitmap) {
        if (b.isRecycled) return
        if (pool.size < MAX_POOL) pool.addLast(b) else b.recycle()
    }
}
