package app.fork.messenger.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Состояние воспроизведения одного голосового/аудио. */
data class PlaybackState(
    val fileId: Int,
    val isPlaying: Boolean,
    val progress: Float, // 0..1
)

/**
 * Один общий ExoPlayer на всё приложение: играем по одному голосовому за раз.
 * Работает на главном потоке (ExoPlayer этого требует).
 */
object AudioPlayer {
    private val _state = MutableStateFlow<PlaybackState?>(null)
    val state: StateFlow<PlaybackState?> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var currentFileId: Int = 0
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var ticker: Job? = null

    /** Нажатие на голосовое: играет, если другое/остановлено; ставит на паузу, если играет это же. */
    fun toggle(context: Context, fileId: Int, path: String) {
        val p = player ?: ExoPlayer.Builder(context.applicationContext).build().also {
            it.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) stop()
                }
            })
            player = it
        }

        if (currentFileId == fileId && p.isPlaying) {
            p.pause()
            publish(false)
            return
        }

        if (currentFileId != fileId) {
            p.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(java.io.File(path))))
            p.prepare()
            currentFileId = fileId
        }
        p.play()
        publish(true)
        startTicker()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                val p = player ?: break
                val dur = p.duration.takeIf { it > 0 } ?: 1L
                val progress = (p.currentPosition.toFloat() / dur).coerceIn(0f, 1f)
                _state.value = PlaybackState(currentFileId, p.isPlaying, progress)
                if (!p.isPlaying) break
                delay(80)
            }
        }
    }

    private fun publish(isPlaying: Boolean) {
        val p = player
        val dur = p?.duration?.takeIf { it > 0 } ?: 1L
        val progress = ((p?.currentPosition ?: 0L).toFloat() / dur).coerceIn(0f, 1f)
        _state.value = PlaybackState(currentFileId, isPlaying, progress)
    }

    fun stop() {
        ticker?.cancel()
        player?.pause()
        _state.value = currentFileId.let { PlaybackState(it, false, 0f) }
    }
}
