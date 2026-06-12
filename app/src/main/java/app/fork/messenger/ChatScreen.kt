package app.fork.messenger

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fork.messenger.media.AnimationContent
import app.fork.messenger.media.DocumentContent
import app.fork.messenger.media.MediaSend
import app.fork.messenger.media.MediaTarget
import app.fork.messenger.media.MediaViewer
import app.fork.messenger.media.PhotoContent
import app.fork.messenger.media.inlineSize
import app.fork.messenger.media.StickerContent
import app.fork.messenger.media.VideoContent
import app.fork.messenger.media.VoiceContent
import app.fork.messenger.ui.ForkAvatar
import app.fork.messenger.ui.ForkEmptyState
import app.fork.messenger.ui.ForkIcons
import app.fork.messenger.ui.GlassPill
import app.fork.messenger.ui.MessageTextStyle
import app.fork.messenger.ui.TimestampStyle
import app.fork.messenger.ui.forkTokens
import app.fork.messenger.ui.senderColor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

/** Экран переписки (Fork Design Spec §4.3). */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(chatId: Long, onBack: () -> Unit, onOpenInfo: (Long) -> Unit) {
    val rawMessages by MessageStore.messages.collectAsStateWithLifecycle()
    val openedChat by MessageStore.openedChat.collectAsStateWithLifecycle()
    // Первый кадр нового чата не должен показывать ленту предыдущего.
    val messages = if (openedChat == chatId) rawMessages else emptyList()
    val header by MessageStore.header.collectAsStateWithLifecycle()
    val loading by MessageStore.loadingHistory.collectAsStateWithLifecycle()
    val tokens = forkTokens

    DisposableEffect(chatId) {
        MessageStore.open(chatId)
        onDispose { MessageStore.close() }
    }

    var mediaTarget by remember { mutableStateOf<MediaTarget?>(null) }
    var searchMode by remember(chatId) { mutableStateOf(false) }
    var searchQuery by remember(chatId) { mutableStateOf("") }
    // Мультивыбор сообщений: непустой список = режим выбора.
    val selection = remember(chatId) { androidx.compose.runtime.mutableStateListOf<Long>() }
    val selectionMode = selection.isNotEmpty()

    // Обои чата (Fork Design Spec §3.8). Предпросмотр из шторки имеет приоритет —
    // выбор применяется живьём за шторкой; после закрытия читаем сохранённое.
    val wallpaperRevision by SettingsStore.wallpaperRevision.collectAsStateWithLifecycle()
    val defaultWallpaper by SettingsStore.defaultWallpaper.collectAsStateWithLifecycle()
    val wallpaperPreview by SettingsStore.wallpaperPreview.collectAsStateWithLifecycle()
    val wallpaper = remember(chatId, wallpaperRevision, defaultWallpaper, wallpaperPreview) {
        app.fork.messenger.ui.ChatWallpaper.byId(
            wallpaperPreview?.first ?: SettingsStore.wallpaperFor(chatId),
        )
    }
    val storedDim by SettingsStore.wallpaperDim.collectAsStateWithLifecycle()
    val wallpaperDim = wallpaperPreview?.second ?: storedDim
    val amoled by SettingsStore.amoled.collectAsStateWithLifecycle()
    var showWallpaperSheet by remember { mutableStateOf(false) }
    var topMenuOpen by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showAutoDelete by remember { mutableStateOf(false) }
    var showMuteOptions by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var stickerSetId by remember { mutableStateOf<Long?>(null) }

    // Назад: сперва закрыть клавиатуру, потом выбор/поиск, и только потом выйти из чата.
    // Опираемся на фокус поля ввода (а не на инсеты IME — они приходят с задержкой,
    // из-за чего «назад» проскакивал в выход из чата).
    var inputFocused by remember(chatId) { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    BackHandler {
        when {
            inputFocused || imeVisible -> {
                focusManager.clearFocus()
                keyboard?.hide()
            }
            selectionMode -> selection.clear()
            searchMode -> {
                searchMode = false
                searchQuery = ""
            }
            else -> onBack()
        }
    }
    val searchHits by MessageStore.searchHits.collectAsStateWithLifecycle()
    val detached by MessageStore.detached.collectAsStateWithLifecycle()
    val firstUnreadId by MessageStore.firstUnread.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reversed = messages.asReversed()
    // Пауза анимаций стикеров во время прокрутки (плавный фling). derivedStateOf —
    // меняется только на старт/стоп скролла, а не каждый кадр.
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }

    // Автопрокрутка к новому сообщению, если пользователь уже у низа.
    val newestId = reversed.firstOrNull()?.id
    LaunchedEffect(newestId) {
        if (newestId != null && listState.firstVisibleItemIndex <= 2 && !detached) {
            listState.animateScrollToItem(0)
        }
    }

    // Прыжок из поиска/закрепа: прокрутка к сообщению, когда оно появилось в списке.
    val scrollTarget by MessageStore.scrollTo.collectAsStateWithLifecycle()
    LaunchedEffect(scrollTarget, messages) {
        val target = scrollTarget ?: return@LaunchedEffect
        val idx = reversed.indexOfFirst { it.id == target }
        if (idx >= 0) {
            listState.scrollToItem(idx)
            MessageStore.consumeScrollTarget()
        }
    }

    // Поиск с лёгкой задержкой, чтобы не дёргать сервер на каждую букву.
    LaunchedEffect(searchQuery, searchMode) {
        if (searchMode && searchQuery.isNotBlank()) {
            kotlinx.coroutines.delay(300)
            MessageStore.searchInChat(searchQuery)
        } else {
            MessageStore.clearChatSearch()
        }
    }

    // Когда прокрутили к самым старым сообщениям — догружаем историю.
    // Когда у низа «окна» после прыжка — догружаем более новые.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo.visibleItemsInfo
            (info.lastOrNull()?.index ?: 0) to listState.firstVisibleItemIndex
        }
            .distinctUntilChanged()
            .collect { (last, first) ->
                val total = MessageStore.messages.value.size
                if (total > 0 && last >= total - 5) MessageStore.loadMore()
                if (total > 0 && first <= 2) MessageStore.loadNewer()
            }
    }

    // Помечаем прочитанным самое новое видимое сообщение (как в Telegram): по мере
    // прокрутки вниз непрочитанные исчезают, и счётчик в списке чатов очищается.
    LaunchedEffect(listState, chatId) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { idx ->
                val list = MessageStore.messages.value
                // reverseLayout: нижнее видимое = list[size-1-idx].
                list.getOrNull(list.size - 1 - idx)?.let { MessageStore.markViewed(it.id) }
            }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (selectionMode) {
                    SelectionTopBar(
                        count = selection.size,
                        onClose = { selection.clear() },
                        onForward = {
                            ForwardBus.start(chatId, selection.toLongArray())
                            selection.clear()
                        },
                        onDelete = { forAll ->
                            MessageStore.deleteMessages(selection.toLongArray(), forAll)
                            selection.clear()
                        },
                        canDeleteForAll = reversed
                            .filter { it.id in selection }
                            .all { it.canDeleteForAll },
                    )
                } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            if (searchMode) {
                                searchMode = false
                                searchQuery = ""
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(
                                ForkIcons.ArrowBack,
                                contentDescription = "назад",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                    title = {
                        if (searchMode) {
                            ChatSearchField(query = searchQuery, onQuery = { searchQuery = it })
                        } else {
                            val h = header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onOpenInfo(chatId) },
                            ) {
                                if (h != null) {
                                    ForkAvatar(
                                        size = 44.dp,
                                        avatarPath = h.avatarPath,
                                        initials = h.initials,
                                        seed = h.colorSeed,
                                        online = h.subtitle == "онлайн",
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        h?.title ?: "",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (h != null && h.subtitle.isNotBlank()) {
                                        val accent = h.subtitle == "онлайн" || h.subtitle.contains("печатает")
                                        Text(
                                            h.subtitle,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (accent) tokens.checkCyan
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        if (!searchMode) {
                            IconButton(onClick = { searchMode = true }) {
                                Icon(
                                    ForkIcons.Search,
                                    contentDescription = "поиск по чату",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Box {
                                IconButton(onClick = { topMenuOpen = true }) {
                                    Icon(
                                        ForkIcons.MoreVert,
                                        contentDescription = "меню",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                androidx.compose.material3.DropdownMenu(
                                    expanded = topMenuOpen,
                                    onDismissRequest = { topMenuOpen = false },
                                ) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Профиль") },
                                        onClick = { topMenuOpen = false; onOpenInfo(chatId) },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Фон чата") },
                                        onClick = { topMenuOpen = false; showWallpaperSheet = true },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(if (MessageStore.isMuted(chatId)) "Включить звук" else "Без звука…") },
                                        onClick = {
                                            topMenuOpen = false
                                            if (MessageStore.isMuted(chatId)) MessageStore.muteFor(chatId, 0)
                                            else showMuteOptions = true
                                        },
                                    )
                                    if (MessageStore.isPrivateChat(chatId)) {
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Заблокировать") },
                                            onClick = { topMenuOpen = false; showBlockConfirm = true },
                                        )
                                    }
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(if (MessageStore.isPinnedChat(chatId)) "Открепить чат" else "Закрепить чат") },
                                        onClick = {
                                            topMenuOpen = false
                                            MessageStore.togglePinChat(chatId, !MessageStore.isPinnedChat(chatId))
                                        },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Перейти к дате") },
                                        onClick = { topMenuOpen = false; showDatePicker = true },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Автоудаление") },
                                        onClick = { topMenuOpen = false; showAutoDelete = true },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Отметить непрочитанным") },
                                        onClick = {
                                            topMenuOpen = false
                                            MessageStore.toggleUnread(chatId, true)
                                        },
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Очистить историю") },
                                        onClick = { topMenuOpen = false; showClearConfirm = true },
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                }
            },
            bottomBar = {
                if (header?.canWrite != false) {
                    MessageInput(chatId, onFocusChanged = { inputFocused = it })
                } else {
                    ReadOnlyBar()
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                val pinned by MessageStore.pinned.collectAsStateWithLifecycle()
                if (!searchMode) {
                    pinned?.let { pin ->
                        PinnedMessageBar(
                            text = pin.text,
                            onClick = { MessageStore.jumpTo(pin.messageId) },
                        )
                    }
                }
                // Результаты поиска по чату — списком поверх переписки.
                if (searchMode && searchHits.isNotEmpty()) {
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        itemsIndexed(searchHits, key = { _, hit -> hit.messageId }) { _, hit ->
                            SearchHitRow(
                                hit = hit,
                                onClick = {
                                    searchMode = false
                                    searchQuery = ""
                                    MessageStore.jumpTo(hit.messageId)
                                },
                            )
                        }
                    }
                    return@Column
                }
                Box(Modifier.fillMaxSize()) {
                // Обои — слой между фоном и лентой; кроссфейд 250ms при смене.
                androidx.compose.animation.Crossfade(
                    targetState = wallpaper,
                    animationSpec = tween(250),
                    label = "wallpaper",
                ) { wp ->
                    app.fork.messenger.ui.ChatWallpaperCanvas(
                        wallpaper = wp,
                        dark = tokens.dark,
                        amoled = amoled,
                        dim = wallpaperDim,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    itemsIndexed(
                        reversed,
                        key = { _, m -> m.id },
                        contentType = { _, _ -> "message" },
                    ) { index, message ->
                        val older = reversed.getOrNull(index + 1)
                        Column {
                            if (older == null || older.dateLabel != message.dateLabel) {
                                DateCapsule(message.dateLabel)
                            }
                            // Разделитель «Непрочитанные сообщения» перед первым непрочитанным.
                            if (message.id == firstUnreadId && firstUnreadId != 0L) {
                                UnreadDivider()
                            }
                            MessageRow(
                                message,
                                onOpenMedia = { mediaTarget = it },
                                selectionMode = selectionMode,
                                selected = message.id in selection,
                                onToggleSelect = {
                                    if (message.id in selection) selection.remove(message.id)
                                    else selection.add(message.id)
                                },
                                onOpenStickerSet = { stickerSetId = it },
                                // TGS animate always (rlottie, cheap). WEBM video
                                // stickers (ExoPlayer/SurfaceView) only when idle, so a
                                // fling never spins up several SurfaceViews at once.
                                animateStickers = !isScrolling,
                            )
                        }
                    }
                    if (loading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    if (!loading && reversed.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 96.dp), contentAlignment = Alignment.Center) {
                                ForkEmptyState(title = "Напишите первым", iconSize = 72.dp)
                            }
                        }
                    }
                }

                // Кнопка «вниз» — стеклянная капсула 46dp (Fork Design Spec §4.3).
                val showScrollDown by remember {
                    derivedStateOf { listState.firstVisibleItemIndex > 3 }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = showScrollDown || detached,
                    enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.7f),
                    exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    Box(contentAlignment = Alignment.TopCenter) {
                        GlassPill(
                            modifier = Modifier
                                .size(46.dp)
                                .clickable {
                                    if (detached) {
                                        MessageStore.returnToLatest()
                                    } else {
                                        scope.launch { listState.animateScrollToItem(0) }
                                    }
                                },
                        ) {
                            Icon(
                                ForkIcons.Down,
                                contentDescription = "вниз",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        // Бейдж непрочитанных над кнопкой «вниз» (как в Telegram).
                        val chatRev by ChatStore.revision.collectAsStateWithLifecycle()
                        val unread = remember(chatRev, chatId) { ChatStore.chat(chatId)?.unreadCount ?: 0 }
                        if (unread > 0) {
                            Box(Modifier.offset(y = (-8).dp)) {
                                app.fork.messenger.ui.UnreadBadge(unread, muted = false)
                            }
                        }
                    }
                }
                }
            }
        }

        // Свайп-назад от левого края экрана (как в Telegram), не мешает свайпу сообщений.
        Box(
            Modifier
                .fillMaxHeight()
                .width(24.dp)
                .align(Alignment.CenterStart)
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (total > 56.dp.toPx()) onBack()
                            total = 0f
                        },
                    ) { change, drag ->
                        change.consume()
                        total += drag
                    }
                },
        )

        mediaTarget?.let { target ->
            // Листаемый просмотрщик: собираем все медиа чата и открываем на нажатом.
            val all = remember(target) { MessageStore.collectMedia() }
            val list = if (all.isEmpty()) listOf(target) else all
            val start = remember(list, target) {
                val key = app.fork.messenger.media.mediaKey(target)
                list.indexOfFirst { app.fork.messenger.media.mediaKey(it) == key }.coerceAtLeast(0)
            }
            MediaViewer(targets = list, startIndex = start, onClose = { mediaTarget = null })
        }

        if (showWallpaperSheet) {
            WallpaperSheet(chatId = chatId, onDismiss = { showWallpaperSheet = false })
        }

        stickerSetId?.let { setId ->
            app.fork.messenger.media.StickerSetSheet(
                setId = setId,
                onDismiss = { stickerSetId = null },
                onPick = { MessageStore.sendSticker(it) },
            )
        }

        TranslationDialog()

        if (showClearConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("Очистить историю?") },
                text = { Text("Все сообщения в этом чате будут удалены у вас.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        MessageStore.clearHistory(chatId)
                        showClearConfirm = false
                    }) { Text("Очистить") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showClearConfirm = false }) {
                        Text("Отмена")
                    }
                },
            )
        }

        if (showDatePicker) {
            JumpToDateDialog(onDismiss = { showDatePicker = false })
        }
        if (showAutoDelete) {
            AutoDeleteDialog(chatId = chatId, onDismiss = { showAutoDelete = false })
        }
        if (showMuteOptions) {
            MuteOptionsDialog(chatId = chatId, onDismiss = { showMuteOptions = false })
        }
        if (showBlockConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showBlockConfirm = false },
                title = { Text("Заблокировать?") },
                text = { Text("Пользователь больше не сможет писать вам.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        MessageStore.blockUser(chatId, true)
                        showBlockConfirm = false
                    }) { Text("Заблокировать", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showBlockConfirm = false }) { Text("Отмена") }
                },
            )
        }
    }
}

