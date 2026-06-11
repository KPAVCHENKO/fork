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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fork.messenger.ui.ForkAvatar
import app.fork.messenger.ui.ForkEmptyState
import app.fork.messenger.ui.ForkIcons

/** Общий каркас экрана выбора со списком (Новый чат / Переслать). */
@Composable
private fun PickerScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(ForkIcons.ArrowBack, contentDescription = "назад", tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(4.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        content()
    }
}

/** Строка человека/чата с аватаром (в дизайн-языке Fork). */
@Composable
private fun PickerRow(
    title: String,
    subtitle: String?,
    avatarPath: String?,
    initials: String,
    seed: Long,
    online: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ForkAvatar(size = 50.dp, avatarPath = avatarPath, initials = initials, seed = seed, online = online)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Новый чат: список контактов, тап открывает личный чат. */
@Composable
fun NewChatScreen(onBack: () -> Unit, onOpenChat: (Long) -> Unit) {
    LaunchedEffect(Unit) { ContactsStore.load() }
    val contacts by ContactsStore.contacts.collectAsStateWithLifecycle()

    PickerScaffold(title = "Новый чат", onBack = onBack) {
        if (contacts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ForkEmptyState(title = "Нет контактов", subtitle = "Здесь появятся ваши контакты из Telegram")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(contacts, key = { it.userId }) { c ->
                    PickerRow(
                        title = c.name,
                        subtitle = c.username?.let { "@$it" },
                        avatarPath = null,
                        initials = c.initials,
                        seed = c.userId,
                        online = c.isOnline,
                        onClick = { ContactsStore.openChat(c.userId) { onOpenChat(it) } },
                    )
                }
            }
        }
    }
}

/** Выбор чата для пересылки: список существующих чатов с поиском по названию. */
@Composable
fun ForwardPickerScreen(onBack: () -> Unit, onPick: (Long) -> Unit) {
    val chats by ChatStore.chatList.collectAsStateWithLifecycle()

    PickerScaffold(title = "Переслать в…", onBack = onBack) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(chats, key = { it.id }) { chat ->
                PickerRow(
                    title = chat.title,
                    subtitle = null,
                    avatarPath = chat.avatarPath,
                    initials = chat.initials,
                    seed = chat.colorSeed,
                    online = chat.isOnline,
                    onClick = { onPick(chat.id) },
                )
            }
        }
    }
}
