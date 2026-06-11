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

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _theme.value = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)
        _style.value = runCatching { ThemeStyle.valueOf(prefs.getString(KEY_STYLE, "AURORA")!!) }.getOrDefault(ThemeStyle.AURORA)
        _amoled.value = prefs.getBoolean(KEY_AMOLED, false)
        _dynamicColors.value = prefs.getBoolean(KEY_DYNAMIC, false)
        _notificationsEnabled.value = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        _enterToSend.value = prefs.getBoolean(KEY_ENTER_SEND, false)
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
}