/** Диалог выбора длительности «без звука». */
@Composable
private fun MuteOptionsDialog(chatId: Long, onDismiss: () -> Unit) {
    val options = listOf(
        "На 1 час" to 3600,
        "На 8 часов" to 28_800,
        "На 2 дня" to 172_800,
        "Навсегда" to Int.MAX_VALUE,
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Без звука") },
        text = {
            Column {
                options.forEach { (label, secs) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { MessageStore.muteFor(chatId, secs); onDismiss() }
                            .padding(vertical = 12.dp),
                    ) { Text(label, style = MaterialTheme.typography.bodyLarge) }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JumpToDateDialog(onDismiss: () -> Unit) {
    val dateState = androidx.compose.material3.rememberDatePickerState()
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                dateState.selectedDateMillis?.let { MessageStore.jumpToDate((it / 1000).toInt()) }
                onDismiss()
            }) { Text("Перейти") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    ) {
        androidx.compose.material3.DatePicker(state = dateState)
    }
}

@Composable
private fun AutoDeleteDialog(chatId: Long, onDismiss: () -> Unit) {
    val options = listOf(
        "Выключено" to 0,
        "1 день" to 86_400,
        "1 неделя" to 604_800,
        "1 месяц" to 2_592_000,
    )
    val current = MessageStore.autoDeleteTime(chatId)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Автоудаление сообщений") },
        text = {
            Column {
                options.forEach { (label, secs) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { MessageStore.setAutoDelete(chatId, secs); onDismiss() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = current == secs,
                            onClick = { MessageStore.setAutoDelete(chatId, secs); onDismiss() },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}

/** Диалог перевода сообщения (TDLib TranslateMessageText). */
@Composable
private fun TranslationDialog() {
    val translation by MessageStore.translation.collectAsStateWithLifecycle()
    translation?.let { t ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { MessageStore.clearTranslation() },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { MessageStore.clearTranslation() }) {
                    Text("Закрыть")
                }
            },
            title = { Text("Перевод") },
            text = {
                if (t.loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Переводим…")
                    }
                } else {
                    Text(t.text ?: "Не удалось перевести")
                }
            },
        )
    }
}

/** Шапка режима мультивыбора: счётчик + переслать + удалить. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onForward: () -> Unit,
    onDelete: (forAll: Boolean) -> Unit,
    canDeleteForAll: Boolean,
) {
    var deleteMenu by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(ForkIcons.Close, contentDescription = "отмена", tint = MaterialTheme.colorScheme.onSurface)
            }
        },
        title = {
            Text(
                "$count выбрано",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        actions = {
            IconButton(onClick = onForward) {
                Icon(ForkIcons.Forward, contentDescription = "переслать", tint = MaterialTheme.colorScheme.onSurface)
            }
            Box {
                IconButton(onClick = { deleteMenu = true }) {
                    Icon(ForkIcons.Trash, contentDescription = "удалить", tint = MaterialTheme.colorScheme.error)
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = deleteMenu,
                    onDismissRequest = { deleteMenu = false },
                ) {
                    if (canDeleteForAll) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Удалить у всех") },
                            onClick = { deleteMenu = false; onDelete(true) },
                        )
                    }
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Удалить у себя") },
                        onClick = { deleteMenu = false; onDelete(false) },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/** Поле поиска по чату в шапке. */
@Composable
private fun ChatSearchField(query: String, onQuery: (String) -> Unit) {
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(contentAlignment = Alignment.CenterStart) {
        if (query.isEmpty()) {
            Text(
                "Поиск по чату",
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
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
    }
}

/** Строка результата поиска по чату. */
@Composable
private fun SearchHitRow(hit: ChatSearchHit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                hit.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                hit.snippet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            hit.time,
            style = TimestampStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Плашка закреплённого сообщения под шапкой чата (как в Telegram). */
@Composable
private fun PinnedMessageBar(text: String, onClick: () -> Unit) {
    val tokens = forkTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tokens.brandGradient),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Закреплённое сообщение",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.checkCyan,
            )
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            ForkIcons.PushPin,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Разделитель «Непрочитанные сообщения» (как в Telegram). */
@Composable
private fun UnreadDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(forkTokens.glassPill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Непрочитанные сообщения",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.padding(vertical = 5.dp),
        )
    }
}

/** Дата-разделитель — стеклянная капсула по центру (Fork Design Spec §7.6). */
@Composable
private fun DateCapsule(label: String) {
    if (label.isBlank()) return
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        GlassPill(shape = RoundedCornerShape(14.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: UiMessage,
    onOpenMedia: (MediaTarget) -> Unit,
    onOpenStickerSet: (Long) -> Unit = {},
    animateStickers: Boolean = true,
) {
    val tokens = forkTokens
    val content = message.content

    // Стикеры и видеокружки показываем без пузыря.
    if (content is TdApi.MessageSticker) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
        ) {
            if (message.showSender && message.senderName != null) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = senderColor(message.senderSeed),
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
            }
            Box {
                StickerContent(content.sticker, play = animateStickers) {
                    if (content.sticker.setId != 0L) onOpenStickerSet(content.sticker.setId)
                }
                // Время + галочки капсулой в углу стикера (как в Telegram).
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color(0x8C050912))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    TimeStatus(message.time, message.outStatus, mine = true)
                }
            }
            if (message.reactions.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                ReactionChips(message.id, message.reactions, mine = message.isMine)
            }
        }
        return
    }
    if (content is TdApi.MessageVideoNote) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
        ) {
            app.fork.messenger.media.VideoNoteContent(content.videoNote)
        }
        return
    }
    // Одиночное эмодзи — показываем как анимированный стикер (как в Telegram).
    if (content is TdApi.MessageAnimatedEmoji) {
        val st = content.animatedEmoji.sticker
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
        ) {
            if (st != null) {
                app.fork.messenger.media.StickerContent(st, play = animateStickers, size = 96.dp)
            } else {
                Text(content.emoji, style = MaterialTheme.typography.displaySmall)
            }
        }
        return
    }

    // Малый угол — у нижнего угла со стороны хвоста, только у последнего в группе.
    val big = tokens.bubbleRadius
    val small = tokens.bubbleRadiusSmall
    val shape = RoundedCornerShape(
        topStart = big,
        topEnd = big,
        bottomStart = if (!message.isMine && message.isLastOfGroup) small else big,
        bottomEnd = if (message.isMine && message.isLastOfGroup) small else big,
    )
    val isText = content is TdApi.MessageText
    val caption = captionText(content)
    val isVisualMedia = content is TdApi.MessagePhoto ||
        content is TdApi.MessageVideo || content is TdApi.MessageAnimation

    // «Голое» медиа без подписи/ответа/имени — чистое фото без пузыря (как в Telegram),
    // время — капсулой поверх угла снимка.
    if (isVisualMedia && caption == null && message.replyText == null &&
        !message.showSender && message.reactions.isEmpty() && message.forwardFrom == null &&
        message.albumMedia.isEmpty()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (message.isFirstOfGroup) 10.dp else 4.dp),
            horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
        ) {
            Box {
                BubbleMedia(content, message.isMine, onOpenMedia, mediaShape = shape)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color(0x8C050912))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    TimeStatus(message.time, message.outStatus, mine = true)
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (message.isFirstOfGroup) 10.dp else 4.dp),
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .then(
                    if (message.isMine) {
                        Modifier.background(tokens.brandGradient)
                    } else {
                        Modifier
                            .background(tokens.bubbleIn)
                            .border(1.dp, tokens.bubbleInBorder, shape)
                    },
                ),
        ) {
            Column(
                Modifier
                    // Для медиа/альбома фиксируем ширину пузыря по ширине фото (252dp),
                    // чтобы текст переносился по краю фото и сбоку не торчал фон пузыря.
                    .then(
                        if (isVisualMedia || message.albumMedia.isNotEmpty()) Modifier.width(252.dp)
                        else Modifier.widthIn(max = 300.dp),
                    )
                    .padding(
                        when {
                            isText -> PaddingValues(horizontal = 13.dp, vertical = 8.dp)
                            isVisualMedia -> PaddingValues(0.dp) // медиа встык к краям пузыря
                            else -> PaddingValues(4.dp)
                        },
                    ),
            ) {
                if (message.showSender && message.senderName != null) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = senderColor(message.senderSeed),
                        modifier = Modifier.padding(
                            start = if (isText) 0.dp else 10.dp,
                            end = if (isText) 0.dp else 10.dp,
                            top = if (isText) 1.dp else 6.dp,
                            bottom = 1.dp,
                        ),
                    )
                    Spacer(Modifier.height(2.dp))
                }

                // «Переслано от …» (как в Telegram).
                if (message.forwardFrom != null) {
                    val fwPad = if (isText) 0.dp else 10.dp
                    Text(
                        "Переслано от ${message.forwardFrom}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (message.isMine) Color.White else tokens.checkCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = fwPad, end = fwPad, top = if (isText) 0.dp else 6.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                }

                if (message.replyText != null) {
                    Box(Modifier.padding(if (isText) PaddingValues(0.dp) else PaddingValues(start = 6.dp, end = 6.dp, top = 6.dp))) {
                        ReplyQuote(message.replyText, mine = message.isMine)
                    }
                    Spacer(Modifier.height(4.dp))
                }

                if (message.albumMedia.isNotEmpty()) {
                    Box(Modifier.padding(4.dp)) { AlbumGrid(message.albumMedia, onOpenMedia) }
                } else {
                    val mediaTop = if (message.showSender || message.replyText != null || message.forwardFrom != null) 6.dp else big
                    BubbleMedia(
                        content, message.isMine, onOpenMedia,
                        mediaShape = if (isVisualMedia) RoundedCornerShape(
                            topStart = mediaTop, topEnd = mediaTop, bottomStart = 4.dp, bottomEnd = 4.dp,
                        ) else null,
                        messageId = message.id,
                    )
                }

                // Форматирование TDLib (жирный/курсив/код/ссылки/спойлеры).
                var spoilersRevealed by remember(message.id) { mutableStateOf(false) }
                val formatted: TdApi.FormattedText? = when (content) {
                    is TdApi.MessageText -> content.text
                    is TdApi.MessagePhoto -> content.caption
                    is TdApi.MessageVideo -> content.caption
                    is TdApi.MessageAnimation -> content.caption
                    is TdApi.MessageDocument -> content.caption
                    else -> null
                }
                val linkColor = if (message.isMine) Color.White else tokens.checkCyan
                val codeBg = if (message.isMine) Color.White.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                val spoilerBg = if (message.isMine) Color.White.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                val annotated = remember(message.id, message.isEdited, spoilersRevealed, content) {
                    formatted?.takeIf { it.text.isNotBlank() }?.toAnnotated(
                        linkColor = linkColor,
                        codeBackground = codeBg,
                        spoilerHidden = spoilerBg,
                        spoilersRevealed = spoilersRevealed,
                        onRevealSpoilers = { spoilersRevealed = true },
                    ) ?: androidx.compose.ui.text.AnnotatedString(if (caption != null) caption else message.text)
                }

                // Inline-контент для кастом/премиум-эмодзи: подставляем стикеры,
                // когда они загрузились; до этого показывается обычный эмодзи-фолбэк.
                val emojiIds = remember(content) { formatted?.customEmojiIds()?.distinct().orEmpty() }
                val emojiVersion by CustomEmojiStore.version.collectAsStateWithLifecycle()
                val inlineEmoji = remember(emojiIds, emojiVersion) {
                    emojiIds.mapNotNull { id ->
                        val st = CustomEmojiStore.sticker(id) ?: return@mapNotNull null
                        "$CUSTOM_EMOJI_PREFIX$id" to androidx.compose.foundation.text.InlineTextContent(
                            androidx.compose.ui.text.Placeholder(
                                width = 1.25.em, height = 1.25.em,
                                placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.Center,
                            ),
                        ) {
                            app.fork.messenger.media.StickerThumb(st, Modifier.fillMaxSize())
                        }
                    }.toMap()
                }

                BubbleText(
                    text = annotated,
                    time = message.time,
                    status = message.outStatus,
                    mine = message.isMine,
                    hasMediaAbove = !isText,
                    showText = isText || caption != null,
                    inlineContent = inlineEmoji,
                )

                // Превью ссылки (как в Telegram): сайт, заголовок, описание, картинка.
                if (content is TdApi.MessageText) {
                    content.linkPreview?.let { lp ->
                        Spacer(Modifier.height(6.dp))
                        LinkPreviewCard(lp, mine = message.isMine, onOpenMedia = onOpenMedia)
                    }
                }

                if (message.reactions.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.padding(
                            if (isText) PaddingValues(0.dp)
                            else PaddingValues(start = 8.dp, end = 8.dp, bottom = 6.dp),
                        ),
                    ) {
                        ReactionChips(message.id, message.reactions, mine = message.isMine)
                    }
                }
            }
        }
    }
}

