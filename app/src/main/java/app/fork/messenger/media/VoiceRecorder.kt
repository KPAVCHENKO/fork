package app.fork.messenger.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import app.fork.messenger.MessageStore
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Запись голосовых в OGG/Opus (формат голосовых Telegram). Доступно на Android 10+
 * (на более старых MediaRecorder не умеет Opus).
 */
object VoiceRecorder {
    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var path: String? = null
    private var startedAt = 0L

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun start(context: Context): Boolean {
        if (!isSupported || _recording.value) return false
        return runCatching {
            val dir = File(context.cacheDir, "outgoing").apply { mkdirs() }
            val file = File(dir, "voice_${System.currentTimeMillis()}.ogg")
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION") MediaRecorder()
            }
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.OGG)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            rec.setAudioSamplingRate(48000)
            rec.setAudioEncodingBitRate(32000)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            path = file.absolutePath
            startedAt = System.currentTimeMillis()
            _recording.value = true
            true
        }.getOrElse {
            cleanup(deleteFile = true)
            false
        }
    }

    /** Останавливает запись и отправляет, если она длиннее ~1 секунды. */
    fun stopAndSend() {
        if (!_recording.value) return
        val durationSec = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        val file = path
        val ok = runCatching { recorder?.stop() }.isSuccess
        cleanup(deleteFile = false)
        if (ok && file != null && durationSec >= 1) {
            MessageStore.sendVoice(file, durationSec, ByteArray(0))
        } else if (file != null) {
            File(file).delete()
        }
    }

    fun cancel() {
        if (!_recording.value) return
        runCatching { recorder?.stop() }
        path?.let { File(it).delete() }
        cleanup(deleteFile = false)
    }

    private fun cleanup(deleteFile: Boolean) {
        runCatching { recorder?.release() }
        recorder = null
        if (deleteFile) path?.let { File(it).delete() }
        path = null
        _recording.value = false
    }
}
