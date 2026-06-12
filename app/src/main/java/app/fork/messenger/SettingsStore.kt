package app.fork.messenger

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Пользовательские настройки приложения, сохраняются на устройстве. */
object SettingsStore {
    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    /** Встроенные стили Fork: Aurora / Frost / Neon Ink (Fork Design Spec §2). */
    enum class ThemeStyle { AURORA, FROST, NEON }

    private const val PREFS = "fork_settings"
    private const val KEY_THEME = "theme"
    private const val KEY_STYLE = "theme_style"
    private const val KEY_AMOLED = "amoled"
    private const val KEY_DYNAMIC = "dynamic_colors"
    private const val KEY_NOTIFICATIONS = "notifications"
    private const val KEY_ENTER_SEND = "enter_send"

    private lateinit var prefs: android.content.SharedPreferences

    private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _style = MutableStateFlow(ThemeStyle.AURORA)
    val style: StateFlow<ThemeStyle> = _style.asStateFlow()

    private val _amoled = MutableStateFlow(false)
    val amoled: StateFlow<Boolean> = _amoled.asStateFlow()

    private val _dynamicColors = MutableStateFlow(false)
    val dynamicColors: StateFlow<Boolean> = _dynamicColors.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _enterToSend = MutableStateFlow(false)
    val enterToSend: StateFlow<Boolean> = _enterToSend.asStateFlow()

    /** Масштаб шрифта приложения (0.85..1.4). Умножается на системный в ForkTheme. */
    private val _fontScale = MutableStateFlow(1f)
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    /** Эмодзи быстрой реакции (двойной тап по сообщению). */
    private val _quickReaction = MutableStateFlow("❤️")
    val quickReaction: StateFlow<String> = _quickReaction.asStateFlow()

    /** Недавно использованные эмодзи (свежие — первыми, как в TG). */
    private val _recentEmoji = MutableStateFlow<List<String>>(emptyList())
    val recentEmoji: StateFlow<List<String>> = _recentEmoji.asStateFlow()

    // ---------- Обои чата (Fork Design Spec §3.8) ----------

    /** Обои по умолчанию для всех чатов. */
    private val _defaultWallpaper = MutableStateFlow("glow")
    val defaultWallpaper: StateFlow<String> = _defaultWallpaper.asStateFlow()

    /** Затемнение узора 0..0.6. */
    private val _wallpaperDim = MutableStateFlow(0f)
    val wallpaperDim: StateFlow<Float> = _wallpaperDim.asStateFlow()

    /** Версия per-chat карты обоев — для перерисовки после смены. */
    private val _wallpaperRevision = MutableStateFlow(0)
    val wallpaperRevision: StateFlow<Int> = _wallpaperRevision.asStateFlow()

    /** Живой предпросмотр за шторкой выбора: id обоев + затемнение. Не сохраняется. */
    private val _wallpaperPreview = MutableStateFlow<Pair<String, Float>?>(null)
    val wallpaperPreview: StateFlow<Pair<String, Float>?> = _wallpaperPreview.asStateFlow()

    fun setWallpaperPreview(id: String?, dim: Float = 0f) {
        _wallpaperPreview.value = id?.let { it to dim.coerceIn(0f, 0.6f) }
    }

    /** Обои конкретного чата (или дефолтные). */
    fun wallpaperFor(chatId: Long): String =
        prefs.getString("wallpaper_$chatId", null) ?: _defaultWallpaper.value

    fun setChatWallpaper(chatId: Long, id: String) {
        prefs.edit().putString("wallpaper_$chatId", id).apply()
        _wallpaperRevision.value++
    }

    fun setDefaultWallpaper(id: String) {
        _defaultWallpaper.value = id
        prefs.edit().putString("wallpaper_default", id).apply()
        _wallpaperRevision.value++
    }

    fun setWallpaperDim(dim: Float) {
        _wallpaperDim.value = dim.coerceIn(0f, 0.6f)
        prefs.edit().putFloat("wallpaper_dim", _wallpaperDim.value).apply()
    }

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _theme.value = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)
        _style.value = runCatching { ThemeStyle.valueOf(prefs.getString(KEY_STYLE, "AURORA")!!) }.getOrDefault(ThemeStyle.AURORA)
        _amoled.value = prefs.getBoolean(KEY_AMOLED, false)
        _dynamicColors.value = prefs.getBoolean(KEY_DYNAMIC, false)
        _notificationsEnabled.value = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        _enterToSend.value = prefs.getBoolean(KEY_ENTER_SEND, false)
        _fontScale.value = prefs.getFloat("font_scale", 1f).coerceIn(0.85f, 1.4f)
        _quickReaction.value = prefs.getString("quick_reaction", "❤️") ?: "❤️"
        _recentEmoji.value = prefs.getString("recent_emoji", "")
            ?.split("")?.filter { it.isNotBlank() } ?: emptyList()
        _defaultWallpaper.value = prefs.getString("wallpaper_default", "glow") ?: "glow"
        _wallpaperDim.value = prefs.getFloat("wallpaper_dim", 0f)
    }

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setStyle(style: ThemeStyle) {
        _style.value = style
        prefs.edit().putString(KEY_STYLE, style.name).apply()
    }

    fun setAmoled(enabled: Boolean) {
        _amoled.value = enabled
        prefs.edit().putBoolean(KEY_AMOLED, enabled).apply()
    }

    fun setDynamicColors(enabled: Boolean) {
        _dynamicColors.value = enabled
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
    }

    fun setEnterToSend(enabled: Boolean) {
        _enterToSend.value = enabled
        prefs.edit().putBoolean(KEY_ENTER_SEND, enabled).apply()
    }

    fun setFontScale(scale: Float) {
        _fontScale.value = scale.coerceIn(0.85f, 1.4f)
        prefs.edit().putFloat("font_scale", _fontScale.value).apply()
    }

    fun setQuickReaction(emoji: String) {
        _quickReaction.value = emoji
        prefs.edit().putString("quick_reaction", emoji).apply()
    }

    fun addRecentEmoji(emoji: String) {
        if (emoji.isBlank()) return
        val list = (listOf(emoji) + _recentEmoji.value.filter { it != emoji }).take(24)
        _recentEmoji.value = list
        prefs.edit().putString("recent_emoji", list.joinToString("")).apply()
    }
}