/** Чипы реакций под пузырём; тап по чипу — поставить/снять свою реакцию. */
@Composable
private fun ReactionChips(messageId: Long, reactions: List<UiReaction>, mine: Boolean) {
    val tokens = forkTokens
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(horizontal = if (mine) 0.dp else 4.dp),
    ) {
        reactions.take(6).forEach { r ->
            val bg = if (r.chosen) tokens.checkCyan.copy(alpha = 0.22f)
            else MaterialTheme.colorScheme.surfaceContainerHigh
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(bg)
                    .clickable { MessageStore.toggleReaction(messageId, r.emoji) }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(r.emoji, style = MaterialTheme.typography.labelMedium)
                if (r.count > 1) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        r.count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (r.chosen) tokens.checkCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Плашка вместо ввода там, где писать нельзя (каналы, ограниченные группы). */
@Composable
private fun ReadOnlyBar() {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Только чтение",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Цитата сообщения, на которое отвечают, внутри пузыря (Fork Design Spec §4.3). */
@Composable
private fun ReplyQuote(text: String, mine: Boolean) {
    val tokens = forkTokens
    val bg = when {
        mine -> Color.White.copy(alpha = 0.18f)
        tokens.dark -> Color.White.copy(alpha = 0.08f)
        else -> Color.Black.copy(alpha = 0.05f)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg),
    ) {
        Box(
            Modifier
                .padding(vertical = 4.dp)
                .width(3.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (mine) Color.White else tokens.checkCyan),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            color = if (mine) Color.White.copy(alpha = 0.9f)
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

/** Карточка превью ссылки внутри пузыря: акцентная полоса, сайт, заголовок, описание, фото. */
@Composable
private fun LinkPreviewCard(
    preview: TdApi.LinkPreview,
    mine: Boolean,
    onOpenMedia: (MediaTarget) -> Unit,
) {
    val tokens = forkTokens
    val context = LocalContext.current
    val bg = when {
        mine -> Color.White.copy(alpha = 0.16f)
        tokens.dark -> Color.White.copy(alpha = 0.07f)
        else -> Color.Black.copy(alpha = 0.04f)
    }
    val accent = if (mine) Color.White else tokens.checkCyan
    Row(
        modifier = Modifier
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(preview.url),
                        ),
                    )
                }
            },
    ) {
        Box(
            Modifier
                .padding(vertical = 6.dp)
                .width(3.dp)
                .fillMaxHeight()
                .defaultMinSize(minHeight = 34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            if (preview.siteName.isNotBlank()) {
                Text(
                    preview.siteName,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (preview.title.isNotBlank()) {
                Text(
                    preview.title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    ),
                    color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val description = preview.description?.text.orEmpty()
            if (description.isNotBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (mine) Color.White.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val photo = (preview.type as? TdApi.LinkPreviewTypeArticle)?.photo
            if (photo != null) {
                Spacer(Modifier.height(6.dp))
                PhotoContent(photo) { onOpenMedia(MediaTarget.Photo(photo)) }
            }
        }
    }
}

/**
 * Мозаика альбома (как в TG): элементы укладываются в ряды по 1–3, выровненные по ширине
 * (justified) — в каждом ряду общая высота, ширины по пропорции. Так фото красиво
 * замощают пузырь, а не идут унылым столбиком, и их удобно тапать/листать.
 */
@Composable
private fun AlbumGrid(items: List<AlbumItem>, onOpenMedia: (MediaTarget) -> Unit) {
    val gap = 2.dp
    val containerW = 250.dp
    val aspects = remember(items) { items.map { albumAspect(it.content) } }
    val rows = remember(aspects) { justifyAlbumRows(aspects) }

    Column(
        Modifier
            .widthIn(max = containerW)
            .clip(RoundedCornerShape(forkTokens.bubbleRadius - 3.dp)),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        var idx = 0
        rows.forEach { count ->
            val rowAspects = aspects.subList(idx, idx + count)
            val sumA = rowAspects.sum().coerceAtLeast(0.1f)
            val rowH = (containerW - gap * (count - 1)) / sumA
            Row(
                Modifier.fillMaxWidth().height(rowH),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                for (k in 0 until count) {
                    val item = items[idx + k]
                    Box(Modifier.weight(rowAspects[k]).fillMaxHeight()) {
                        AlbumCell(item, onOpenMedia)
                    }
                }
            }
            idx += count
        }
    }
}

/** Соотношение сторон элемента альбома (клампим, чтобы экстремальные не ломали мозаику). */
private fun albumAspect(content: TdApi.MessageContent): Float {
    val (w, h) = when (content) {
        is TdApi.MessagePhoto -> content.photo.inlineSize()?.let { it.width to it.height } ?: (1 to 1)
        is TdApi.MessageVideo -> content.video.width to content.video.height
        else -> 1 to 1
    }
    return if (w <= 0 || h <= 0) 1f else (w.toFloat() / h).coerceIn(0.6f, 1.8f)
}

/** Жадно группирует элементы в ряды по 1–3 (целевая «ширина ряда» ~1.8 суммы аспектов). */
private fun justifyAlbumRows(aspects: List<Float>): List<Int> {
    val rows = ArrayList<Int>()
    var i = 0
    while (i < aspects.size) {
        var count = 0
        var sum = 0f
        while (i + count < aspects.size && count < 3) {
            sum += aspects[i + count]
            count++
            if (sum >= 1.8f) break
        }
        rows.add(count)
        i += count
    }
    return rows
}

@Composable
private fun AlbumCell(item: AlbumItem, onOpenMedia: (MediaTarget) -> Unit) {
    when (val c = item.content) {
        is TdApi.MessagePhoto -> {
            val size = c.photo.inlineSize() ?: return
            app.fork.messenger.media.MediaSquare(size.photo, c.photo.minithumbnail) {
                onOpenMedia(MediaTarget.Photo(c.photo))
            }
        }
        is TdApi.MessageVideo -> {
            Box(contentAlignment = Alignment.Center) {
                c.video.thumbnail?.file?.let { thumb ->
                    app.fork.messenger.media.MediaSquare(thumb, c.video.minithumbnail) {
                        onOpenMedia(MediaTarget.Video(c.video))
                    }
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color(0x8C0E1424)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(ForkIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
        else -> Unit
    }
}

/** Картиночная/медийная часть пузыря (если есть). */
@Composable
private fun BubbleMedia(
    content: TdApi.MessageContent?,
    mine: Boolean,
    onOpenMedia: (MediaTarget) -> Unit,
    mediaShape: androidx.compose.ui.graphics.Shape? = null,
    messageId: Long = 0L,
) {
    when (content) {
        is TdApi.MessagePhoto -> PhotoContent(content.photo, mediaShape) { onOpenMedia(MediaTarget.Photo(content.photo)) }
        is TdApi.MessageVideo -> VideoContent(content.video, mediaShape) { onOpenMedia(MediaTarget.Video(content.video)) }
        is TdApi.MessageAnimation -> AnimationContent(content.animation, mediaShape)
        is TdApi.MessageVoiceNote -> VoiceContent(content.voiceNote, mine = mine)
        is TdApi.MessageDocument -> DocumentContent(content.document, mine = mine)
        is TdApi.MessagePoll -> PollContent(messageId, content.poll, mine)
        else -> Unit
    }
}

/** Опрос в пузыре: вопрос, варианты (тап — голос), результаты с прогресс-барами. */
@Composable
private fun PollContent(messageId: Long, poll: TdApi.Poll, mine: Boolean) {
    val tokens = forkTokens
    val voted = poll.isClosed || poll.options.orEmpty().any { it != null && (it.isChosen || it.isBeingChosen) }
    val textColor = if (mine) Color.White else MaterialTheme.colorScheme.onSurface
    val mutedColor = if (mine) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.widthIn(max = 292.dp).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(
            poll.question?.text.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            color = textColor,
        )
        Text(
            when {
                poll.type is TdApi.PollTypeQuiz -> "Викторина"
                poll.isAnonymous -> "Анонимный опрос"
                else -> "Опрос"
            },
            style = MaterialTheme.typography.labelSmall,
            color = mutedColor,
        )
        Spacer(Modifier.height(6.dp))

        poll.options.orEmpty().filterNotNull().forEachIndexed { index, option ->
            if (voted) {
                // Результаты: процент + полоса.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        "${option.votePercentage}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor,
                        modifier = Modifier.width(40.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                option.text?.text.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (option.isChosen) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    ForkIcons.Check,
                                    contentDescription = "ваш голос",
                                    tint = if (mine) Color.White else tokens.checkCyan,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (mine) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth((option.votePercentage / 100f).coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .then(
                                        if (mine) Modifier.background(Color.White)
                                        else Modifier.background(tokens.brandGradient),
                                    ),
                            )
                        }
                    }
                }
            } else {
                // Голосование: тап по варианту.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { MessageStore.votePoll(messageId, intArrayOf(index)) }
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                ) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, mutedColor, CircleShape),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        option.text?.text.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            when {
                poll.totalVoterCount == 0 -> "Пока никто не голосовал"
                else -> "Проголосовало: ${poll.totalVoterCount}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = mutedColor,
        )
    }
}

@Composable
private fun BubbleText(
    text: androidx.compose.ui.text.AnnotatedString,
    time: String,
    status: OutStatus,
    mine: Boolean,
    hasMediaAbove: Boolean,
    showText: Boolean,
    inlineContent: Map<String, androidx.compose.foundation.text.InlineTextContent> = emptyMap(),
) {
    if (!showText) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Spacer(Modifier.weight(1f))
            TimeStatus(time, status, mine)
        }
        return
    }
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.padding(
            horizontal = if (hasMediaAbove) 11.dp else 0.dp,
            vertical = if (hasMediaAbove) 7.dp else 0.dp,
        ),
    ) {
        Text(
            text = text,
            style = MessageTextStyle,
            color = if (mine) Color.White else forkTokens.bubbleTextIn,
            inlineContent = inlineContent,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.padding(start = 8.dp))
        TimeStatus(time, status, mine)
    }
}

/** Время + галочки: циановые «прочитано», белые на градиенте (Fork Design Spec §7.7). */
@Composable
private fun TimeStatus(time: String, status: OutStatus, mine: Boolean) {
    val tokens = forkTokens
    val timeColor = if (mine) Color.White.copy(alpha = 0.75f)
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = time, style = TimestampStyle, color = timeColor)
        val icon = when (status) {
            OutStatus.SENDING -> ForkIcons.Clock
            OutStatus.SENT -> ForkIcons.Check
            OutStatus.READ -> ForkIcons.CheckDouble
            OutStatus.FAILED -> ForkIcons.Clock
            OutStatus.NONE -> null
        }
        if (icon != null) {
            Spacer(Modifier.width(3.dp))
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = when {
                    status == OutStatus.FAILED -> MaterialTheme.colorScheme.error
                    status == OutStatus.READ && mine -> Color.White
                    status == OutStatus.READ -> tokens.checkCyan
                    else -> timeColor
                },
            )
        }
    }
}

private fun captionText(content: TdApi.MessageContent?): String? {
    val caption = when (content) {
        is TdApi.MessagePhoto -> content.caption
        is TdApi.MessageVideo -> content.caption
        is TdApi.MessageAnimation -> content.caption
        is TdApi.MessageDocument -> content.caption
        else -> null
    }
    return caption?.text?.takeIf { it.isNotBlank() }
}

/** Удаляет последний графемный кластер (эмодзи могут быть из нескольких код-поинтов). */
private fun dropLastGrapheme(s: String): String {
    if (s.isEmpty()) return s
    val bi = java.text.BreakIterator.getCharacterInstance()
    bi.setText(s)
    bi.last()
    val prev = bi.previous()
    return if (prev == java.text.BreakIterator.DONE || prev <= 0) "" else s.substring(0, prev)
}

@Composable
private fun MessageInput(chatId: Long, onFocusChanged: (Boolean) -> Unit = {}) {
    var text by rememberSaveable(chatId) { mutableStateOf("") }
    val context = LocalContext.current

    // Черновик: подставляем при входе в чат, автосохраняем в Telegram с дебаунсом.
    LaunchedEffect(chatId) {
        MessageStore.draftText.value?.let { draft ->
            if (text.isEmpty()) text = draft
        }
    }
    LaunchedEffect(text) {
        kotlinx.coroutines.delay(700)
        MessageStore.saveDraft(text)
    }

    // Выбор фото/видео из галереи: сначала показываем предпросмотр с подписью.
    var pendingMedia by remember { mutableStateOf<PendingMedia?>(null) }
    var pendingAlbum by remember { mutableStateOf<List<PendingMedia>?>(null) }
    val mediaScope = rememberCoroutineScope()
    // До 10 фото/видео разом (как в TG): 1 — обычное сообщение, несколько — альбом.
    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        mediaScope.launch {
            val items = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                uris.mapNotNull { uri -> buildPendingMedia(context, uri) }
            }
            when {
                items.size == 1 -> pendingMedia = items[0]
                items.size > 1 -> pendingAlbum = items
            }
        }
    }

    pendingAlbum?.let { album ->
        AlbumPreviewDialog(
            items = album,
            onCancel = { pendingAlbum = null },
            onSend = { caption ->
                MessageStore.sendAlbum(album, caption)
                pendingAlbum = null
            },
        )
    }

    pendingMedia?.let { media ->
        MediaPreviewDialog(
            media = media,
            onCancel = { pendingMedia = null },
            onSend = { caption ->
                if (media.isVideo) {
                    MessageStore.sendVideo(media.path, media.width, media.height, media.duration, caption)
                } else {
                    MessageStore.sendPhoto(media.path, media.width, media.height, caption)
                }
                pendingMedia = null
            },
        )
    }

    val reply by MessageStore.reply.collectAsStateWithLifecycle()
    val editing by MessageStore.editing.collectAsStateWithLifecycle()
    val enterToSend by SettingsStore.enterToSend.collectAsStateWithLifecycle()
    var showPanel by remember { mutableStateOf(false) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val inputFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    // Открытие панели прячет клавиатуру — как в TG, снизу одно общее пространство.
    LaunchedEffect(showPanel) { if (showPanel) keyboard?.hide() }

    // Вход в режим редактирования — подставляем текст сообщения в поле.
    LaunchedEffect(editing) {
        editing?.let { text = it.text }
    }

    fun submit() {
        if (text.isBlank()) return
        if (editing != null) {
            MessageStore.submitEdit(text)
        } else {
            MessageStore.sendText(text)
        }
        text = ""
    }

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Поднимаем поле ввода над клавиатурой (когда открыта) и над полосой
                // системной навигации (когда закрыта) — берётся максимум из двух отступов.
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        ) {
            if (editing != null) {
                EditBar(editing!!.text, onCancel = { MessageStore.clearEdit(); text = "" })
            }
            if (reply != null) {
                ReplyBar(reply!!)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // Капсула: скрепка + поле + стикеры (Fork Design Spec §4.3).
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    IconButton(
                        onClick = {
                            pickMedia.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                            )
                        },
                        modifier = Modifier.size(52.dp),
                    ) {
                        Icon(
                            ForkIcons.Attach,
                            contentDescription = "вложение",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(top = 14.dp, bottom = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                "Сообщение",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                imeAction = if (enterToSend) ImeAction.Send else ImeAction.Default,
                            ),
                            keyboardActions = KeyboardActions(onSend = { submit() }),
                            maxLines = 5,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(inputFocus)
                                .onFocusChanged {
                                    // Тап по полю → показать клавиатуру вместо панели.
                                    if (it.isFocused) showPanel = false
                                    onFocusChanged(it.isFocused)
                                },
                        )
                    }
                    IconButton(
                        onClick = {
                            if (showPanel) {
                                // Панель открыта → вернуть клавиатуру.
                                showPanel = false
                                inputFocus.requestFocus()
                                keyboard?.show()
                            } else {
                                showPanel = true
                            }
                        },
                        modifier = Modifier.size(52.dp),
                    ) {
                        Icon(
                            if (showPanel) ForkIcons.Keyboard else ForkIcons.Smile,
                            contentDescription = if (showPanel) "клавиатура" else "эмодзи и стикеры",
                            tint = if (showPanel) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                SendMicButton(
                    hasText = text.isNotBlank(),
                    onSend = { submit() },
                    onSendSilent = {
                        if (editing == null && text.isNotBlank()) {
                            MessageStore.sendText(text, silent = true)
                            text = ""
                        } else {
                            submit()
                        }
                    },
                    onSendScheduled = { atUnix ->
                        if (editing == null && text.isNotBlank()) {
                            MessageStore.sendText(text, scheduleAtUnix = atUnix)
                            text = ""
                        }
                    },
                    onSendWhenOnline = {
                        if (editing == null && text.isNotBlank()) {
                            MessageStore.sendText(text, sendWhenOnline = true)
                            text = ""
                        }
                    },
                    context = context,
                )
            }
            if (showPanel) {
                // Back closes the panel instead of leaving the chat.
                BackHandler { showPanel = false }
                app.fork.messenger.media.ContentPanel(
                    onSticker = { MessageStore.sendSticker(it) },
                    onGif = { MessageStore.sendGif(it) },
                    onEmoji = { emoji -> text += emoji },
                    onBackspace = { text = dropLastGrapheme(text) },
                )
            }
        }
    }
}

