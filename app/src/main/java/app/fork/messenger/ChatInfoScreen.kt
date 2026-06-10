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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fork.messenger.ui.avatarBrush
import coil.compose.AsyncImage
import java.io.File
import org.drinkless.tdlib.TdApi

/** Профиль чата или пользователя: аватар, имя, @имя, телефон, статус, без звука. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoScreen(chatId: Long, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val revision by ChatStore.revision.collectAsStateWithLifecycle()
    val chat = remember(chatId, revision) { ChatStore.chat(chatId) }
    val clipboard = LocalClipboardManager.current

    if (chat == null) {
        onBack()
        return
    }

    val userId = (chat.type as? TdApi.ChatTypePrivate)?.userId ?: 0L
    val user = if (userId != 0L) UserCache.user(userId) else null
    val username = user?.usernames?.activeUsernames?.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(app.fork.messenger.ui.ForkIcons.ArrowBack, contentDescription = "назад")
                    }
                },
                title = { Text("Информация", fontWeight = FontWeight.SemiBold) },
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            // Большой аватар
            val avatarPath = chat.photo?.small?.local?.takeIf { it.isDownloadingCompleted }?.path
            if (avatarPath != null) {
                AsyncImage(
                    model = File(avatarPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(110.dp).clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(avatarBrush(chat.id)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        MessageFormat.initials(chat.title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(chat.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    userId != 0L -> UserCache.statusText(userId)
                    chat.type.let { it is TdApi.ChatTypeSupergroup && it.isChannel } -> "канал"
                    else -> "группа"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()

            if (username != null) {
                InfoRow(label = "Имя пользователя", value = "@$username") {
                    clipboard.setText(AnnotatedString("@$username"))
                }
            }
            if (user != null && user.phoneNumber.isNotBlank()) {
                InfoRow(label = "Телефон", value = "+${user.phoneNumber}") {
                    clipboard.setText(AnnotatedString("+${user.phoneNumber}"))
                }
            }

            MuteRow(chat)

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(value, style = MaterialTheme.typography.bodyLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Переключатель «Без звука» — меняет настройки уведомлений чата в Telegram. */
@Composable
private fun MuteRow(chat: TdApi.Chat) {
    var mutedState by remember(chat.id) {
        mutableIntStateOf(chat.notificationSettings?.takeIf { !it.useDefaultMuteFor }?.muteFor ?: 0)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Без звука", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Не показывать уведомления из этого чата",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = mutedState > 0,
            onCheckedChange = { mute ->
                val newMuteFor = if (mute) 500_000_000 else 0
                mutedState = newMuteFor
                val settings = (chat.notificationSettings ?: TdApi.ChatNotificationSettings()).also {
                    it.useDefaultMuteFor = false
                    it.muteFor = newMuteFor
                }
                TdClient.send(TdApi.SetChatNotificationSettings(chat.id, settings))
            },
        )
    }
}
