package app.fork.messenger.ui

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fork.messenger.R
import app.fork.messenger.SettingsStore

// ---------------------------------------------------------------------------
// Бренд Fork: индиго → циан. Единственный «цветной герой» всех экранов
// (Fork Design Spec §1, §3.3).
// ---------------------------------------------------------------------------

val BrandIndigo = Color(0xFF2362FD)
val BrandCyan = Color(0xFF00B8D9)

/** Диагональный фирменный градиент 135°. */
fun brandGradient(start: Color = BrandIndigo, end: Color = BrandCyan): Brush =
    Brush.linearGradient(listOf(start, end))

/** Градиент для текста на тёмном фоне — светлее ради контраста. */
val BrandGradientText: Brush =
    Brush.linearGradient(listOf(Color(0xFF3D8DF7), Color(0xFF00B8D9)))

// ---------------------------------------------------------------------------
// Палитры Material 3 (Fork Design Spec §3.1–3.2)
// ---------------------------------------------------------------------------

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2362FD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E4FF),
    onPrimaryContainer = Color(0xFF082B73),
    secondary = Color(0xFF0091AC),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCF3FA),
    onSecondaryContainer = Color(0xFF003844),
    tertiary = Color(0xFF8B5CF6),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEBE2FF),
    onTertiaryContainer = Color(0xFF2E1A66),
    background = Color(0xFFF6F8FD),
    onBackground = Color(0xFF171C26),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171C26),
    surfaceVariant = Color(0xFFE8EDF7),
    onSurfaceVariant = Color(0xFF5B6575),
    surfaceContainerLow = Color(0xFFFBFCFE),
    surfaceContainer = Color(0xFFF1F4FA),
    surfaceContainerHigh = Color(0xFFEAEEF6),
    inverseSurface = Color(0xFF2C3140),
    inverseOnSurface = Color(0xFFEFF1F8),
    outline = Color(0xFFC3CCDD),
    outlineVariant = Color(0xFFE1E7F2),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color.Black,
)

/** Тёмная палитра конкретного стиля; поверхности различаются, акценты общие. */
private fun darkSchemeFor(style: SettingsStore.ThemeStyle, amoled: Boolean): ColorScheme {
    data class Surfaces(
        val background: Color,
        val surface: Color,
        val container: Color,
        val containerHigh: Color,
        val variant: Color,
        val onSurface: Color,
        val onVariant: Color,
        val outline: Color,
        val outlineVariant: Color,
    )

    val s = when (style) {
        SettingsStore.ThemeStyle.AURORA -> Surfaces(
            background = Color(0xFF0E1424), surface = Color(0xFF101729),
            container = Color(0xFF19223A), containerHigh = Color(0xFF202C49),
            variant = Color(0xFF1B2742), onSurface = Color(0xFFF1F5FF),
            onVariant = Color(0xFF8FA0BF), outline = Color(0xFF3A4763),
            outlineVariant = Color(0xFF232E4A),
        )
        SettingsStore.ThemeStyle.FROST -> Surfaces(
            background = Color(0xFF0A0F1E), surface = Color(0xFF0E1528),
            container = Color(0xFF141C33), containerHigh = Color(0xFF1A2440),
            variant = Color(0xFF18213C), onSurface = Color(0xFFF1F5FF),
            onVariant = Color(0xFF8FA0BF), outline = Color(0xFF3A4763),
            outlineVariant = Color(0xFF222D4C),
        )
        SettingsStore.ThemeStyle.NEON -> Surfaces(
            background = Color(0xFF070B14), surface = Color(0xFF0A101F),
            container = Color(0xFF0E1526), containerHigh = Color(0xFF121B30),
            variant = Color(0xFF121B30), onSurface = Color(0xFFEEF3FF),
            onVariant = Color(0xFF7C8AA5), outline = Color(0xFF2A3A5C),
            outlineVariant = Color(0xFF1D2944),
        )
    }
    // AMOLED-надстройка: чисто чёрная база (Fork Design Spec §2).
    val bg = if (amoled) Color.Black else s.background
    val surface = if (amoled) Color.Black else s.surface
    val container = if (amoled) Color(0xFF0B0F1A) else s.container
    val containerHigh = if (amoled) Color(0xFF101624) else s.containerHigh
    val variant = if (amoled) Color(0xFF0E1422) else s.variant
    val outlineVariant = if (amoled) Color(0xFF161E32) else s.outlineVariant

    return darkColorScheme(
        primary = Color(0xFF5C8DFF),
        onPrimary = Color(0xFF04173F),
        primaryContainer = Color(0xFF16409E),
        onPrimaryContainer = Color(0xFFD9E4FF),
        secondary = Color(0xFF2BCDEA),
        onSecondary = Color(0xFF00191F),
        secondaryContainer = Color(0xFF00505F),
        onSecondaryContainer = Color(0xFFCCF3FA),
        tertiary = Color(0xFFA78BFA),
        onTertiary = Color(0xFF22115C),
        error = Color(0xFFFFB4AB),
        errorContainer = Color(0xFF93000A),
        inverseSurface = Color(0xFFE8ECF6),
        scrim = Color.Black,
        background = bg,
        onBackground = s.onSurface,
        surface = surface,
        onSurface = s.onSurface,
        surfaceVariant = variant,
        onSurfaceVariant = s.onVariant,
        surfaceContainerLow = container,
        surfaceContainer = container,
        surfaceContainerHigh = containerHigh,
        outline = s.outline,
        outlineVariant = outlineVariant,
    )
}