/**
 * Круглая кнопка 52dp: микрофон ⇄ отправка с пружинным морфом (Fork Design Spec §7.4).
 * Удержание микрофона — запись голосового, кнопка растёт ×1.6.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SendMicButton(
    hasText: Boolean,
    onSend: () -> Unit,
    onSendSilent: () -> Unit = onSend,
    onSendScheduled: (Int) -> Unit = {},
    onSendWhenOnline: () -> Unit = {},
    context: android.content.Context,
) {
    val tokens = forkTokens
    var sendMenu by remember { mutableStateOf(false) }
    val recording by app.fork.messenger.media.VoiceRecorder.recording.collectAsStateWithLifecycle()
    var hasPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    // Пульс при морфе микрофон ⇄ отправка.
    val morphScale = remember { Animatable(1f) }
    LaunchedEffect(hasText) {
        morphScale.snapTo(0.88f)
        morphScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
    }
    // Рост кнопки при записи голосового.
    val recordScale by animateFloatAsState(
        targetValue = if (recording) 1.6f else 1f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "recordScale",
    )

    Box(
        modifier = Modifier
            .size(52.dp)
            .graphicsLayer {
                val s = morphScale.value * recordScale
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .then(
                when {
                    recording -> Modifier.background(MaterialTheme.colorScheme.error)
                    hasText -> Modifier.background(tokens.brandGradient)
                    else -> Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                },
            )
            .then(
                if (hasText) {
                    Modifier.combinedClickable(onClick = onSend, onLongClick = { sendMenu = true })
                } else {
                    Modifier.pointerInput(hasPermission) {
                        detectTapGestures(
                            onLongPress = {},
                            onPress = {
                                if (!hasPermission) {
                                    askPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                                    return@detectTapGestures
                                }
                                val started = app.fork.messenger.media.VoiceRecorder.start(context)
                                if (started) {
                                    tryAwaitRelease()
                                    app.fork.messenger.media.VoiceRecorder.stopAndSend()
                                }
                            },
                        )
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = hasText || recording, animationSpec = tween(120), label = "micSend") { active ->
            if (active && hasText) {
                Icon(
                    ForkIcons.Send,
                    contentDescription = "отправить",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Icon(
                    ForkIcons.Mic,
                    contentDescription = "записать голосовое",
                    tint = if (recording) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // Долгий тап по «отправить»: без звука / по расписанию.
        androidx.compose.material3.DropdownMenu(expanded = sendMenu, onDismissRequest = { sendMenu = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Отправить без звука") },
                onClick = { sendMenu = false; onSendSilent() },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Когда появится в сети") },
                onClick = { sendMenu = false; onSendWhenOnline() },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Отправить через час") },
                onClick = {
                    sendMenu = false
                    onSendScheduled((System.currentTimeMillis() / 1000 + 3600).toInt())
                },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Завтра в 9:00") },
                onClick = {
                    sendMenu = false
                    val cal = java.util.Calendar.getInstance().apply {
                        add(java.util.Calendar.DAY_OF_YEAR, 1)
                        set(java.util.Calendar.HOUR_OF_DAY, 9)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                    }
                    onSendScheduled((cal.timeInMillis / 1000).toInt())
                },
            )
        }
    }
}

/** Выбранное из галереи медиа, ждёт подтверждения отправки. */
data class PendingMedia(val path: String, val isVideo: Boolean, val width: Int, val height: Int, val duration: Int)

