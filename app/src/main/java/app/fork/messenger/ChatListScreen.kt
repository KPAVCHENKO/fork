package app.fork.messenger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fork.messenger.ui.ForkAvatar
import app.fork.messenger.ui.ForkEmptyState
import app.fork.messenger.ui.ForkIcons
import app.fork.messenger.ui.BrandGradientText
import app.fork.messenger.ui.BrandIndigo
import app.fork.messenger.ui.TimestampStyle
import app.fork.messenger.ui.UnreadBadge
import app.fork.messenger.ui.forkTokens
import kotlinx.coroutines.launch

/** Главный экран — список чатов (Fork Design Spec §4.2). */
@Composable
fun ChatListScreen(onChatClick: (Long) -> Unit, onSettings: () -> Unit, onOpenArchive: () -> Unit = {}) {
    val chats by ChatStore.chatList.collectAsStateWithLifecycle()
    val archive by ChatStore.archiveList.collectAsStateWithLifecycle()
    val folders by ChatStore.folders.collectAsStateWithLifecycle()
    val folderChats by ChatStore.folderChats.collectAsStateWithLifecycle()
    val loading by ChatStore.loading.collectAsStateWithLifecycle()
    val connection by TdClient.connectionState.collectAsStateWithLifecycle()
    val updateState by app.fork.messenger.update.UpdateManager.state.collectAsStateWithLifecycle()
    val tokens = forkTokens

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(query, searching) {
        if (searching) SearchStore.search(query) else SearchStore.clear()
    }

    val title = if (connection == "подключено") "Fork" else connection.replaceFirstChar { it.uppercase() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (searching) {
            SearchHeader(
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
            SearchResults(onChatClick = { id -> searching = false; query = ""; onChatClick(id) })
            return@Column
        }

        // Вкладки = настоящие папки Telegram; между ними можно свайпать (как в Telegram).
        val tabs = listOf("Все") + folders.map { it.title }
        val pagerState = androidx.compose.foundation.pager.rememberPagerState { tabs.size }
        val safeTab = pagerState.currentPage.coerceIn(0, tabs.lastIndex)
        val onTab: (Int) -> Unit = { index ->
            scope.launch { pagerState.animateScrollToPage(index) }
        }
        when (tokens.style) {
            SettingsStore.ThemeStyle.AURORA -> AuroraHeader(
                title = title, tabs = tabs, tab = safeTab,
                onTab = onTab, onSearch = { searching = true }, onSettings = onSettings,
            )
            SettingsStore.ThemeStyle.FROST -> FrostHeader(
                title = title, tabs = tabs, tab = safeTab,
                onTab = onTab, onSearch = { searching = true }, onSettings = onSettings,
            )
            SettingsStore.ThemeStyle.NEON -> NeonHeader(
                title = title, tabs = tabs, tab = safeTab,
                onTab = onTab, onSearch = { searching = true }, onSettings = onSettings,
            )
        }

        val available = updateState as? app.fork.messenger.update.UpdateState.Available
        if (available != null) {
            UpdateBanner(version = available.release.version, onClick = onSettings)
        }

        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            val filtered = if (page == 0) {
                chats
            } else {
                folderChats[folders.getOrNull(page - 1)?.id].orEmpty()
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (loading && chats.isEmpty()) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    } else {
                        ForkEmptyState(title = "Пока тишина", subtitle = "Начните первый чат")
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (page == 0 && archive.isNotEmpty()) {
                        item(key = "archive_entry", contentType = "archive") {
                            ArchiveEntry(count = archive.sumOf { it.unread }, onClick = onOpenArchive)
                        }
                    }
                    items(filtered, key = { it.id }, contentType = { "chat" }) { chat ->
                        ChatRow(chat = chat, onClick = { onChatClick(chat.id) })
                    }
                }
            }
        }
    }
}

/** Строка входа в архив над списком чатов. */
@Composable
private fun ArchiveEntry(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ForkIcons.Archive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text("Архив", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (count > 0) UnreadBadge(count = count, muted = true)
    }
}

// ---------------------------------------------------------------------------
// Шапки трёх стилей
// ---------------------------------------------------------------------------

/** Aurora: градиентный кэп с поиском и чипами папок. */
@Composable
private fun AuroraHeader(
    title: String,
    tabs: List<String>,
    tab: Int,
    onTab: (Int) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val tokens = forkTokens
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .background(tokens.brandGradient),
    ) {
        Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    ForkIcons.ForkMark,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        ForkIcons.Settings,
                        contentDescription = "настройки",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            SearchCapsule(
                onClick = onSearch,
                container = Color.White.copy(alpha = 0.2f),
                contentColor = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(12.dp))
            FolderChips(
                tabs = tabs, tab = tab, onTab = onTab,
                activeBg = Color.White, activeText = BrandIndigo,
                inactiveBg = Color.White.copy(alpha = 0.18f), inactiveText = Color.White,
            )
        }
    }
}

