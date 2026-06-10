package app.fork.messenger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape



import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fork.messenger.ui.avatarBrush
import coil.compose.AsyncImage
import java.io.File

/** Главный экран — список чатов. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(onChatClick: (Long) -> Unit) {
    val chats by ChatStore.chatList.collectAsStateWithLifecycle()
    val loading by ChatStore.loading.collectAsStateWithLifecycle()
    val connection by TdClient.connectionState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (connection == "подключено") "Fork" else connection.replaceFirstChar { it.uppercase() },
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (chats.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (loading) {
                    CircularProgressIndicator()
                } else {
                    Text("Чатов пока нет", color = MaterialTheme.colorScheme.outline)
                }
            }
            return@Scaffold
        }

        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(chats, key = { it.id }) { chat ->
                ChatRow(chat = chat, onClick = { onChatClick(chat.id) })
            }
        }
    }
}

@Composable
private fun ChatRow(chat: UiChat, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(chat)
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (chat.isMuted) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        app.fork.messenger.ui.ForkIcons.VolumeOff,
                        contentDescription = "без звука",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = chat.time,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            Spacer(Modifier.padding(top = 2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chat.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                when {
                    chat.unread > 0 -> UnreadBadge(chat.unread, chat.isMuted)
                    chat.isPinned -> Icon(
                        app.fork.messenger.ui.ForkIcons.PushPin,
                        contentDescription = "закреплён",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun Avatar(chat: UiChat) {
    if (chat.avatarPath != null) {
        AsyncImage(
            model = File(chat.avatarPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(54.dp).clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(avatarBrush(chat.colorSeed)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = chat.initials,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.White,
            )
        }
    }
}

@Composable
private fun UnreadBadge(count: Int, muted: Boolean) {
    val bg = if (muted) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary
    val fg = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}
