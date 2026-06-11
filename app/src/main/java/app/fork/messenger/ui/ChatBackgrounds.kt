package app.fork.messenger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.random.Random

/**
 * Обои чата (Fork Design Spec §3.8): 6 встроенных фонов, каждый = градиент +
 * векторный узор. Рисуются одним Canvas без bitmap-ассетов; узор статичен.
 */
enum class ChatWallpaper(val id: String, val title: String) {
    GLOW("glow", "Сияние"),
    DOODLE("doodle", "Дудлы"),
    WAVES("waves", "Волны"),
    RINGS("rings", "Кольца"),
    NORTH("north", "Север"),
    SUNSET("sunset", "Закат");

    companion object {
        fun byId(id: String): ChatWallpaper = entries.firstOrNull { it.id == id } ?: GLOW
    }
}

private val Indigo = Color(0xFF2362FD)
private val Cyan = Color(0xFF00B8D9)
private val Violet = Color(0xFF8B5CF6)

/** Полотно обоев; dim — затемнение узора 0..0.6 (scrim #04070E). */
@Composable
fun ChatWallpaperCanvas(
    wallpaper: ChatWallpaper,
    dark: Boolean,
    amoled: Boolean,
    dim: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        drawWallpaper(wallpaper, dark = dark, amoled = amoled)
        if (dim > 0f) {
            drawRect(Color(0xFF04070E).copy(alpha = dim.coerceIn(0f, 0.6f)))
        }
    }
}

private fun DrawScope.drawWallpaper(wallpaper: ChatWallpaper, dark: Boolean, amoled: Boolean) {
    // AMOLED: база уводится к чёрному, узор приглушается ×0.8 (по спеке).
    val patternScale = if (amoled && dark) 0.8f else 1f
    when (wallpaper) {
        ChatWallpaper.GLOW -> drawGlow(dark, amoled, patternScale)
        ChatWallpaper.DOODLE -> drawDoodle(dark, amoled, patternScale)
        ChatWallpaper.WAVES -> drawWaves(dark, amoled, patternScale)
        ChatWallpaper.RINGS -> drawRings(dark, amoled, patternScale)
        ChatWallpaper.NORTH -> drawNorth(dark, amoled, patternScale)
        ChatWallpaper.SUNSET -> drawSunset(dark, amoled, patternScale)
    }
}

private fun DrawScope.base(dark: Boolean, amoled: Boolean, top: Color, bottom: Color) {
    val colors = when {
        amoled && dark -> listOf(top.darkenToAmoled(), bottom.darkenToAmoled())
        dark -> listOf(top, bottom)
        else -> listOf(Color(0xFFEEF3FB), Color(0xFFE3EAF7))
    }
    drawRect(
        Brush.linearGradient(
            colors = colors,
            start = Offset(size.width * 0.15f, 0f),
            end = Offset(size.width * 0.85f, size.height),
        ),
    )
}

private fun Color.darkenToAmoled(): Color =
    Color(red = red * 0.35f, green = green * 0.35f, blue = blue * 0.35f)

/** Цвет узора: на тёмной базе — белый, на светлой — чёрный 8–10%. */
private fun patternColor(dark: Boolean, alpha: Float): Color =
    if (dark) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = (alpha * 0.7f).coerceAtMost(0.10f))

// ---------- 1. Сияние ----------

private fun DrawScope.drawGlow(dark: Boolean, amoled: Boolean, k: Float) {
    base(dark, amoled, Color(0xFF0F1729), Color(0xFF0A0F1E))
    val lightK = if (dark) 1f else 0.55f
    fun glow(center: Offset, radius: Float, color: Color, alpha: Float) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha * k * lightK), Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
    glow(Offset(size.width * 0.18f, size.height * 0.16f), size.width * 0.62f, Indigo, 0.52f)
    glow(Offset(size.width * 0.92f, size.height * 0.46f), size.width * 0.55f, Cyan, 0.36f)
    glow(Offset(size.width * 0.22f, size.height * 0.88f), size.width * 0.58f, Violet, 0.30f)
}

// ---------- 2. Дудлы ----------