/** Frost: плавающая стеклянная карточка. */
@Composable
private fun FrostHeader(
    title: String,
    tabs: List<String>,
    tab: Int,
    onTab: (Int) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val tokens = forkTokens
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(tokens.glassPanel)
            .border(1.dp, tokens.glassBorder, shape),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (title == "Fork") "Чаты" else title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSettings, modifier = Modifier.size(38.dp)) {
                    Icon(
                        ForkIcons.Settings,
                        contentDescription = "настройки",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SearchCapsule(
                onClick = onSearch,
                container = Color.White.copy(alpha = if (tokens.dark) 0.07f else 0.55f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FolderChips(
                tabs = tabs, tab = tab, onTab = onTab,
                activeBg = MaterialTheme.colorScheme.onSurface, activeText = MaterialTheme.colorScheme.surface,
                inactiveBg = Color.White.copy(alpha = if (tokens.dark) 0.07f else 0.55f),
                inactiveText = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Neon Ink: без подложки — крупная типографика и градиентные акценты. */
@Composable
private fun NeonHeader(
    title: String,
    tabs: List<String>,
    tab: Int,
    onTab: (Int) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
        Text(
            text = if (title == "Fork") "FORK" else title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 2.5.sp,
                brush = BrandGradientText,
            ),
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Чаты",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            NeonRoundButton(icon = { Icon(ForkIcons.Search, contentDescription = "поиск", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }, onClick = onSearch)
            Spacer(Modifier.width(10.dp))
            NeonRoundButton(icon = { Icon(ForkIcons.Settings, contentDescription = "настройки", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }, onClick = onSettings)
        }
        Spacer(Modifier.height(14.dp))
        FolderTabs(tabs = tabs, tab = tab, onTab = onTab)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun NeonRoundButton(icon: @Composable () -> Unit, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

/** Капсула «Поиск» 46dp — открывает режим поиска. */
@Composable
private fun SearchCapsule(onClick: () -> Unit, container: Color, contentColor: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ForkIcons.Search, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("Поиск", style = MaterialTheme.typography.bodyLarge, color = contentColor)
    }
}

/** Чипы папок 32dp (Aurora / Frost). */
@Composable
private fun FolderChips(
    tabs: List<String>,
    tab: Int,
    onTab: (Int) -> Unit,
    activeBg: Color,
    activeText: Color,
    inactiveBg: Color,
    inactiveText: Color,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val active = tab == index
            Box(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (active) activeBg else inactiveBg)
                    .clickable { onTab(index) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) activeText else inactiveText,
                )
            }
        }
    }
}

/** Текстовые табы с градиентным подчёркиванием 3dp (Neon Ink). */
@Composable
private fun FolderTabs(tabs: List<String>, tab: Int, onTab: (Int) -> Unit) {
    val tokens = forkTokens
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val active = tab == index
            Column(
                Modifier.clickable { onTab(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .width(22.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .then(
                            if (active) Modifier.background(tokens.brandGradient)
                            else Modifier,
                        ),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Поиск
// ---------------------------------------------------------------------------

@Composable
private fun SearchHeader(
    query: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                ForkIcons.ArrowBack,
                contentDescription = "назад",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(
            Modifier
                .weight(1f)
                .height(46.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    "Чаты, @имя или ссылка",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
    }
}

@Composable
private fun SearchResults(onChatClick: (Long) -> Unit) {
    val ids by SearchStore.results.collectAsStateWithLifecycle()
    val status by SearchStore.status.collectAsStateWithLifecycle()
    val revision by ChatStore.revision.collectAsStateWithLifecycle()

    val items = remember(ids, revision) { ids.mapNotNull { ChatStore.uiFor(it) } }

    if (status != null) {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                status!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (items.isEmpty() && status == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ForkEmptyState(
                title = "Кого ищем?",
                subtitle = "Имя чата, @имя или ссылка",
                iconSize = 72.dp,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { chat ->
            ChatRow(chat = chat, onClick = { onChatClick(chat.id) })
        }
    }
}

// ---------------------------------------------------------------------------
// Баннер обновления и ячейка чата
// ---------------------------------------------------------------------------

@Composable
private fun UpdateBanner(version: String, onClick: () -> Unit) {
    val tokens = forkTokens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tokens.brandGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ForkIcons.Download,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Доступно обновление",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Fork $version",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Установить",
            style = MaterialTheme.typography.labelMedium,
            color = tokens.checkCyan,
        )
    }
}

/** Ячейка чата 76dp: аватар 56 с онлайн-кольцом, имя, превью, время, бейдж. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun ChatRow(chat: UiChat, onClick: () -> Unit) {
    val tokens = forkTokens
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(if (chat.isPinned) "Открепить" else "Закрепить") },
                onClick = { ChatStore.togglePin(chat.id); menuOpen = false },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(if (chat.isMuted) "Включить звук" else "Без звука") },
                onClick = { ChatStore.toggleMute(chat.id); menuOpen = false },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("В архив") },
                onClick = { ChatStore.archive(chat.id, true); menuOpen = false },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(if (chat.kind == ChatKind.PRIVATE) "Удалить" else "Покинуть") },
                onClick = { ChatStore.deleteOrLeave(chat.id); menuOpen = false },
            )
        }
        ForkAvatar(
            size = 56.dp,
            avatarPath = chat.avatarPath,
            initials = chat.initials,
            seed = chat.colorSeed,
            online = chat.isOnline,
        )
        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (chat.isMuted) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        ForkIcons.VolumeOff,
                        contentDescription = "без звука",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = chat.time,
                    style = TimestampStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(3.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val voicePreview = chat.preview.startsWith("🎤")
                chat.previewThumb?.let { thumb ->
                    androidx.compose.foundation.Image(
                        bitmap = thumb,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = chat.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (voicePreview) tokens.checkCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                when {
                    chat.unread > 0 -> UnreadBadge(chat.unread, chat.isMuted)
                    chat.isPinned -> Icon(
                        ForkIcons.PushPin,
                        contentDescription = "закреплён",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}
