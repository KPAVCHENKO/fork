package app.fork.messenger.media

import android.graphics.Bitmap
import android.util.Log

/**
 * JNI bindings to the native rlottie engine (see app/src/main/cpp/rlottie_jni.cpp).
 * Mirrors how Telegram renders TGS: parse once, render frames into a Bitmap.
 */
object RLottie {
    @Volatile var available = false
        private set

    init {
        available = runCatching { System.loadLibrary("forklottie") }
            .onFailure { Log.w("RLottie", "native lib unavailable: ${it.message}") }
            .isSuccess
    }

    /** Loads an animation from raw (un-gzipped) Lottie JSON. Returns a native handle, or 0. */
    external fun nativeLoad(json: String): Long

    external fun nativeFrameCount(ptr: Long): Int
    external fun nativeFrameRate(ptr: Long): Double

    /** Renders [frame] into [bitmap] (ARGB_8888, size w×h). */
    external fun nativeRender(ptr: Long, frame: Int, bitmap: Bitmap, w: Int, h: Int)

    external fun nativeDestroy(ptr: Long)
}