private fun DrawScope.drawDoodle(dark: Boolean, amoled: Boolean, k: Float) {
    base(dark, amoled, Color(0xFF111A30), Color(0xFF0C1322))
    val tile = 170.dp.toPx()
    val stroke = Stroke(width = 1.6.dp.toPx())
    val color = patternColor(dark, 0.13f * k)
    var row = 0
    var y = -tile / 2
    while (y < size.height + tile) {
        var x = if (row % 2 == 0) -tile / 2 else 0f
        while (x < size.width + tile) {
            drawDoodleTile(Offset(x, y), tile, color, stroke)
            x += tile
        }
        y += tile
        row++
    }
}

/** Один тайл дудлов: вилка, сердце, звезда, пузырь, нота, «+», смайл. */
private fun DrawScope.drawDoodleTile(origin: Offset, tile: Float, color: Color, stroke: Stroke) {
    val u = tile / 100f
    fun at(dx: Float, dy: Float) = Offset(origin.x + dx * u, origin.y + dy * u)

    // Вилка: черенок + три зубца
    drawLine(color, at(18f, 12f), at(18f, 34f), stroke.width)
    for (i in -1..1) {
        drawLine(color, at(18f + i * 4f, 12f), at(18f + i * 4f, 20f), stroke.width)
    }
    // Сердце: две дуги
    val heart = Path().apply {
        moveTo(at(62f, 22f).x, at(62f, 22f).y)
        cubicTo(at(56f, 14f).x, at(56f, 14f).y, at(48f, 22f).x, at(48f, 22f).y, at(62f, 32f).x, at(62f, 32f).y)
        cubicTo(at(76f, 22f).x, at(76f, 22f).y, at(68f, 14f).x, at(68f, 14f).y, at(62f, 22f).x, at(62f, 22f).y)
    }
    drawPath(heart, color, style = stroke)
    // Звезда (4 луча)
    drawLine(color, at(88f, 50f), at(88f, 62f), stroke.width)
    drawLine(color, at(82f, 56f), at(94f, 56f), stroke.width)
    // Пузырь сообщения
    drawRoundRect(
        color = color,
        topLeft = at(10f, 58f),
        size = Size(24f * u, 16f * u),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * u),
        style = stroke,
    )
    drawLine(color, at(14f, 74f), at(18f, 78f), stroke.width)
    // Нота
    drawLine(color, at(52f, 62f), at(52f, 78f), stroke.width)
    drawCircle(color, 3.5f * u, at(49f, 79f), style = stroke)
    // «+»
    drawLine(color, at(34f, 38f), at(34f, 48f), stroke.width)
    drawLine(color, at(29f, 43f), at(39f, 43f), stroke.width)
    // Смайл
    drawCircle(color, 8f * u, at(78f, 84f), style = stroke)
    drawCircle(color, 1f * u, at(75f, 82f))
    drawCircle(color, 1f * u, at(81f, 82f))
}

// ---------- 3. Волны ----------

private fun DrawScope.drawWaves(dark: Boolean, amoled: Boolean, k: Float) {
    base(dark, amoled, Color(0xFF0B1120), Color(0xFF0E1A33))
    val colors = listOf(Indigo, Cyan, Indigo, Cyan, Violet)
    val alphas = listOf(0.24f, 0.20f, 0.16f, 0.14f, 0.12f)
    for (i in 0 until 5) {
        val yBase = size.height * (0.08f + i * 0.07f)
        val amp = size.height * 0.03f
        val path = Path().apply {
            moveTo(-20f, yBase)
            cubicTo(
                size.width * 0.25f, yBase - amp,
                size.width * 0.4f, yBase + amp,
                size.width * 0.62f, yBase - amp * 0.4f,
            )
            cubicTo(
                size.width * 0.8f, yBase - amp * 1.3f,
                size.width * 0.9f, yBase + amp * 0.6f,
                size.width + 20f, yBase - amp * 0.2f,
            )
        }
        drawPath(
            path,
            colors[i].copy(alpha = alphas[i] * k * (if (dark) 1f else 0.5f)),
            style = Stroke(width = (1.6f + i * 0.2f).dp.toPx()),
        )
    }
    // Две мягкие заливки снизу
    for ((i, c) in listOf(Indigo, Cyan).withIndex()) {
        val yTop = size.height * (0.78f + i * 0.1f)
        val blob = Path().apply {
            moveTo(-20f, size.height + 20f)
            lineTo(-20f, yTop + size.height * 0.06f)
            cubicTo(
                size.width * 0.3f, yTop - size.height * 0.05f,
                size.width * 0.7f, yTop + size.height * 0.07f,
                size.width + 20f, yTop,
            )
            lineTo(size.width + 20f, size.height + 20f)
            close()
        }
        drawPath(blob, c.copy(alpha = (0.12f - i * 0.02f) * k * (if (dark) 1f else 0.5f)))
    }
}

