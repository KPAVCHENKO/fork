package app.fork.messenger

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fork.messenger.ui.ForkAvatar
import app.fork.messenger.ui.ForkIcons
import app.fork.messenger.ui.GradientButton
import app.fork.messenger.ui.forkTokens
import app.fork.messenger.update.UpdateManager
import app.fork.messenger.update.UpdateState

/** Настройки (Fork Design Spec §4.5): карточные группы, переключатель трёх стилей. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, bottomInset: androidx.compose.ui.unit.Dp = 0.dp) {
    val context = LocalContext.current
    val myName by TdClient.myName.collectAsStateWithLifecycle()
    val updateState by UpdateManager.state.collectAsStateWithLifecycle()
    var showSessions by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showWallpapers by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showProxy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    if (showSessions) {
        SessionsScreen(onBack = { showSessions = false })
        return
    }
    if (showProxy) {
        ProxyScreen(onBack = { showProxy = false })
        return
    }
    if (showWallpapers) {
        WallpaperSheet(chatId = null, onDismiss = { showWallpapers = false })
    }
    BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        ForkIcons.ArrowBack,
                        contentDescription = "назад",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            title = { Text("Настройки", style = MaterialTheme.typography.titleLarge) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Карточка профиля
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val name = myName ?: "Аккаунт"
                    ForkAvatar(
                        size = 72.dp,
                        avatarPath = null,
                        initials = MessageFormat.initials(name),
                        seed = name.hashCode().toLong(),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(name, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Fork ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionLabel("Оформление")
            SettingsCard {
                FontSizeSetting()
                Spacer(Modifier.height(8.dp))
                AppearanceSection()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showWallpapers = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Фон чатов", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Обои по умолчанию для всех чатов",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionLabel("Уведомления и поведение")
            SettingsCard { BehaviorSection(context) }

            SectionLabel("Безопасность")
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSessions = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Активные сессии", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Устройства, где выполнен вход в аккаунт",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionLabel("Соединение")
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showProxy = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Прокси", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Список прокси, статус и переключение",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionLabel("Обновления")
            SettingsCard { UpdateSection(updateState, context) }

            Spacer(Modifier.height(24.dp + bottomInset))
        }
    }
}

/** Масштаб шрифта приложения — слайдер, мгновенно применяется ко всему тексту. */
@Composable
private fun FontSizeSetting() {
    val scale by SettingsStore.fontScale.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Размер шрифта", style = MaterialTheme.typography.bodyLarge)
            Text(
                "${(scale * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    androidx.compose.material3.Slider(
        value = scale,
        onValueChange = { SettingsStore.setFontScale((it * 20).toInt() / 20f) },
        valueRange = 0.85f..1.40f,
        steps = 10,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("А", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Пример текста этого размера", style = MaterialTheme.typography.bodyMedium)
        Text("А", style = MaterialTheme.typography.titleLarge)
    }
}

/** Заголовок группы настроек. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** Карточка-группа: surfaceContainer, радиус 22 (Fork Design Spec §4.5). */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        content = content,
    )
}

// ---------------------------------------------------------------------------
// Оформление: стиль × режим × AMOLED × Material You
// ---------------------------------------------------------------------------

@Composable
private fun AppearanceSection() {
    val theme by SettingsStore.theme.collectAsStateWithLifecycle()
    val style by SettingsStore.style.collectAsStateWithLifecycle()
    val amoled by SettingsStore.amoled.collectAsStateWithLifecycle()
    val dynamic by SettingsStore.dynamicColors.collectAsStateWithLifecycle()

    // Три встроенных стиля — превью-карточки.
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemeCard(
            name = "Aurora",
            style = SettingsStore.ThemeStyle.AURORA,
            selected = style == SettingsStore.ThemeStyle.AURORA,
            background = Color(0xFF0E1424),
            bubbleIn = Color(0xFF1B2742),
            modifier = Modifier.weight(1f),
        )
        ThemeCard(
            name = "Frost",
            style = SettingsStore.ThemeStyle.FROST,
            selected = style == SettingsStore.ThemeStyle.FROST,
            background = Color(0xFF0A0F1E),
            bubbleIn = Color(0xFF222C48),
            modifier = Modifier.weight(1f),
        )
        ThemeCard(
            name = "Neon Ink",
            style = SettingsStore.ThemeStyle.NEON,
            selected = style == SettingsStore.ThemeStyle.NEON,
            background = Color(0xFF070B14),
            bubbleIn = Color(0xFF121B30),
            modifier = Modifier.weight(1f),
        )
    }

    Spacer(Modifier.height(16.dp))

    // Режим: Светлая / Тёмная / Системная — сегмент-контрол с градиентной заливкой.
    ModeSegments(theme)

    val dark = when (theme) {
        SettingsStore.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        SettingsStore.ThemeMode.LIGHT -> false
        SettingsStore.ThemeMode.DARK -> true
    }

    Spacer(Modifier.height(8.dp))
    ToggleRow(
        title = "Чисто чёрный (AMOLED)",
        subtitle = "Экономит заряд на OLED-экранах",
        checked = amoled && dark,
        enabled = dark,
        onCheckedChange = { SettingsStore.setAmoled(it) },
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ToggleRow(
            title = "Material You",
            subtitle = "Цвета из обоев · Android 12+",
            checked = dynamic,
            onCheckedChange = { SettingsStore.setDynamicColors(it) },
        )
    }
}

