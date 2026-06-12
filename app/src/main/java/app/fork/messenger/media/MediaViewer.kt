package app.fork.messenger.media

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.fork.messenger.ui.ForkIcons
import coil.compose.AsyncImage
import java.io.File
import org.drinkless.tdlib.TdApi

/** Что показываем во весь экран. */
sealed interface MediaTarget {
    data class Photo(val photo: TdApi.Photo) : MediaTarget
    data class Video(val video: TdApi.Video) : MediaTarget
}

/** Одиночное медиа (без листания) — для экранов без списка (инфо чата и т.п.). */
@Composable
fun MediaViewer(target: MediaTarget, onClose: () -> Unit) = MediaViewer(listOf(target), 0, onClose)

/** Ключ медиа по id файла — для поиска стартового индекса в списке. */
fun mediaKey(t: MediaTarget): Long = when (t) {
    is MediaTarget.Photo -> (t.photo.fullSize()?.photo?.id ?: t.photo.sizes.lastOrNull()?.photo?.id ?: 0).toLong()
    is MediaTarget.Video -> t.video.video.id.toLong()
}

/**
 * Полноэкранный просмотр с ЛИСТАНИЕМ между всеми медиа чата (свайп влево/вправо, как в
 * TG). Фото — с зумом, видео — ExoPlayer. Кнопки сохранить/поделиться/копировать
 * действуют на текущую страницу; сверху — счётчик «N / M».
 */
@Composable
fun MediaViewer(targets: List<MediaTarget>, startIndex: Int, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    if (targets.isEmpty()) return
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = startIndex.coerceIn(0, targets.size - 1),
    ) { targets.size }
    // Когда текущее фото увеличено — отключаем листание, чтобы свайп двигал фото.
    var zoomed by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 16.dp,
            userScrollEnabled = !zoomed,
        ) { page ->
            val isCurrent = page == pagerState.currentPage
            when (val t = targets[page]) {
                is MediaTarget.Photo -> PhotoViewer(
                    t.photo,
                    onZoomChange = { if (isCurrent) zoomed = it },
                )
                is MediaTarget.Video -> VideoViewer(t.video)
            }
        }

        val current = targets[pagerState.currentPage]
        val isVideo = current is MediaTarget.Video
        val mainFile = when (current) {
            is MediaTarget.Photo -> current.photo.fullSize()?.photo ?: current.photo.inlineSize()?.photo
            is MediaTarget.Video -> current.video.video
        }
        val savePath = rememberFileState(mainFile, autoDownload = false, priority = 1).path

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(8.dp),
        ) {
            Icon(ForkIcons.ArrowBack, contentDescription = "закрыть", tint = Color.White)
        }

        if (targets.size > 1) {
            Text(
                "${pagerState.currentPage + 1} / ${targets.size}",
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .systemBarsPadding()
                    .padding(top = 16.dp),
            )
        }

        if (savePath != null) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .systemBarsPadding()
                    .padding(4.dp),
            ) {
                if (!isVideo) {
                    IconButton(onClick = {
                        val ok = copyImage(context, savePath)
                        android.widget.Toast.makeText(
                            context, if (ok) "Скопировано" else "Не удалось скопировать",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }) {
                        Icon(ForkIcons.Copy, contentDescription = "копировать", tint = Color.White)
                    }
                }
                IconButton(onClick = { shareMedia(context, savePath, isVideo) }) {
                    Icon(ForkIcons.Forward, contentDescription = "поделиться", tint = Color.White)
                }
                IconButton(onClick = {
                    val ok = saveToGallery(context, savePath, isVideo)
                    android.widget.Toast.makeText(
                        context,
                        if (ok) "Сохранено в галерею" else "Не удалось сохранить",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }) {
                    Icon(ForkIcons.Download, contentDescription = "сохранить", tint = Color.White)
                }
            }
        }
    }
}