// ---------- 4. Кольца ----------

private fun DrawScope.drawRings(dark: Boolean, amoled: Boolean, k: Float) {
    base(dark, amoled, Color(0xFF0D1424), Color(0xFF0B101E))
    val tile = 260.dp.toPx()
    val stroke = Stroke(width = 1.4.dp.toPx())
    val lightK = if (dark) 1f else 0.5f
    var row = 0
    var y = 0f
    while (y < size.height + tile) {
        var x = if (row % 2 == 0) 0f else tile / 2
        while (x < size.width + tile) {
            val center = Offset(x, y)
            for (i in 1..4) {
                val color = if (i % 2 == 0) Indigo.copy(alpha = 0.14f * k * lightK)
                else Cyan.copy(alpha = 0.16f * k * lightK)
                drawCircle(color, radius = tile * 0.11f * i, center = center, style = stroke)
            }
            x += tile
        }
        y += tile * 0.85f
        row++
    }
}

// ---------- 5. Север ----------

private fun DrawScope.drawNorth(dark: Boolean, amoled: Boolean, k: Float) {
    val stops =
        if (dark && !amoled) listOf(Color(0xFF070D1C), Color(0xFF0B1530), Color(0xFF0D1830))
        else if (dark) listOf(Color(0xFF070D1C).darkenToAmoled(), Color(0xFF0B1530).darkenToAmoled(), Color(0xFF0D1830).darkenToAmoled())
        else listOf(Color(0xFFEEF3FB), Color(0xFFE3EAF7))
    drawRect(Brush.verticalGradient(stops))
    val lightK = if (dark) 1f else 0.5f

    // Две ленты северного сияния (повёрнутые градиентные полосы)
    for ((i, c) in listOf(Cyan, Violet).withIndex()) {
        rotate(degrees = -12f + i * 3f, pivot = Offset(size.width / 2, size.height * 0.3f)) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        c.copy(alpha = (0.34f - i * 0.1f) * k * lightK),
                        Color.Transparent,
                    ),
                ),
                topLeft = Offset(-size.width * 0.2f, size.height * (0.12f + i * 0.16f)),
                size = Size(size.width * 1.4f, size.height * 0.16f),
            )
        }
    }
    // Звёзды в верхней половине (фиксированный seed — узор стабилен)
    val rnd = Random(42)
    val starColor = patternColor(dark, 1f)
    repeat(64) {
        val x = rnd.nextFloat() * size.width
        val y = rnd.nextFloat() * size.height * 0.5f
        val r = (0.9f + rnd.nextFloat() * 0.7f).dp.toPx()
        val a = (0.24f + rnd.nextFloat() * 0.31f) * k
        drawCircle(starColor.copy(alpha = if (dark) a else a * 0.5f), r, Offset(x, y))
    }
}

// ---------- 6. Закат ----------

private fun DrawScope.drawSunset(dark: Boolean, amoled: Boolean, k: Float) {
    val stops =
        if (dark && !amoled) listOf(Color(0xFF131026), Color(0xFF1B1430), Color(0xFF241738))
        else if (dark) listOf(Color(0xFF131026).darkenToAmoled(), Color(0xFF1B1430).darkenToAmoled(), Color(0xFF241738).darkenToAmoled())
        else listOf(Color(0xFFFDF2EC), Color(0xFFF3E5EF))
    drawRect(Brush.verticalGradient(stops))
    val lightK = if (dark) 1f else 0.6f

    // Тёплое свечение у горизонта (45% высоты)
    val horizonY = size.height * 0.45f
    val glowCenter = Offset(size.width * 0.5f, horizonY)
    val radius = size.width * 0.75f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFF7A3D).copy(alpha = 0.38f * k * lightK),
                Color(0xFFFFB547).copy(alpha = 0.16f * k * lightK),
                Color.Transparent,
            ),
            center = glowCenter,
            radius = radius,
        ),
        radius = radius,
        center = glowCenter,
    )
    // Линия горизонта
    drawLine(
        color = Color(0xFFFF9F6E).copy(alpha = 0.30f * k * lightK),
        start = Offset(0f, horizonY),
        end = Offset(size.width, horizonY),
        strokeWidth = 1.2.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(min(size.width, 24f), 0f)),
    )
}
