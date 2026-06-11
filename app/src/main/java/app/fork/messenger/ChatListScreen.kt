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



import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fork.messenger.ui.avatarBrush
import coil.compose.AsyncImage
import java.io.File

/** Главный экран — список чатов. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(onChatClick: (Long) -> Unit, onSettings: () -> Unit) {
    val chats by ChatStore.chatList.collectAsStateWithLifecycle()
    val loading by ChatStore.loading.collectAsStateWithLifecycle()
    val connection by TdClient.connectionState.collectAsStateWithLifecycle()
    val updateState by app.fork.messenger.update.UpdateManager.state.collectAsStateWithLifecycle()

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(query, searching) {
        if (searching) SearchStore.search(query) else SearchStore.clear()
    }

    Scaffold(
        topBar = {
            if (searching) {
                SearchBar(
                    query = query,
                    onQuery = { query = it },
                    onClose = { searching = false; query = "" },
                    onSubmit = {
                        if (query.isNotBlank()) {
                            SearchStore.resolveAndOpen(query) { id ->
                                searching = false; query = ""; onChatClick(id)
                            }
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = if (connection == "подключено") "Fork" else connection.replaceFirstChar { it.uppercase() },
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    actions = {
                        IconButton(onClick = { searching = true }) {
                            Icon(app.fork.messenger.ui.ForkIcons.Search, contentDescription = "поиск")
                        }
                        IconButton(onClick = onSettings) {
                            Icon(app.fork.messenger.ui.ForkIcons.Settings, contentDescription = "настройки")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searching) {
                SearchResults(onChatClick = { id -> searching = false; query = ""; onChatClick(id) })
                return@Column
            }

            val available = updateState as? app.fork.messenger.update.UpdateState.Available
            if (available != null) {
                UpdateBanner(version = available.release.version, onClick = onSettings)
            }

            var tab by rememberSaveable { mutableStateOf(0) }
            val tabs = listOf("Все", "Личные", "Группы", "Каналы")
            val filtered = when (tab) {
                1 -> chats.filter { it.kind == ChatKind.PRIVATE }
                2 -> chats.filter { it.kind == ChatKind.GROUP }
                3 -> chats.filter { it.kind == ChatKind.CHANNEL }
                else -> chats
            }

            if (chats.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = tab,
                    edgePadding = 8.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    divider = {},
                ) {
                    tabs.forEachIndexed { index, label ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(label, fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal) },
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (loading && chats.isEmpty()) {
                        CircularProgressIndicator()
                    } else {
                        Text("Здесь пусто", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { chat ->
                        ChatRow(chat = chat, onClick = { onChatClick(chat.id) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(app.fork.messenger.ui.ForkIcons.ArrowBack, contentDescription = "назад")
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Поиск чатов, @имя или ссылка") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun SearchResults(onChatClick: (Long) -> Unit) {
    val ids by SearchStore.results.collectAsStateWithLifecycle()
    val status by SearchStore.status.collectAsStateWithLifecycle()
    val revision by ChatStore.revision.collectAsStateWithLifecycle()

    val items = remember(ids, revision) { ids.mapNotNull { ChatStore.uiFor(it) } }

    if (status != null) {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(status!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (items.isEmpty() && status == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Введите запрос для поиска", color = MaterialTheme.colorScheme.outline)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { chat ->
            ChatRow(chat = chat, onClick = { onChatClick(chat.id) })
        }
    }
}

@Composable
private fun UpdateBanner(version: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                app.fork.messenger.ui.ForkIcons.Download,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Доступно обновление $version — нажмите, чтобы установить",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
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