// ---------------------------------------------------------------------------
// Типографика Manrope (Fork Design Spec §3.4)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: FontWeight) = Font(
    R.font.manrope,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Manrope = FontFamily(
    manrope(FontWeight.Normal),
    manrope(FontWeight.Medium),
    manrope(FontWeight.SemiBold),
    manrope(FontWeight.Bold),
    manrope(FontWeight.ExtraBold),
)

val ForkTypography = Typography(
    // displayBrand — «Fork» на логине
    displayLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
        fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-1.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-1.0).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
        fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp,
    ),
    // listTitle — имя в ячейке чата
    titleSmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp,
    ),
)

/** Текст сообщения в пузыре: message 16sp/500 (Fork Design Spec §3.4). */
val MessageTextStyle = TextStyle(
    fontFamily = Manrope, fontWeight = FontWeight.Medium,
    fontSize = 16.sp, lineHeight = 23.sp,
)

/** Время в пузыре: timestamp 11sp/600. */
val TimestampStyle = TextStyle(
    fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.2.sp,
)

// ---------------------------------------------------------------------------
// Fork-токены — то, чего нет в Material 3 (Fork Design Spec §3.3)
// ---------------------------------------------------------------------------

data class ForkTokens(
    val style: SettingsStore.ThemeStyle,
    val dark: Boolean,
    val brandGradient: Brush,
    val brandStart: Color,
    val brandEnd: Color,
    /** Галочки «прочитано», статус «в сети», акцентные мелочи. */
    val checkCyan: Color,
    // Пузыри сообщений
    val bubbleIn: Color,
    val bubbleInBorder: Color,
    val bubbleTextIn: Color,
    val bubbleRadius: Dp,
    val bubbleRadiusSmall: Dp,
    // Стеклянные капсулы (дата, кнопка «вниз», оверлеи медиа)
    val glassPill: Color,
    val glassPanel: Color,
    val glassBorder: Color,
    // Бейдж непрочитанных
    val unreadBadge: Brush,
    val unreadBadgeText: Color,
    /** Ступени градиента прослушанной части waveform. */
    val waveformSteps: List<Color>,
)

val LocalForkTokens = compositionLocalOf {
    forkTokens(SettingsStore.ThemeStyle.AURORA, dark = true, scheme = null)
}

/** Токены текущей темы — короткий доступ из любого composable. */
val forkTokens: ForkTokens
    @Composable get() = LocalForkTokens.current

fun forkTokens(
    style: SettingsStore.ThemeStyle,
    dark: Boolean,
    scheme: ColorScheme?,
): ForkTokens {
    // При Material You градиент строится из dynamic primary → tertiary,
    // чтобы исходящие пузыри оставались градиентными (Fork Design Spec §2).
    val start = scheme?.primary ?: BrandIndigo
    val end = scheme?.tertiary ?: BrandCyan
    val gradient = if (scheme != null) brandGradient(start, end) else brandGradient()

    val neon = style == SettingsStore.ThemeStyle.NEON
    return ForkTokens(
        style = style,
        dark = dark,
        brandGradient = gradient,
        brandStart = if (scheme != null) start else BrandIndigo,
        brandEnd = if (scheme != null) end else BrandCyan,
        checkCyan = if (dark) Color(0xFF2BCDEA) else Color(0xFF0091AC),
        bubbleIn = when {
            !dark -> Color.White
            style == SettingsStore.ThemeStyle.AURORA -> Color(0xFF1B2742)
            style == SettingsStore.ThemeStyle.FROST -> Color(0xFF222C48) // стекло без blur: плотный tint
            else -> Color(0xFF121B30)
        },
        bubbleInBorder = when {
            !dark -> Color(0xFFE1E7F2)
            style == SettingsStore.ThemeStyle.FROST -> Color(0x12FFFFFF)
            neon -> Color(0xFF1A2540)
            else -> Color.Transparent
        },
        bubbleTextIn = when {
            !dark -> Color(0xFF171C26)
            neon -> Color(0xFFE6EDFA)
            else -> Color(0xFFEAF0FB)
        },
        bubbleRadius = if (neon) 22.dp else 20.dp,
        bubbleRadiusSmall = if (neon) 7.dp else 6.dp,
        glassPill = if (dark) Color(0xC719223A) else Color(0xE6FFFFFF),
        glassPanel = if (dark) Color(0x8C161E38) else Color(0xA6FFFFFF),
        glassBorder = if (dark) Color(0x1AFFFFFF) else Color(0x0F000000),
        unreadBadge = if (neon) Brush.linearGradient(listOf(BrandCyan, BrandCyan)) else gradient,
        unreadBadgeText = if (neon) Color(0xFF04121C) else Color.White,
        waveformSteps = listOf(
            Color(0xFF2F6BFD), Color(0xFF1A82EE), Color(0xFF0C9FE0), Color(0xFF00B8D9),
        ),
    )
}

