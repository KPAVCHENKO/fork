package app.fork.messenger

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import app.fork.messenger.ui.ForkIcons
import app.fork.messenger.ui.forkTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.drinkless.tdlib.TdApi

/** Активные сессии аккаунта: текущая, остальные, завершение по одной и всех разом. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var sessions by remember { mutableStateOf<List<TdApi.Session>?>(null) }

    fun reload() {
        TdClient.send(TdApi.GetActiveSessions()) { result ->
            if (result is TdApi.Sessions) {
                sessions = result.sessions.orEmpty().filterNotNull()
                    .sortedByDescending { if (it.isCurrent) Int.MAX_VALUE else it.lastActiveDate }
            }
        }
    }
    LaunchedEffect(Unit) { reload() }

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
            title = { Text("Активные сессии", style = MaterialTheme.typography.titleLarge) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        val list = sessions
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.id }) { session ->
                SessionRow(
                    session = session,
                    onTerminate = {
                        TdClient.send(TdApi.TerminateSession(session.id)) { reload() }
                    },
                )
            }
            if (list.any { !it.isCurrent }) {
                item(key = "terminate_all") {
                    Text(
                        "Завершить все другие сессии",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                TdClient.send(TdApi.TerminateAllOtherSessions()) { reload() }
                            }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: TdApi.Session, onTerminate: () -> Unit) {
    val tokens = forkTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .then(
                    if (session.isCurrent) Modifier.background(tokens.brandGradient)
                    else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ForkIcons.Settings,
                contentDescription = null,
                tint = if (session.isCurrent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${session.applicationName} ${session.applicationVersion}".trim()
                    .ifBlank { "Приложение" },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                listOf(session.deviceModel, session.platform, session.systemVersion)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (session.isCurrent) {
                    "Текущая сессия"
                } else {
                    val date = SimpleDateFormat("d MMM HH:mm", Locale("ru"))
                        .format(Date(session.lastActiveDate * 1000L))
                    listOf(session.location, date).filter { it.isNotBlank() }.joinToString(" · ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (session.isCurrent) tokens.checkCyan
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!session.isCurrent) {
            Text(
                "Завершить",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClick = onTerminate)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}
