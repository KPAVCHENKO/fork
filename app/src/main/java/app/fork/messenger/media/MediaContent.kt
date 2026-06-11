package app.fork.messenger.media

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fork.messenger.ui.ForkIcons
import app.fork.messenger.ui.TimestampStyle
import app.fork.messenger.ui.forkTokens
import coil.compose.AsyncImage
import java.io.File
import org.drinkless.tdlib.TdApi

// Выбор размера фото: для пузыря — средний, для просмотрщика — самый большой.
fun TdApi.Photo.inlineSize(): TdApi.PhotoSize? =
    sizes.firstOrNull { it.type == "x" } ?: sizes.maxByOrNull { it.width }

fun TdApi.Photo.fullSize(): TdApi.PhotoSize? =
    sizes.maxByOrNull { it.width.toLong() * it.height }

private fun aspect(width: Int, height: Int): Float =
    if (width <= 0 || height <= 0) 1f else (width.toFloat() / height).coerceIn(0.65f, 1.4f)

/** Картинка медиа: показывает мини-превью сразу, поверх — полный файл по мере загрузки. */
@Composable
fun MediaImage(
    file: TdApi.File,
    mini: TdApi.Minithumbnail?,
    width: Int,
    height: Int,
    modifier: Modifier = Modifier,
    priority: Int = 24,
    shape: androidx.compose.ui.graphics.Shape? = null,
    onClick: (() -> Unit)? = null,
) {
    val state = rememberFileState(file, autoDownload = true, priority = priority)
    val miniBitmap = remember(mini) {
        mini?.data?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    Box(
        modifier
            .widthIn(max = 252.dp)
            .aspectRatio(aspect(width, height))
            // Радиус медиа = радиус пузыря − 3dp; для «голых» медиа — форма пузыря.
            .clip(shape ?: RoundedCornerShape(forkTokens.bubbleRadius - 3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (miniBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = miniBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        val path = state.path
        if (path != null) {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else if (state.downloading) {
            CircularProgressIndicator(
                progress = { state.progress.coerceAtLeast(0.02f) },
                color = Color.White,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
fun PhotoContent(photo: TdApi.Photo, shape: androidx.compose.ui.graphics.Shape? = null, onOpen: () -> Unit) {
    val size = photo.inlineSize() ?: return
    MediaImage(
        file = size.photo,
        mini = photo.minithumbnail,
        width = size.width,
        height = size.height,
        priority = 28,
        shape = shape,
        onClick = onOpen,
    )
}

@Composable
fun VideoContent(video: TdApi.Video, shape: androidx.compose.ui.graphics.Shape? = null, onOpen: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        val thumb = video.thumbnail?.file
        if (thumb != null) {
            MediaImage(
                file = thumb,
                mini = video.minithumbnail,
                width = video.width,
                height = video.height,
                priority = 20,
                shape = shape,
                onClick = onOpen,
            )
        }
        // Кнопка воспроизведения по центру — стеклянная капсула (Fork Design Spec §4.3).
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0x8C0E1424))
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ForkIcons.Play, contentDescription = "играть", tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Text(
            text = formatDuration(video.duration),
            color = Color.White,
            style = TimestampStyle,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0x8C050912))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun AnimationContent(animation: TdApi.Animation, shape: androidx.compose.ui.graphics.Shape? = null) {
    val context = LocalContext.current
    val state = rememberFileState(animation.animation, autoDownload = false, priority = 16)
    Box(contentAlignment = Alignment.Center) {
        val thumb = animation.thumbnail?.file
        if (thumb != null) {
            MediaImage(
                file = thumb,
                mini = animation.minithumbnail,
                width = animation.width,
                height = animation.height,
                priority = 18,
                shape = shape,
                onClick = {
                    state.path?.let { openExternally(context, it, animation.mimeType.ifBlank { "video/mp4" }) }
                        ?: FileHub.ensureDownloaded(animation.animation, 28)
                },
            )
        }
        Text(
            "GIF",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0x8C050912))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun VoiceContent(voice: TdApi.VoiceNote, mine: Boolean) {
    val context = LocalContext.current
    val tokens = forkTokens
    val state = rememberFileState(voice.voice, autoDownload = true, priority = 30)
    val playback by AudioPlayer.state.collectAsStateWithLifecycle()
    val isThis = playback?.fileId == voice.voice.id
    val isPlaying = isThis && playback?.isPlaying == true
    val progress = if (isThis) playback?.progress ?: 0f else 0f

    val bars = remember(voice.waveform) { decodeWaveform(voice.waveform) }
    // На градиентном пузыре — белые элементы, на входящем — фирменный градиент.
    val played = if (mine) listOf(Color.White) else tokens.waveformSteps
    val rest = when {
        mine -> Color.White.copy(alpha = 0.35f)
        tokens.dark -> Color.White.copy(alpha = 0.25f)
        else -> Color(0xFF171C26).copy(alpha = 0.2f)
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 248.dp)) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .then(
                    if (mine) Modifier.background(Color.White.copy(alpha = 0.22f))
                    else Modifier.background(tokens.brandGradient),
                )
                .clickable {
                    val path = state.path
                    if (path != null) AudioPlayer.toggle(context, voice.voice.id, path)
                },
            contentAlignment = Alignment.Center,
        ) {
            if (state.path == null && state.downloading) {
                CircularProgressIndicator(
                    progress = { state.progress.coerceAtLeast(0.02f) },
                    color = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Icon(
                    if (isPlaying) ForkIcons.Pause else ForkIcons.Play,
                    contentDescription = if (isPlaying) "пауза" else "играть",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Waveform(bars = bars, progress = progress, played = played, rest = rest)
            Spacer(Modifier.height(4.dp))
            val timeText = if (isThis && progress > 0f) {
                "${formatDuration((voice.duration * progress).toInt())} / ${formatDuration(voice.duration)}"
            } else {
                formatDuration(voice.duration)
            }
            Text(
                timeText,
                style = TimestampStyle,
                color = if (mine) Color.White.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Дорожка голосового: прослушанная часть прокрашивается ступенями
 * градиента индиго → циан (Fork Design Spec §7.5).
 */
@Composable
private fun Waveform(bars: IntArray, progress: Float, played: List<Color>, rest: Color) {
    Canvas(
        modifier = Modifier
            .width(160.dp)
            .height(26.dp),
    ) {
        if (bars.isEmpty()) return@Canvas
        val barWidth = size.width / (bars.size * 1.6f)
        val gap = barWidth * 0.6f
        val progressX = size.width * progress
        var x = 0f
        for (h in bars) {
            val norm = (h / 31f).coerceIn(0.1f, 1f)
            val barHeight = size.height * norm
            val top = (size.height - barHeight) / 2
            val color = if (x <= progressX) {
                gradientStep(played, x / size.width)
            } else {
                rest
            }
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(x, top),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )
            x += barWidth + gap
        }
    }
}

/** Цвет ступени градиента в точке t (0..1) — плавная интерполяция между стопами. */
private fun gradientStep(steps: List<Color>, t: Float): Color {
    if (steps.size == 1) return steps[0]
    val clamped = t.coerceIn(0f, 0.999f) * (steps.size - 1)
    val i = clamped.toInt()
    return lerp(steps[i], steps[minOf(i + 1, steps.size - 1)], clamped - i)
}

@Composable
fun DocumentContent(document: TdApi.Document, mine: Boolean = false) {
    val state = rememberFileState(document.document, autoDownload = false, priority = 16)
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(max = 248.dp)
            .clickable {
                state.path?.let { openExternally(context, it, document.mimeType) }
                    ?: FileHub.ensureDownloaded(document.document, 28)
            },
    ) {
        // Иконка-сквиркл 42dp (Fork Design Spec §4.3).
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (mine) Color.White.copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.primaryContainer,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val iconTint = if (mine) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
            if (state.downloading) {
                CircularProgressIndicator(
                    progress = { state.progress.coerceAtLeast(0.02f) },
                    color = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Icon(ForkIcons.Download, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                document.fileName.ifBlank { "Файл" },
                style = MaterialTheme.typography.titleSmall,
                color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                formatBytes(document.document.size),
                style = MaterialTheme.typography.bodyMedium,
                color = if (mine) Color.White.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StickerContent(sticker: TdApi.Sticker) {
    StickerView(sticker, modifier = Modifier.size(140.dp), play = true)
}

/**
 * Видеосообщение-«кружок»: превью с кнопкой, по тапу играет в круге со звуком.
 * Плеер создаётся ТОЛЬКО на время воспроизведения (урок ANR со стикерами).
 */
@Composable
fun VideoNoteContent(note: TdApi.VideoNote) {
    val state = rememberFileState(note.video, autoDownload = true, priority = 26)
    var playing by remember(note.video.id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(216.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable {
                val path = state.path
                if (path != null) playing = !playing else FileHub.ensureDownloaded(note.video, 30)
            },
        contentAlignment = Alignment.Center,
    ) {
        val path = state.path
        if (playing && path != null) {
            CircleVideoPlayer(path = path, onEnded = { playing = false })
        } else {
            val miniBitmap = remember(note.minithumbnail) {
                note.minithumbnail?.data?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
            val thumbState = rememberFileState(note.thumbnail?.file, autoDownload = true, priority = 20)
            if (miniBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = miniBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            thumbState.path?.let {
                AsyncImage(
                    model = File(it),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            if (state.path == null && state.downloading) {
                CircularProgressIndicator(
                    progress = { state.progress.coerceAtLeast(0.02f) },
                    color = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0x8C0E1424)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(ForkIcons.Play, contentDescription = "играть", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
        Text(
            text = formatDuration(note.duration),
            color = Color.White,
            style = TimestampStyle,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(10.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0x8C050912))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun CircleVideoPlayer(path: String, onEnded: () -> Unit) {
    val context = LocalContext.current
    val player = remember(path) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(File(path))))
            prepare()
            playWhenReady = true
        }
    }
    androidx.compose.runtime.DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) onEnded()
            }
        }
        player.addListener(listener)
        onDispose { player.release() }
    }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                this.player = player
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

// ---------- утилиты ----------

private fun openExternally(context: android.content.Context, path: String, mime: String) {
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", File(path),
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime.ifBlank { "*/*" })
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f МБ".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f КБ".format(bytes / 1_000.0)
    else -> "$bytes Б"
}

/** Распаковка 5-битной телеграмовской waveform в высоты 0..31. */
fun decodeWaveform(data: ByteArray?): IntArray {
    if (data == null || data.isEmpty()) return IntArray(0)
    val count = data.size * 8 / 5
    val result = IntArray(minOf(count, 100))
    for (i in result.indices) {
        val bitOffset = i * 5
        val byteIndex = bitOffset / 8
        val bitInByte = bitOffset % 8
        if (byteIndex + 1 >= data.size) {
            val v = (data[byteIndex].toInt() and 0xFF) shr bitInByte
            result[i] = v and 0x1F
        } else {
            val twoBytes = (data[byteIndex].toInt() and 0xFF) or ((data[byteIndex + 1].toInt() and 0xFF) shl 8)
            result[i] = (twoBytes shr bitInByte) and 0x1F
        }
    }
    return result
}
