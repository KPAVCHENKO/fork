package app.fork.messenger

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/** Новый чат: «Избранное» + список контактов. */
@Composable
fun NewChatScreen(onBack: () -> Unit, onOpenChat: (Long) -> Unit) {
    LaunchedEffect(Unit) { ContactsStore.load() }
    val contacts by ContactsStore.contacts.collectAsStateWithLifecycle()

    PickerScaffold(title = "Новый чат", onBack = onBack) {
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "saved") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { ContactsStore.openSavedMessages { onOpenChat(it) } }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(app.fork.messenger.ui.forkTokens.brandGradient),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(ForkIcons.ForkMark, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Избранное", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (contacts.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        ForkEmptyState(title = "Нет контактов", subtitle = "Здесь появятся ваши контакты из Telegram")
                    }
                }
            } else {
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

/** Архив: список архивных чатов; долгое нажатие — вернуть из архива. */
@Composable
fun ArchiveScreen(onBack: () -> Unit, onOpenChat: (Long) -> Unit) {
    val archive by ChatStore.archiveList.collectAsStateWithLifecycle()
    PickerScaffold(title = "Архив", onBack = onBack) {
        if (archive.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ForkEmptyState(title = "Архив пуст", subtitle = "Архивированные чаты появятся здесь")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(archive, key = { it.id }) { chat ->
                    ArchiveRow(chat = chat, onClick = { onOpenChat(chat.id) })
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun ArchiveRow(chat: UiChat, onClick: () -> Unit) {
    var menuOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ForkAvatar(size = 50.dp, avatarPath = chat.avatarPath, initials = chat.initials, seed = chat.colorSeed, online = chat.isOnline)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(chat.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (chat.preview.isNotBlank()) {
                    Text(
                        chat.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Вернуть из архива") },
                onClick = { ChatStore.archive(chat.id, false); menuOpen = false },
            )
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