/** Превью-карточка стиля: мини-скрин чата (Fork Design Spec §4.5). */
@Composable
private fun ThemeCard(
    name: String,
    style: SettingsStore.ThemeStyle,
    selected: Boolean,
    background: Color,
    bubbleIn: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = forkTokens
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(148.dp)
            .clip(shape)
            .background(background)
            .then(
                if (selected) Modifier.border(2.dp, tokens.brandGradient, shape)
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), shape),
            )
            .clickable { SettingsStore.setStyle(style) },
    ) {
        Column(Modifier.padding(10.dp)) {
            // Входящий пузырь
            Box(
                Modifier
                    .width(52.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 8.dp))
                    .background(bubbleIn),
            )
            Spacer(Modifier.height(6.dp))
            // Исходящий — фирменный градиент
            Box(
                Modifier
                    .width(64.dp)
                    .height(18.dp)
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 3.dp))
                    .background(tokens.brandGradient),
            )
            Spacer(Modifier.height(6.dp))
            // Капсула-дата
            Box(
                Modifier
                    .width(36.dp)
                    .height(10.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White.copy(alpha = 0.12f)),
            )
            Spacer(Modifier.weight(1f))
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFF1F5FF),
            )
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(tokens.checkCyan),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    ForkIcons.Check,
                    contentDescription = null,
                    tint = Color(0xFF04121C),
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

/** Сегмент-контрол режима: активный сегмент залит фирменным градиентом. */
@Composable
private fun ModeSegments(current: SettingsStore.ThemeMode) {
    val tokens = forkTokens
    val modes = listOf(
        SettingsStore.ThemeMode.LIGHT to "Светлая",
        SettingsStore.ThemeMode.DARK to "Тёмная",
        SettingsStore.ThemeMode.SYSTEM to "Системная",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp),
    ) {
        modes.forEach { (mode, label) ->
            val active = current == mode
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .then(if (active) Modifier.background(tokens.brandGradient) else Modifier)
                    .clickable { SettingsStore.setTheme(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Поведение и обновления
// ---------------------------------------------------------------------------

@Composable
private fun BehaviorSection(context: android.content.Context) {
    val notifications by SettingsStore.notificationsEnabled.collectAsStateWithLifecycle()
    val enterToSend by SettingsStore.enterToSend.collectAsStateWithLifecycle()

    ToggleRow(
        title = "Уведомления о сообщениях",
        subtitle = "Показывать всплывающие уведомления",
        checked = notifications,
        onCheckedChange = { SettingsStore.setNotificationsEnabled(it) },
    )
    ToggleRow(
        title = "Enter отправляет сообщение",
        subtitle = "Иначе Enter — перенос строки",
        checked = enterToSend,
        onCheckedChange = { SettingsStore.setEnterToSend(it) },
    )
    Spacer(Modifier.height(12.dp))
    QuickReactionSetting()
    Spacer(Modifier.height(12.dp))
    GradientButton(
        text = "Отключить экономию батареи",
        onClick = {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                )
            }
        },
    )
    Text(
        "Чтобы сообщения приходили мгновенно даже в фоне",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Выбор эмодзи быстрой реакции (двойной тап по сообщению). */
@Composable
private fun QuickReactionSetting() {
    val current by SettingsStore.quickReaction.collectAsStateWithLifecycle()
    val options = listOf("❤️", "👍", "🔥", "😁", "😢", "🎉", "👏", "🙏")
    Column(Modifier.fillMaxWidth()) {
        Text("Быстрая реакция", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Эмодзи при двойном тапе по сообщению",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { e ->
                val sel = e == current
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                        .clickable { SettingsStore.setQuickReaction(e) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(e, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun UpdateSection(state: UpdateState, context: android.content.Context) {
    when (state) {
        is UpdateState.Available -> {
            Text(
                "Доступна версия ${state.release.version}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (state.release.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    state.release.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            GradientButton(
                text = "Скачать и установить",
                onClick = { UpdateManager.downloadAndInstall(context) },
            )
        }

        is UpdateState.Downloading -> {
            Text("Загрузка… ${state.percent}%", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        UpdateState.Installing -> RowStatus("Установка…", spinning = true)

        UpdateState.NeedPermission -> Text(
            "Разреши установку приложений из этого источника в открывшихся настройках, " +
                "затем нажми «Скачать и установить» ещё раз.",
            style = MaterialTheme.typography.bodyMedium,
        )

        UpdateState.Checking -> RowStatus("Проверка…", spinning = true)

        UpdateState.UpToDate -> {
            Text(
                "У вас последняя версия",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            CheckButton(context)
        }

        is UpdateState.Failed -> {
            Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            CheckButton(context)
        }

        UpdateState.Idle -> CheckButton(context)
    }
}

@Composable
private fun CheckButton(context: android.content.Context) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(percent = 50))
            .clickable { UpdateManager.checkExplicit(context) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Проверить обновления",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun RowStatus(text: String, spinning: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (spinning) {
            CircularProgressIndicator(
                Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(10.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
