package app.fork.messenger

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fork.messenger.net.NetworkMonitor
import app.fork.messenger.net.ProxyPool
import app.fork.messenger.ui.ForkIcons
import app.fork.messenger.ui.forkTokens

/** Settings → Прокси: pool with live status, manual switch, check-all, add by link. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val tokens = forkTokens
    val entries by ProxyPool.entries.collectAsStateWithLifecycle()
    val activeKey by ProxyPool.activeKey.collectAsStateWithLifecycle()
    val checking by ProxyPool.checking.collectAsStateWithLifecycle()
    val connection by TdClient.connectionState.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { ProxyPool.refreshEntries(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(ForkIcons.ArrowBack, contentDescription = "назад", tint = MaterialTheme.colorScheme.onSurface)
                }
            },
            title = { Text("Прокси", style = MaterialTheme.typography.titleLarge) },
            actions = {
                IconButton(onClick = { showAdd = true }) {
                    Icon(ForkIcons.Edit, contentDescription = "добавить", tint = MaterialTheme.colorScheme.onSurface)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        // Status line: connection + VPN/direct mode.
        val vpn = NetworkMonitor.isVpnActive()
        val direct = activeKey == null
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (connection == "подключено") "Соединение: подключено" else "Соединение: $connection",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (connection == "подключено") tokens.checkCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (vpn) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "VPN",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(tokens.brandGradient)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            if (direct) {
                Text(
                    if (vpn) "Напрямую через VPN (без прокси — меньше задержка)" else "Напрямую, без прокси",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Actions.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PillButton(
                text = if (checking) "Проверяю…" else "Проверить все",
                onClick = { ProxyPool.checkAll(context) },
                modifier = Modifier.weight(1f),
            )
            PillButton(
                text = "Обновить из канала",
                onClick = { ProxyPool.refreshFromChannel(context) },
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(entries, key = { it.key }) { entry ->
                ProxyRow(
                    entry = entry,
                    active = entry.key == activeKey,
                    onClick = { ProxyPool.selectManual(context, entry) },
                    onRemove = if (entry.source != ProxyPool.Source.PRIMARY) {
                        { ProxyPool.removeEntry(context, entry) }
                    } else null,
                )
            }
        }
    }

    if (showAdd) {
        AddProxyDialog(onDismiss = { showAdd = false }, onAdd = { link ->
            ProxyPool.addManual(context, link)
            showAdd = false
        })
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ProxyRow(
    entry: ProxyPool.ProxyEntry,
    active: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val tokens = forkTokens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Active indicator dot.
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (active) tokens.checkCyan else MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.host,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(
                        when (entry.source) {
                            ProxyPool.Source.PRIMARY -> "Основной"
                            ProxyPool.Source.CHANNEL -> "Из канала"
                            ProxyPool.Source.MANUAL -> "Добавлен"
                        },
                    )
                    append(" · :${entry.port}")
                    if (active) append(" · активен")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Status chip.
        StatusChip(entry)
        if (onRemove != null) {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                Icon(
                    ForkIcons.Trash,
                    contentDescription = "удалить",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusChip(entry: ProxyPool.ProxyEntry) {
    val tokens = forkTokens
    val (text, color) = when (entry.lastOk) {
        true -> (if (entry.latencyMs >= 0) "${entry.latencyMs} мс" else "ок") to tokens.checkCyan
        false -> "нет" to MaterialTheme.colorScheme.error
        null -> "?" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun AddProxyDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var link by remember { mutableStateOf("") }
    val tokens = forkTokens
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить прокси") },
        text = {
            Column {
                Text(
                    "Вставьте ссылку tg://proxy?server=…&port=…&secret=…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(12.dp),
                ) {
                    if (link.isEmpty()) {
                        Text(
                            "tg://proxy?server=…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    BasicTextField(
                        value = link,
                        onValueChange = { link = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (link.isNotBlank()) onAdd(link) }) {
                Text("Добавить", color = tokens.checkCyan)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
