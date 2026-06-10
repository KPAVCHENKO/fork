package app.fork.messenger

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fork.messenger.SettingsStore
import app.fork.messenger.update.UpdateState
import app.fork.messenger.update.UpdateManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val myName by TdClient.myName.collectAsStateWithLifecycle()
    val updateState by UpdateManager.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(app.fork.messenger.ui.ForkIcons.ArrowBack, contentDescription = "назад")
                    }
                },
                title = { Text("Настройки", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(myName ?: "Аккаунт", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Версия ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )

            Spacer(Modifier.height(28.dp))
            Text("Оформление", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            AppearanceSection()

            Spacer(Modifier.height(28.dp))
            Text("Уведомления и поведение", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            BehaviorSection(context)

            Spacer(Modifier.height(28.dp))
            Text("Обновления", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            UpdateSection(updateState, context)

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AppearanceSection() {
    val theme by SettingsStore.theme.collectAsStateWithLifecycle()
    val dynamic by SettingsStore.dynamicColors.collectAsStateWithLifecycle()

    Text("Тема", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val modes = listOf(
            SettingsStore.ThemeMode.SYSTEM to "Системная",
            SettingsStore.ThemeMode.LIGHT to "Светлая",
            SettingsStore.ThemeMode.DARK to "Тёмная",
        )
        modes.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = theme == mode,
                onClick = { SettingsStore.setTheme(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
            ) { Text(label) }
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            title = "Цвета системы (Material You)",
            subtitle = "Подстраивать палитру под обои телефона",
            checked = dynamic,
            onCheckedChange = { SettingsStore.setDynamicColors(it) },
        )
    }
}

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
    Spacer(Modifier.height(4.dp))
    ToggleRow(
        title = "Enter отправляет сообщение",
        subtitle = "Иначе Enter — перенос строки",
        checked = enterToSend,
        onCheckedChange = { SettingsStore.setEnterToSend(it) },
    )
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Отключить экономию батареи для Fork")
    }
    Text(
        "Чтобы сообщения приходили мгновенно даже в фоне",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun UpdateSection(state: UpdateState, context: android.content.Context) {
    when (state) {
        is UpdateState.Available -> {
            Text(
                "Доступна версия ${state.release.version}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
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
            Button(
                onClick = { UpdateManager.downloadAndInstall(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(app.fork.messenger.ui.ForkIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Скачать и установить")
            }
        }

        is UpdateState.Downloading -> {
            Text("Загрузка… ${state.percent}%", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
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
            Text("У вас последняя версия", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            CheckButton(context)
        }

        is UpdateState.Failed -> {
            Text(state.message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            CheckButton(context)
        }

        UpdateState.Idle -> CheckButton(context)
    }
}

@Composable
private fun CheckButton(context: android.content.Context) {
    OutlinedButton(
        onClick = { UpdateManager.checkExplicit(context) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Проверить обновления")
    }
}

@Composable
private fun RowStatus(text: String, spinning: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (spinning) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(10.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
