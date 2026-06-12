package app.fork.messenger.media

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * VP9-with-alpha sticker engine — the real fix for "black square" video stickers.
 *
 * Telegram video stickers are WEBM/VP9 with an ALPHA channel. In WebM the alpha is a
 * SECOND VP9 stream carried per-frame in BlockAdditional (BlockAddID=1) — the standard
 * video pipeline (ExoPlayer/MediaExtractor) never even sees it, which is why stickers
 * played as opaque black squares.
 *
 * This engine does what ffmpeg/Chrome do:
 *  - parse the WebM container ourselves (small EBML walker below) and pull out BOTH
 *    per-frame payloads: the main VP9 frame and the alpha VP9 frame;
 *  - run TWO MediaCodec VP9 decoders in lockstep (hardware when available) — one for
 *    color, one for the alpha plane;
 *  - merge YUV(color) + Y(alpha) into ARGB bitmaps off the UI thread and draw them in
 *    the Compose draw phase only (like RLottieView) — no AndroidView/SurfaceView, so
 *    scrolling stays smooth, corners clip correctly, and transparency just works.
 *
 * Looping: we never flush the codecs. The frame sequence restarts from the keyframe with
 * monotonically increasing pts — for the decoder it's one continuous, valid stream
 * (flush()-based looping froze on some OMX decoders).
 */

private const val TAG = "ForkWebm"

// ---------------------------------------------------------------------------
// WebM (Matroska) demuxer — just enough EBML to extract VP8/VP9 + alpha frames.
// ---------------------------------------------------------------------------

internal class WebmFrame(val data: ByteArray, val alpha: ByteArray?, val timeMs: Long)

internal class WebmVideo(
    val width: Int,
    val height: Int,
    val mime: String,
    val hasAlpha: Boolean,
    val frames: List<WebmFrame>,
)

internal object WebmDemuxer {
    private const val SEGMENT = 0x18538067
    private const val INFO = 0x1549A966
    private const val TIMECODE_SCALE = 0x2AD7B1
    private const val TRACKS = 0x1654AE6B
    private const val TRACK_ENTRY = 0xAE
    private const val TRACK_NUMBER = 0xD7
    private const val TRACK_TYPE = 0x83
    private const val CODEC_ID = 0x86
    private const val VIDEO = 0xE0
    private const val PIXEL_WIDTH = 0xB0
    private const val PIXEL_HEIGHT = 0xBA
    private const val CLUSTER = 0x1F43B675
    private const val CLUSTER_TIMECODE = 0xE7
    private const val SIMPLE_BLOCK = 0xA3
    private const val BLOCK_GROUP = 0xA0
    private const val BLOCK = 0xA1
    private const val BLOCK_ADDITIONS = 0x75A1
    private const val BLOCK_MORE = 0xA6
    private const val BLOCK_ADD_ID = 0xEE
    private const val BLOCK_ADDITIONAL = 0xA5

    fun parse(d: ByteArray): WebmVideo? = runCatching { doParse(d) }
        .onFailure { Log.e(TAG, "demux failed: ${it.message}") }
        .getOrNull()

    /**
     * Length of an EBML vint by its first byte (1..8 bytes; ffmpeg/libwebm пишут размер
     * Segment 8-байтовым vint — поддержка только 1..4 ломала разбор любого файла).
     */
    private fun vintLen(first: Int): Int? = when {
        first >= 0x80 -> 1
        first >= 0x40 -> 2
        first >= 0x20 -> 3
        first >= 0x10 -> 4
        first >= 0x08 -> 5
        first >= 0x04 -> 6
        first >= 0x02 -> 7
        first >= 0x01 -> 8
        else -> null
    }

