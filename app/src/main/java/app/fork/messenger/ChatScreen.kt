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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fork.messenger.media.AnimationContent
import app.fork.messenger.media.DocumentContent
import app.fork.messenger.media.MediaSend
import app.fork.messenger.media.MediaTarget
import app.fork.messenger.media.MediaViewer
import app.fork.messenger.media.PhotoContent
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatId: Long, onBack: () -> Unit, onOpenInfo: (Long) -> Unit) {
    val messages by MessageStore.messages.collectAsStateWithLifecycle()
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

    BackHandler {
        when {
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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reversed = messages.asReversed()

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
                    MessageInput()
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
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    itemsIndexed(reversed, key = { _, m -> m.id }) { index, message ->
                        val older = reversed.getOrNull(index + 1)
                        Column {
                            if (older == null || older.dateLabel != message.dateLabel) {
                                DateCapsule(message.dateLabel)
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
            MediaViewer(target = target, onClose = { mediaTarget = null })
        }
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
fun MessageBubble(message: UiMessage, onOpenMedia: (MediaTarget) -> Unit) {
    val tokens = forkTokens
    val content = message.content

    // Стикеры и видеокружки показываем без пузыря.
    if (content is TdApi.MessageSticker) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
        ) {
            StickerContent(content.sticker)
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
                    .widthIn(max = 300.dp)
                    .padding(
                        if (isText) PaddingValues(horizontal = 13.dp, vertical = 8.dp)
                        else PaddingValues(4.dp),
                    ),
            ) {
                if (message.showSender && message.senderName != null) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = senderColor(message.senderSeed),
                        modifier = Modifier.padding(
                            horizontal = if (isText) 0.dp else 8.dp,
                            vertical = 1.dp,
                        ),
                    )
                    Spacer(Modifier.height(2.dp))
                }

                if (message.replyText != null) {
                    ReplyQuote(message.replyText, mine = message.isMine)
                    Spacer(Modifier.height(4.dp))
                }

                BubbleMedia(content, message.isMine, onOpenMedia)

                val caption = captionText(content)
                BubbleText(
                    text = if (caption != null) caption else message.text,
                    time = message.time,
                    status = message.outStatus,
                    mine = message.isMine,
                    hasMediaAbove = !isText,
                    showText = isText || caption != null,
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
                    ReactionChips(message.id, message.reactions, mine = message.isMine)
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

/** Картиночная/медийная часть пузыря (если есть). */
@Composable
private fun BubbleMedia(
    content: TdApi.MessageContent?,
    mine: Boolean,
    onOpenMedia: (MediaTarget) -> Unit,
) {
    when (content) {
        is TdApi.MessagePhoto -> PhotoContent(content.photo) { onOpenMedia(MediaTarget.Photo(content.photo)) }
        is TdApi.MessageVideo -> VideoContent(content.video) { onOpenMedia(MediaTarget.Video(content.video)) }
        is TdApi.MessageAnimation -> AnimationContent(content.animation)
        is TdApi.MessageVoiceNote -> VoiceContent(content.voiceNote, mine = mine)
        is TdApi.MessageDocument -> DocumentContent(content.document, mine = mine)
        else -> Unit
    }
}

@Composable
private fun BubbleText(
    text: String,
    time: String,
    status: OutStatus,
    mine: Boolean,
    hasMediaAbove: Boolean,
    showText: Boolean,
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
            horizontal = if (hasMediaAbove) 8.dp else 0.dp,
            vertical = if (hasMediaAbove) 4.dp else 0.dp,
        ),
    ) {
        Text(
            text = text,
            style = MessageTextStyle,
            color = if (mine) Color.White else forkTokens.bubbleTextIn,
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

@Composable
private fun MessageInput() {
    var text by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    // Выбор фото/видео из галереи: сначала показываем предпросмотр с подписью.
    var pendingMedia by remember { mutableStateOf<PendingMedia?>(null) }
    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            pendingMedia = if (MediaSend.isVideo(context, uri)) {
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
        }
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
    var showStickers by remember { mutableStateOf(false) }

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
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    IconButton(
                        onClick = { showStickers = !showStickers },
                        modifier = Modifier.size(52.dp),
                    ) {
                        Icon(
                            ForkIcons.Sticker,
                            contentDescription = "стикеры",
                            tint = if (showStickers) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                SendMicButton(hasText = text.isNotBlank(), onSend = { submit() }, context = context)
            }
            if (showStickers) {
                app.fork.messenger.media.StickerPanel(onPick = { sticker ->
                    MessageStore.sendSticker(sticker)
                })
            }
        }
    }
}

/**
 * Круглая кнопка 52dp: микрофон ⇄ отправка с пружинным морфом (Fork Design Spec §7.4).
 * Удержание микрофона — запись голосового, кнопка растёт ×1.6.
 */
@Composable
private fun SendMicButton(hasText: Boolean, onSend: () -> Unit, context: android.content.Context) {
    val tokens = forkTokens
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
                    Modifier.clickable(onClick = onSend)
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
    }
}

/** Выбранное из галереи медиа, ждёт подтверждения отправки. */
data class PendingMedia(val path: String, val isVideo: Boolean, val width: Int, val height: Int, val duration: Int)

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
