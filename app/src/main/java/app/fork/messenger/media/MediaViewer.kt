package app.fork.messenger.media

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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

/** Полноэкранный просмотр фото (с зумом) и видео (ExoPlayer). */
@Composable
fun MediaViewer(target: MediaTarget, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when (target) {
            is MediaTarget.Photo -> PhotoViewer(target.photo)
            is MediaTarget.Video -> VideoViewer(target.video)
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(8.dp),
        ) {
            Icon(ForkIcons.ArrowBack, contentDescription = "закрыть", tint = Color.White)
        }
    }
}

@Composable
private fun PhotoViewer(photo: TdApi.Photo) {
    val size = photo.fullSize() ?: photo.inlineSize() ?: return
    val state = rememberFileState(size.photo, autoDownload = true, priority = 32)
    val mini = remember(photo.minithumbnail) {
        photo.minithumbnail?.data?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offsetX += panChange.x
        offsetY += panChange.y
        if (scale == 1f) { offsetX = 0f; offsetY = 0f }
    }

    val path = state.path
    val modifier = Modifier
        .fillMaxSize()
        .graphicsLayer(
            scaleX = scale, scaleY = scale,
            translationX = offsetX, translationY = offsetY,
        )
        .transformable(transform)

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