// ---------------------------------------------------------------------------
// Тема
// ---------------------------------------------------------------------------

/** Кроссфейд ключевых ролей при смене стиля/режима: tween 300ms (Fork Design Spec §2). */
@Composable
private fun ColorScheme.animated(): ColorScheme {
    @Composable
    fun c(target: Color): Color {
        val v by animateColorAsState(target, tween(300), label = "scheme")
        return v
    }
    return copy(
        primary = c(primary),
        primaryContainer = c(primaryContainer),
        secondary = c(secondary),
        background = c(background),
        onBackground = c(onBackground),
        surface = c(surface),
        onSurface = c(onSurface),
        surfaceVariant = c(surfaceVariant),
        onSurfaceVariant = c(onSurfaceVariant),
        surfaceContainerLow = c(surfaceContainerLow),
        surfaceContainer = c(surfaceContainer),
        surfaceContainerHigh = c(surfaceContainerHigh),
        outline = c(outline),
        outlineVariant = c(outlineVariant),
    )
}

/**
 * Тема приложения: Стиль (Aurora / Frost / Neon Ink) × Режим (light / dark / system)
 * + AMOLED + Material You. Управляется SettingsStore.
 */
@Composable
fun ForkTheme(content: @Composable () -> Unit) {
    val themeMode by SettingsStore.theme.collectAsStateWithLifecycle()
    val style by SettingsStore.style.collectAsStateWithLifecycle()
    val amoled by SettingsStore.amoled.collectAsStateWithLifecycle()
    val dynamicColors by SettingsStore.dynamicColors.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val dark = when (themeMode) {
        SettingsStore.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        SettingsStore.ThemeMode.LIGHT -> false
        SettingsStore.ThemeMode.DARK -> true
    }

    val canDynamic = dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        canDynamic && dark -> dynamicDarkColorScheme(context)
        canDynamic -> dynamicLightColorScheme(context)
        dark -> darkSchemeFor(style, amoled)
        else -> LightScheme
    }.animated()

    val tokens = forkTokens(style, dark, scheme = if (canDynamic) scheme else null)

    // Иконки статусбара: светлые на тёмных темах, тёмные на светлой.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalForkTokens provides tokens) {
        MaterialTheme(colorScheme = scheme, typography = ForkTypography, content = content)
    }
}

// ---------------------------------------------------------------------------
// 7 градиентных пар аватаров (Fork Design Spec §3.3), 135°, инициалы белые
// ---------------------------------------------------------------------------

private val AvatarGradients = listOf(
    listOf(Color(0xFF2362FD), Color(0xFF00B8D9)), // indigo
    listOf(Color(0xFF8B5CF6), Color(0xFF4C7DFF)), // violet
    listOf(Color(0xFFFF6F91), Color(0xFFFF9F6E)), // rose
    listOf(Color(0xFF00C885), Color(0xFF00B8D9)), // teal
    listOf(Color(0xFFFFB547), Color(0xFFFF7A3D)), // amber
    listOf(Color(0xFF4C7DFF), Color(0xFF22D3EE)), // sky
    listOf(Color(0xFFF45B9A), Color(0xFF8B5CF6)), // magenta
)

fun avatarBrush(seed: Long): Brush {
    val colors = AvatarGradients[(kotlin.math.abs(seed) % AvatarGradients.size).toInt()]
    return Brush.linearGradient(colors, start = Offset.Zero, end = Offset.Infinite)
}

fun senderColor(seed: Long): Color =
    AvatarGradients[(kotlin.math.abs(seed) % AvatarGradients.size).toInt()][1]