    private fun doParse(d: ByteArray): WebmVideo? {
        var p = 0

        // Element ID (Matroska IDs — 1..4 байта, маркер-бит сохраняется).
        fun readId(): Int {
            val len = vintLen(d[p].toInt() and 0xFF) ?: return -1
            if (len > 4) return -1
            var v = 0
            repeat(len) { v = (v shl 8) or (d[p].toInt() and 0xFF); p++ }
            return v
        }

        /** Element size; -2 = unknown (до конца родителя/файла), -1 = ошибка. */
        fun readSize(): Long {
            val first = d[p].toInt() and 0xFF
            val len = vintLen(first) ?: return -1
            var v = (first and (0xFF ushr len)).toLong(); p++
            repeat(len - 1) { v = (v shl 8) or (d[p].toInt() and 0xFF).toLong(); p++ }
            return if (v == (1L shl (7 * len)) - 1) -2 else v
        }

        fun readUint(end: Int): Long {
            var v = 0L
            while (p < end) { v = (v shl 8) or (d[p].toInt() and 0xFF).toLong(); p++ }
            return v
        }

        var timecodeScale = 1_000_000L // нс на единицу таймкода (дефолт Matroska)
        var videoTrack = -1L
        var width = 0
        var height = 0
        var mime = ""
        val frames = ArrayList<WebmFrame>(128)

        /** Block/SimpleBlock payload → кадр видеотрека (+опц. альфа). */
        fun addBlock(start: Int, end: Int, clusterTime: Long, alphaBytes: ByteArray?) {
            var q = start
            val first = d[q].toInt() and 0xFF
            val len = vintLen(first) ?: return
            var track = (first and (0xFF ushr len)).toLong(); q++
            repeat(len - 1) { track = (track shl 8) or (d[q].toInt() and 0xFF).toLong(); q++ }
            if (track != videoTrack) return
            val rel = (((d[q].toInt() and 0xFF) shl 8) or (d[q + 1].toInt() and 0xFF)).toShort().toInt()
            q += 2
            val flags = d[q].toInt(); q++
            if ((flags shr 1) and 3 != 0) return // лейсинг для видео не встречается
            val timeMs = (clusterTime + rel) * timecodeScale / 1_000_000
            frames.add(WebmFrame(d.copyOfRange(q, end), alphaBytes, timeMs))
        }

        // --- EBML header ---
        if (readId() != 0x1A45DFA3) return null
        val hSize = readSize()
        if (hSize < 0) return null
        p += hSize.toInt()

        if (readId() != SEGMENT) return null
        val segSize = readSize()
        if (segSize == -1L) return null
        val segEnd = if (segSize == -2L) d.size else (p + segSize).toInt().coerceAtMost(d.size)

        while (p < segEnd - 1) {
            val id = readId()
            val size = readSize()
            if (id < 0 || size == -1L) return null
            val end = if (size == -2L) segEnd else (p + size).toInt().coerceAtMost(d.size)
            when (id) {
                INFO -> {
                    while (p < end - 1) {
                        val cid = readId(); val csz = readSize()
                        if (cid < 0 || csz < 0) return null
                        val cend = (p + csz).toInt()
                        if (cid == TIMECODE_SCALE) timecodeScale = readUint(cend) else p = cend
                    }
                    p = end
                }
                TRACKS -> {
                    while (p < end - 1) {
                        val tid = readId(); val tsz = readSize()
                        if (tid < 0 || tsz < 0) return null
                        val tend = (p + tsz).toInt()
                        if (tid == TRACK_ENTRY) {
                            var num = -1L; var type = -1L; var codec = ""
                            var w = 0; var h = 0
                            while (p < tend - 1) {
                                val eid = readId(); val esz = readSize()
                                if (eid < 0 || esz < 0) return null
                                val eend = (p + esz).toInt()
                                when (eid) {
                                    TRACK_NUMBER -> num = readUint(eend)
                                    TRACK_TYPE -> type = readUint(eend)
                                    CODEC_ID -> { codec = String(d, p, esz.toInt()).trimEnd(' '); p = eend }
                                    VIDEO -> {
                                        while (p < eend - 1) {
                                            val vid = readId(); val vsz = readSize()
                                            if (vid < 0 || vsz < 0) return null
                                            val vend = (p + vsz).toInt()
                                            when (vid) {
                                                PIXEL_WIDTH -> w = readUint(vend).toInt()
                                                PIXEL_HEIGHT -> h = readUint(vend).toInt()
                                                else -> p = vend
                                            }
                                        }
                                        p = eend
                                    }
                                    else -> p = eend
                                }
                            }
                            if (type == 1L && videoTrack == -1L && codec.startsWith("V_VP")) {
                                videoTrack = num; width = w; height = h
                                mime = if (codec == "V_VP9") "video/x-vnd.on2.vp9" else "video/x-vnd.on2.vp8"
                            }
                            p = tend
                        } else p = tend
                    }
                    p = end
                }
                CLUSTER -> {
                    var clusterTime = 0L
                    while (p < end - 1) {
                        val cid = readId(); val csz = readSize()
                        if (cid < 0 || csz < 0) return null
                        val cend = (p + csz).toInt().coerceAtMost(d.size)
                        when (cid) {
                            CLUSTER_TIMECODE -> clusterTime = readUint(cend)
                            SIMPLE_BLOCK -> { addBlock(p, cend, clusterTime, null); p = cend }
                            BLOCK_GROUP -> {
                                var blockStart = -1; var blockEnd = -1
                                var alphaBytes: ByteArray? = null
                                while (p < cend - 1) {
                                    val gid = readId(); val gsz = readSize()
                                    if (gid < 0 || gsz < 0) return null
                                    val gend = (p + gsz).toInt()
                                    when (gid) {
                                        BLOCK -> { blockStart = p; blockEnd = gend; p = gend }
                                        BLOCK_ADDITIONS -> {
                                            while (p < gend - 1) {
                                                val mid = readId(); val msz = readSize()
                                                if (mid < 0 || msz < 0) return null
                                                val mend = (p + msz).toInt()
                                                if (mid == BLOCK_MORE) {
                                                    var addId = 1L
                                                    var add: ByteArray? = null
                                                    while (p < mend - 1) {
                                                        val aid = readId(); val asz = readSize()
                                                        if (aid < 0 || asz < 0) return null
                                                        val aend = (p + asz).toInt()
                                                        when (aid) {
                                                            BLOCK_ADD_ID -> addId = readUint(aend)
                                                            BLOCK_ADDITIONAL -> { add = d.copyOfRange(p, aend); p = aend }
                                                            else -> p = aend
                                                        }
                                                    }
                                                    if (addId == 1L) alphaBytes = add
                                                    p = mend
                                                } else p = mend
                                            }
                                            p = gend
                                        }
                                        else -> p = gend
                                    }
                                }
                                if (blockStart >= 0) addBlock(blockStart, blockEnd, clusterTime, alphaBytes)
                                p = cend
                            }
                            else -> p = cend
                        }
                    }
                    p = end
                }
                else -> p = end
            }
        }

        if (videoTrack == -1L || width <= 0 || height <= 0 || frames.isEmpty()) {
            Log.e(TAG, "demux: no usable video (track=$videoTrack ${width}x$height frames=${frames.size})")
            return null
        }
        frames.sortBy { it.timeMs }
        val hasAlpha = frames.all { it.alpha != null }
        return WebmVideo(width, height, mime, hasAlpha, frames)
    }
}

