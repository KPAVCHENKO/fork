package app.fork.messenger

import android.app.ActivityManager
import android.content.Context

/**
 * Класс производительности устройства (как в Telegram: LOW / AVERAGE / HIGH). Определяется
 * один раз по числу ядер и объёму ОЗУ; по нему масштабируем тяжёлые эффекты — на слабых
 * телефонах отключаем размытие-стекло, режем число одновременных видео-стикеров и снижаем
 * разрешение рендера, чтобы держать плавность.
 */
object PerfClass {
    enum class Level { LOW, AVERAGE, HIGH }

    @Volatile
    var level: Level = Level.AVERAGE
        private set

    fun init(context: Context) {
        val cores = Runtime.getRuntime().availableProcessors()
        val totalRamGb = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            mi.totalMem / (1024.0 * 1024.0 * 1024.0)
        }.getOrDefault(4.0)

        level = when {
            cores >= 8 && totalRamGb >= 6 -> Level.HIGH
            cores <= 4 || totalRamGb < 3 -> Level.LOW
            else -> Level.AVERAGE
        }
    }

    /** Размытие-стекло (Haze) — дорогое, на слабых выключаем (останется полупрозрачность). */
    val blurEnabled: Boolean get() = level != Level.LOW

    /** Сколько видео-стикеров (пар VP9-декодеров) держать одновременно. */
    val maxWebmEngines: Int get() = when (level) {
        Level.LOW -> 3
        Level.HIGH -> 10
        Level.AVERAGE -> 6
    }

    /** Разрешение рендера TGS-стикеров (px по стороне). */
    val stickerRenderPx: Int get() = if (level == Level.LOW) 192 else 256
}