/** Делится медиа через системный диалог (FileProvider). */
private fun shareMedia(context: android.content.Context, srcPath: String, isVideo: Boolean) {
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", File(srcPath),
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (isVideo) "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

/** Копирует изображение в буфер обмена. */
private fun copyImage(context: android.content.Context, srcPath: String): Boolean = runCatching {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", File(srcPath),
    )
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
        as android.content.ClipboardManager
    val clip = android.content.ClipData.newUri(context.contentResolver, "image", uri)
    clipboard.setPrimaryClip(clip)
    true
}.getOrDefault(false)

/** Сохраняет скачанный медиафайл в галерею (Pictures/Fork или Movies/Fork). */
private fun saveToGallery(context: android.content.Context, srcPath: String, isVideo: Boolean): Boolean =
    runCatching {
        val src = File(srcPath)
        if (!src.exists()) return false
        val name = "Fork_${System.currentTimeMillis()}." + if (isVideo) "mp4" else "jpg"
        val resolver = context.contentResolver
        val collection = if (isVideo) {
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val dir = if (isVideo) android.os.Environment.DIRECTORY_MOVIES else android.os.Environment.DIRECTORY_PICTURES
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "$dir/Fork")
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values) ?: return false
        resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } } ?: return false
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            values.clear()
            values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    }.getOrDefault(false)

@Composable
private fun PhotoViewer(photo: TdApi.Photo, onZoomChange: (Boolean) -> Unit = {}) {
    val size = photo.fullSize() ?: photo.inlineSize() ?: return
    val state = rememberFileState(size.photo, autoDownload = true, priority = 32)
    val mini = remember(photo.minithumbnail) {
        photo.minithumbnail?.data?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        // Зумим/двигаем; пока увеличено — сообщаем наружу, чтобы пейджер не перехватывал
        // горизонтальный свайп (двигаем фото, а не листаем страницы).
        if ((newScale > 1.01f) != (scale > 1.01f)) onZoomChange(newScale > 1.01f)
        scale = newScale
        if (scale > 1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            offsetX = 0f; offsetY = 0f
        }
    }
    // Двойной тап — переключить зум (приблизить/сбросить).
    val tapModifier = Modifier.pointerInput(Unit) {
        detectTapGestures(onDoubleTap = {
            if (scale > 1.01f) {
                scale = 1f; offsetX = 0f; offsetY = 0f; onZoomChange(false)
            } else {
                scale = 2.5f; onZoomChange(true)
            }
        })
    }

    val path = state.path
    val modifier = Modifier
        .fillMaxSize()
        .graphicsLayer(
            scaleX = scale, scaleY = scale,
            translationX = offsetX, translationY = offsetY,
        )
        // Жесты зума/панорамы перехватываем ТОЛЬКО когда увеличено — иначе горизонтальный
        // свайп уходит пейджеру и фото листаются (раньше transformable съедал свайп).
        .transformable(transform, enabled = scale > 1f)
        .then(tapModifier)

    when {
        path != null -> AsyncImage(
            model = File(path), contentDescription = null,
            contentScale = ContentScale.Fit, modifier = modifier,
        )
        mini != null -> androidx.compose.foundation.Image(
            bitmap = mini.asImageBitmap(), contentDescription = null,
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
        )
        else -> CircularProgressIndicator(color = Color.White)
    }
    if (path == null && mini != null) {
        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoViewer(video: TdApi.Video) {
    val context = LocalContext.current
    val state = rememberFileState(video.video, autoDownload = true, priority = 32)
    val path = state.path

    if (path == null) {
        CircularProgressIndicator(
            progress = { state.progress.coerceAtLeast(0.02f) },
            color = Color.White,
            modifier = Modifier.size(48.dp),
        )
        Text(
            "${(state.progress * 100).toInt()}%",
            color = Color.White,
            modifier = Modifier.padding(top = 64.dp),
        )
        return
    }

    val exoPlayer = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(path))))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