// ---------------------------------------------------------------------------
// Dual-decoder engine + Compose view
// ---------------------------------------------------------------------------

/**
 * Видео-стикер с прозрачностью. Рисуется как rlottie: фоновые потоки декодируют,
 * Canvas читает кадр только в draw-фазе. При любой ошибке остаётся базовая миниатюра.
 */
@Composable
fun WebmAlphaView(path: String, modifier: Modifier = Modifier) {
    var frame by remember(path) { mutableStateOf<ImageBitmap?>(null) }

    DisposableEffect(path) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            runCatching { runEngine(path) { img -> frame = img } }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException) Log.e(TAG, "engine failed: $it") }
        }
        onDispose { scope.cancel() }
    }

    Canvas(modifier) {
        frame?.let { drawFitted(it) }
    }
}

/** Кадр вписывается в канву с сохранением пропорций (стикеры бывают не квадратными). */
private fun DrawScope.drawFitted(img: ImageBitmap) {
    val scale = minOf(size.width / img.width, size.height / img.height)
    val w = (img.width * scale).toInt()
    val h = (img.height * scale).toInt()
    drawImage(
        image = img,
        dstOffset = IntOffset(((size.width - w) / 2f).toInt(), ((size.height - h) / 2f).toInt()),
        dstSize = IntSize(w, h),
    )
}

