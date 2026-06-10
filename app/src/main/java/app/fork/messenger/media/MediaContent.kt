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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fork.messenger.ui.ForkIcons
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
    onClick: (() -> Unit)? = null,
) {
    val state = rememberFileState(file, autoDownload = true, priority = priority)
    val miniBitmap = remember(mini) {
        mini?.data?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    Box(
        modifier
            .widthIn(max = 248.dp)
            .aspectRatio(aspect(width, height))
            .clip(RoundedCornerShape(16.dp))
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
fun PhotoContent(photo: TdApi.Photo, onOpen: () -> Unit) {
    val size = photo.inlineSize() ?: return
    MediaImage(
        file = size.photo,
        mini = photo.minithumbnail,
        width = size.width,
        height = size.height,
        priority = 28,
        onClick = onOpen,
    )
}

@Composable
fun VideoContent(video: TdApi.Video, onOpen: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        val thumb = video.thumbnail?.file
        if (thumb != null) {
            MediaImage(
                file = thumb,
                mini = video.minithumbnail,
                width = video.width,
                height = video.height,
                priority = 20,
                onClick = onOpen,
            )
        }
        // Кнопка воспроизведения по центру.
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ForkIcons.Play, contentDescription = "играть", tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Text(
            text = formatDuration(video.duration),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun AnimationContent(animation: TdApi.Animation) {
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
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun VoiceContent(voice: TdApi.VoiceNote, mine: Boolean) {
    val context = LocalContext.current
    val state = rememberFileState(voice.voice, autoDownload = true, priority = 30)
    val playback by AudioPlayer.state.collectAsStateWithLifecycle()
    val isThis = playback?.fileId == voice.voice.id
    val isPlaying = isThis && playback?.isPlaying == true
    val progress = if (isThis) playback?.progress ?: 0f else 0f

    val bars = remember(voice.waveform) { decodeWaveform(voice.waveform) }
    val tint = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 240.dp)) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(tint)
                .clickable {
                    val path = state.path
                    if (path != null) AudioPlayer.toggle(context, voice.voice.id, path)
                },
            contentAlignment = Alignment.Center,
        ) {
            if (state.path == null && state.downloading) {
                CircularProgressIndicator(
                    progress = { state.progress.coerceAtLeast(0.02f) },
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Icon(
                    if (isPlaying) ForkIcons.Pause else ForkIcons.Play,
                    contentDescription = if (isPlaying) "пауза" else "играть",
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Waveform(bars = bars, progress = progress, color = tint)
            Spacer(Modifier.height(4.dp))
            Text(
                formatDuration(voice.duration),
                style = MaterialTheme.typography.labelSmall,
                color = tint.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun Waveform(bars: IntArray, progress: Float, color: Color) {
    val played = color
    val rest = color.copy(alpha = 0.3f)
    Canvas(
        modifier = Modifier
            .width(160.dp)
            .height(24.dp),
    ) {
        if (bars.isEmpty()) return@Canvas
        val barWidth = size.width / (bars.size * 1.6f)
        val gap = barWidth * 0.6f
        val progressX = size.width * progress
        var x = 0f
        for (h in bars) {
            val norm = (h / 31f).coerceIn(0.08f, 1f)
            val barHeight = size.height * norm
            val top = (size.height - barHeight) / 2
            drawRoundRect(
                color = if (x <= progressX) played else rest,
                topLeft = androidx.compose.ui.geometry.Offset(x, top),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )
            x += barWidth + gap
        }
    }
}

@Composable
fun DocumentContent(document: TdApi.Document) {
    val state = rememberFileState(document.document, autoDownload = false, priority = 16)
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(max = 240.dp)
            .clickable {
                state.path?.let { openExternally(context, it, document.mimeType) }
                    ?: FileHub.ensureDownloaded(document.document, 28)
            },
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            if (state.downloading) {
                CircularProgressIndicator(
                    progress = { state.progress.coerceAtLeast(0.02f) },
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Icon(ForkIcons.Download, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                document.fileName.ifBlank { "Файл" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                formatBytes(document.document.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StickerContent(sticker: TdApi.Sticker) {
    when (sticker.format) {
        // Анимированные TGS = gzip-сжатый Lottie JSON, рендерим через lottie-compose.
        is TdApi.StickerFormatTgs -> TgsSticker(sticker)
        else -> {
            // WEBP — картинка; WEBM (видео-стикеры) — пока статичное превью.
            val file = if (sticker.format is TdApi.StickerFormatWebp) sticker.sticker
            else (sticker.thumbnail?.file ?: sticker.sticker)
            val state = rememberFileState(file, autoDownload = true, priority = 26)
            Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                val path = state.path
                if (path != null) {
                    AsyncImage(model = File(path), contentDescription = sticker.emoji, modifier = Modifier.matchParentSize())
                } else {
                    Text(sticker.emoji, style = MaterialTheme.typography.displaySmall)
                }
            }
        }
    }
}

@Composable
private fun TgsSticker(sticker: TdApi.Sticker) {
    val state = rememberFileState(sticker.sticker, autoDownload = true, priority = 26)
    val path = state.path

    Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
        if (path == null) {
            Text(sticker.emoji, style = MaterialTheme.typography.displaySmall)
            return@Box
        }
        // Распаковываем gzip в фоне.
        val json by androidx.compose.runtime.produceState<String?>(null, path) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    java.util.zip.GZIPInputStream(File(path).inputStream())
                        .bufferedReader().use { it.readText() }
                }.getOrNull()
            }
        }
        val data = json
        if (data == null) {
            Text(sticker.emoji, style = MaterialTheme.typography.displaySmall)
        } else {
            val composition by com.airbnb.lottie.compose.rememberLottieComposition(
                com.airbnb.lottie.compose.LottieCompositionSpec.JsonString(data),
            )
            com.airbnb.lottie.compose.LottieAnimation(
                composition = composition,
                iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
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