/** Копирует выбранный URI в кэш и измеряет размеры (для одиночной отправки и альбома). */
private fun buildPendingMedia(context: android.content.Context, uri: android.net.Uri): PendingMedia? =
    if (MediaSend.isVideo(context, uri)) {
        MediaSend.copyToCache(context, uri, "mp4")?.let { file ->
            val info = MediaSend.videoInfo(file.absolutePath)
            PendingMedia(file.absolutePath, true, info.width, info.height, info.durationSeconds)
        }
    } else {
        MediaSend.copyToCache(context, uri, "jpg")?.let { file ->
            val (w, h) = MediaSend.imageSize(file.absolutePath)
            PendingMedia(file.absolutePath, false, w, h, 0)
        }
    }

/** Предпросмотр альбома (несколько фото/видео): сетка превью + подпись + Отправить. */
@Composable
private fun AlbumPreviewDialog(
    items: List<PendingMedia>,
    onCancel: () -> Unit,
    onSend: (String) -> Unit,
) {
    var caption by rememberSaveable { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Отправить ${items.size}") },
        text = {
            Column {
                items.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        row.forEach { m ->
                            coil.compose.AsyncImage(
                                model = java.io.File(m.path),
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    placeholder = { Text("Подпись…") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onSend(caption) }) { Text("Отправить") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onCancel) { Text("Отмена") }
        },
    )
}

/** Полноэкранный предпросмотр перед отправкой: медиа + подпись + Отправить. */
@Composable
private fun MediaPreviewDialog(media: PendingMedia, onCancel: () -> Unit, onSend: (String) -> Unit) {
    var caption by rememberSaveable { mutableStateOf("") }
    val tokens = forkTokens
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onCancel,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // Само медиа по центру
            if (media.isVideo) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        ForkIcons.Play,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Видео · ${app.fork.messenger.media.formatDuration(media.duration)}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                coil.compose.AsyncImage(
                    model = java.io.File(media.path),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(bottom = 88.dp),
                )
            }

            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(4.dp),
            ) {
                Icon(ForkIcons.Close, contentDescription = "отмена", tint = Color.White)
            }

            // Подпись + отправить
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .padding(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (caption.isEmpty()) {
                        Text(
                            "Добавить подпись…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                    BasicTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                        cursorBrush = SolidColor(Color.White),
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(tokens.brandGradient)
                        .clickable { onSend(caption) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        ForkIcons.Send,
                        contentDescription = "отправить",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/** Панель «редактирую сообщение» над полем ввода. */
@Composable
private fun EditBar(preview: String, onCancel: () -> Unit) {
    val tokens = forkTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            ForkIcons.Edit,
            contentDescription = null,
            tint = tokens.checkCyan,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Редактирование",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.checkCyan,
            )
            Text(
                preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onCancel) {
            Icon(
                ForkIcons.Close,
                contentDescription = "отменить редактирование",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Панель «отвечаю на …» над полем ввода. */
@Composable
private fun ReplyBar(reply: ReplyDraft) {
    val tokens = forkTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tokens.checkCyan),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Ответ ${reply.sender?.let { "· $it" } ?: ""}",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.checkCyan,
            )
            Text(
                reply.preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { MessageStore.clearReply() }) {
            Icon(
                ForkIcons.Close,
                contentDescription = "отменить ответ",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