private suspend fun runEngine(path: String, onFrame: (ImageBitmap) -> Unit) {
    val webm = WebmDemuxer.parse(File(path).readBytes()) ?: return
    val frames = webm.frames
    val n = frames.size
    Log.e(TAG, "start ${webm.width}x${webm.height} n=$n alpha=${webm.hasAlpha} ${webm.mime}")

    // Рендерим с даунсемплированием 512→256 (как rlottie): мельче глазу не нужно,
    // а merge-цикл в 4 раза дешевле.
    val scaleDown = if (maxOf(webm.width, webm.height) > 320) 2 else 1
    val outW = webm.width / scaleDown
    val outH = webm.height / scaleDown
    val pixels = IntArray(outW * outH)
    var front = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    var back = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)

    // Длительность одного прохода: последний таймкод + средний шаг кадра.
    val avgStep = if (n > 1) (frames.last().timeMs / (n - 1)).coerceAtLeast(16) else 33
    val loopDurMs = frames.last().timeMs + avgStep

    val ctx = currentCoroutineContext()

    fun newCodec(): MediaCodec = MediaCodec.createDecoderByType(webm.mime).apply {
        val fmt = MediaFormat.createVideoFormat(webm.mime, webm.width, webm.height)
        fmt.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
        )
        configure(fmt, null, null, 0)
        start()
    }

    var main = newCodec()
    var alpha = if (webm.hasAlpha) newCodec() else null
    var restarts = 0

    try {
        val infoM = MediaCodec.BufferInfo()
        val infoA = MediaCodec.BufferInfo()
        var fedM = 0L
        var fedA = 0L
        var merged = 0L
        var heldM = -1
        var heldA = -1
        var anchorNs = System.nanoTime()
        var stallSince = 0L

        while (ctx.isActive) {
            var progressed = false

            // Кормим декодеры жадно: бесконечный поток (цикл анимации повторяется,
            // pts монотонно растёт — flush не нужен).
            run {
                val i = main.dequeueInputBuffer(0)
                if (i >= 0) {
                    val f = frames[(fedM % n).toInt()]
                    val pts = ((fedM / n) * loopDurMs + f.timeMs) * 1000
                    val b = main.getInputBuffer(i)!!
                    b.clear(); b.put(f.data)
                    main.queueInputBuffer(i, 0, f.data.size, pts, 0)
                    fedM++; progressed = true
                }
            }
            alpha?.let { a ->
                val i = a.dequeueInputBuffer(0)
                if (i >= 0) {
                    val f = frames[(fedA % n).toInt()]
                    val ad = f.alpha!!
                    val pts = ((fedA / n) * loopDurMs + f.timeMs) * 1000
                    val b = a.getInputBuffer(i)!!
                    b.clear(); b.put(ad)
                    a.queueInputBuffer(i, 0, ad.size, pts, 0)
                    fedA++; progressed = true
                }
            }

            // Забираем выходы; держим по одному, пока не готова пара.
            if (heldM < 0) {
                val i = main.dequeueOutputBuffer(infoM, 4_000)
                if (i >= 0) { heldM = i; progressed = true }
            }
            if (alpha != null && heldA < 0) {
                val i = alpha!!.dequeueOutputBuffer(infoA, 4_000)
                if (i >= 0) { heldA = i; progressed = true }
            }

            if (heldM >= 0 && (alpha == null || heldA >= 0)) {
                val imgM = main.getOutputImage(heldM)
                val imgA = if (alpha != null && heldA >= 0) alpha!!.getOutputImage(heldA) else null
                if (imgM != null) mergeYuvAlpha(imgM, imgA, pixels, outW, outH)
                imgM?.close(); imgA?.close()
                main.releaseOutputBuffer(heldM, false); heldM = -1
                if (alpha != null && heldA >= 0) { alpha!!.releaseOutputBuffer(heldA, false); heldA = -1 }

                // Пейсинг по таймкодам контейнера; при сильном отставании — пере-якорь.
                val offMs = (merged / n) * loopDurMs + frames[(merged % n).toInt()].timeMs
                val waitMs = (anchorNs + offMs * 1_000_000 - System.nanoTime()) / 1_000_000
                if (waitMs > 0) delay(waitMs)
                else if (waitMs < -250) anchorNs = System.nanoTime() - offMs * 1_000_000

                back.setPixels(pixels, 0, outW, 0, 0, outW, outH)
                onFrame(back.asImageBitmap())
                val t = front; front = back; back = t
                merged++
                restarts = 0
                progressed = true

                if (n == 1) {
                    // Единственный кадр: показали и спим, не гоняя декодер.
                    while (ctx.isActive) delay(60_000)
                }
            }

            if (!progressed) {
                val now = System.nanoTime()
                if (stallSince == 0L) {
                    stallSince = now
                } else if (now - stallSince > 1_200_000_000L) {
                    // Декодер заклинило — полное пересоздание (несколько попыток).
                    restarts++
                    Log.e(TAG, "stall, restart #$restarts (merged=$merged)")
                    if (restarts > 3) return
                    runCatching { main.stop() }; runCatching { main.release() }
                    alpha?.let { runCatching { it.stop() }; runCatching { it.release() } }
                    main = newCodec()
                    alpha = if (webm.hasAlpha) newCodec() else null
                    fedM = 0; fedA = 0; merged = 0; heldM = -1; heldA = -1
                    anchorNs = System.nanoTime()
                    stallSince = 0L
                }
                delay(5)
            } else {
                stallSince = 0L
            }
        }
    } finally {
        runCatching { main.stop() }; runCatching { main.release() }
        alpha?.let { a -> runCatching { a.stop() }; runCatching { a.release() } }
    }
}

