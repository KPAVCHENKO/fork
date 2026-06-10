package app.fork.messenger.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Фирменные цвета Fork — индиго и циан, как на иконке
private val BrandPrimary = Color(0xFF2362FD)
private val BrandSecondary = Color(0xFF00B8D9)

private val LightScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = Color(0xFF002463),
    secondary = BrandSecondary,
    surface = Color(0xFFFDFCFF),
    background = Color(0xFFFDFCFF),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9DB7FF),
    onPrimary = Color(0xFF00297A),
    primaryContainer = Color(0xFF1F44A8),
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Color(0xFF4DD8F0),
    surface = Color(0xFF121318),
    background = Color(0xFF121318),
)

/**
 * Тема приложения: на Android 12+ подхватывает системные динамические цвета
 * (Material You), на старых версиях — фирменная палитра Fork.
 */
@Composable
fun ForkTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Градиентные пары для аватаров-заглушек (в духе Telegram, но свои). */
private val AvatarGradients = listOf(
    listOf(Color(0xFFFF885E), Color(0xFFFF516A)),
    listOf(Color(0xFFFFCD6A), Color(0xFFFFA85C)),
    listOf(Color(0xFFA0DE7E), Color(0xFF54CB68)),
    listOf(Color(0xFF53EDD6), Color(0xFF28C9B7)),
    listOf(Color(0xFF72D5FD), Color(0xFF2A9EF1)),
    listOf(Color(0xFFE0A2F3), Color(0xFFD669ED)),
    listOf(Color(0xFF82B1FF), Color(0xFF565FE8)),
)

fun avatarBrush(seed: Long): Brush {
    val colors = AvatarGradients[(kotlin.math.abs(seed) % AvatarGradients.size).toInt()]
    return Brush.linearGradient(colors)
}

fun senderColor(seed: Long): Color =
    AvatarGradients[(kotlin.math.abs(seed) % AvatarGradients.size).toInt()][1]