/**
 * YUV420 (цвет) + Y-плоскость альфа-декодера → ARGB. BT.601 video range —
 * то, чем кодирует libvpx у Telegram. Семплирование с даунскейлом на лету.
 */
private fun mergeYuvAlpha(imgM: Image, imgA: Image?, out: IntArray, outW: Int, outH: Int) {
    val crop = imgM.cropRect
    val srcW = crop.width()
    val srcH = crop.height()
    val pY = imgM.planes[0]; val pU = imgM.planes[1]; val pV = imgM.planes[2]
    val bY = pY.buffer; val bU = pU.buffer; val bV = pV.buffer
    val pA = imgA?.planes?.get(0)
    val bA = pA?.buffer
    val aCrop = imgA?.cropRect

    var di = 0
    for (oy in 0 until outH) {
        val sy = crop.top + oy * srcH / outH
        val rowY = sy * pY.rowStride
        val rowU = (sy shr 1) * pU.rowStride
        val rowV = (sy shr 1) * pV.rowStride
        val rowA = if (pA != null && aCrop != null) {
            (aCrop.top + oy * aCrop.height() / outH) * pA.rowStride
        } else 0
        for (ox in 0 until outW) {
            val sx = crop.left + ox * srcW / outW
            val y = (bY.get(rowY + sx * pY.pixelStride).toInt() and 0xFF)
            val u = (bU.get(rowU + (sx shr 1) * pU.pixelStride).toInt() and 0xFF) - 128
            val v = (bV.get(rowV + (sx shr 1) * pV.pixelStride).toInt() and 0xFF) - 128
            val a = if (pA != null && bA != null && aCrop != null) {
                bA.get(rowA + (aCrop.left + ox * aCrop.width() / outW) * pA.pixelStride).toInt() and 0xFF
            } else 0xFF
            val c = 298 * (y - 16)
            var r = (c + 409 * v + 128) shr 8
            var g = (c - 100 * u - 208 * v + 128) shr 8
            var b = (c + 516 * u + 128) shr 8
            if (r < 0) r = 0 else if (r > 255) r = 255
            if (g < 0) g = 0 else if (g > 255) g = 255
            if (b < 0) b = 0 else if (b > 255) b = 255
            out[di++] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
