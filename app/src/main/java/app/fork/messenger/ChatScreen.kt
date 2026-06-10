package app.fork.messenger

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions



import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import app.fork.messenger.SettingsStore
import app.fork.messenger.ui.senderColor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

/** Экран переписки. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatId: Long, onBack: () -> Unit, onOpenInfo: (Long) -> Unit) {
    val messages by MessageStore.messages.collectAsStateWithLifecycle()
    val header by MessageStore.header.collectAsStateWithLifecycle()
    val loading by MessageStore.loadingHistory.collectAsStateWithLifecycle()

    DisposableEffect(chatId) {
        MessageStore.open(chatId)
        onDispose { MessageStore.close() }
    }
    BackHandler(onBack = onBack)

    var mediaTarget by remember { mutableStateOf<MediaTarget?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reversed = messages.asReversed()

    // Автопрокрутка к новому сообщению, если пользователь уже у низа.
    val newestId = reversed.firstOrNull()?.id
    LaunchedEffect(newestId) {
        if (newestId != null && listState.firstVisibleItemIndex <= 2) {
            listState.animateScrollToItem(0)
        }
    }

    // Когда прокрутили к самым старым сообщениям — догружаем историю.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last ->
                val total = MessageStore.messages.value.size
                if (total > 0 && last >= total - 5) MessageStore.loadMore()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(app.fork.messenger.ui.ForkIcons.ArrowBack, contentDescription = "назад")
                    }
                },
                title = {
                    val h = header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onOpenInfo(chatId) },
                    ) {
                        HeaderAvatar(h)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                h?.title ?: "",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (h != null && h.subtitle.isNotBlank()) {
                                Text(
                                    h.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (h.subtitle == "онлайн") MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            if (header?.canWrite != false) {
                MessageInput()
            } else {
                ReadOnlyBar()
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 10.dp, vertical = 8.dp,
                ),
            ) {
                items(reversed, key = { it.id }) { message ->
                    MessageRow(message, onOpenMedia = { mediaTarget = it })
                }
                if (loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
            }

            // Кнопка «вниз» появляется, когда прокрутили вверх.
            val showScrollDown by remember {
                derivedStateOf { listState.firstVisibleItemIndex > 3 }
            }
            if (showScrollDown) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Icon(
                        app.fork.messenger.ui.ForkIcons.Download,
                        contentDescription = "вниз",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    mediaTarget?.let { target ->
        MediaViewer(target = target, onClose = { mediaTarget = null })
    }
}

@Composable
fun MessageBubble(message: UiMessage, onOpenMedia: (MediaTarget) -> Unit) {
    val content = message.content

    // Стикеры показываем без пузыря.
    if (content is TdApi.MessageSticker) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
        ) {
            StickerContent(content.sticker)
        }
        return
    }

    val bubbleColor =
        if (message.isMine) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (message.isMine) 18.dp else 6.dp,
        bottomEnd = if (message.isMine) 6.dp else 18.dp,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (message.isFirstOfGroup) 6.dp else 2.dp),
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start,
    ) {
        Surface(color = bubbleColor, shape = shape) {
            Column(Modifier.widthIn(max = 300.dp).padding(6.dp)) {
                if (message.showSender && message.senderName != null) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = senderColor(message.senderSeed),
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 1.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                }

                if (message.replyText != null) {
                    ReplyQuote(message.replyText)
                    Spacer(Modifier.height(2.dp))
                }

                BubbleMedia(content, onOpenMedia)

                val caption = captionText(content)
                BubbleText(
                    text = if (caption != null) caption else message.text,
                    time = message.time,
                    status = message.outStatus,
                    hasMediaAbove = content !is TdApi.MessageText,
                    showText = content is TdApi.MessageText || caption != null,
                )
            }
        }
    }
}

/** Мини-аватар в шапке чата. */
@Composable
private fun HeaderAvatar(h: ChatHeader?) {
    if (h == null) return
    if (h.avatarPath != null) {
        coil.compose.AsyncImage(
            model = java.io.File(h.avatarPath),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(38.dp).clip(androidx.compose.foundation.shape.CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(app.fork.messenger.ui.avatarBrush(h.colorSeed)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                h.initials,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

/** Плашка вместо ввода там, где писать нельзя (каналы, ограниченные группы). */
@Composable
private fun ReadOnlyBar() {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
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

/** Цитата сообщения, на которое отвечают, внутри пузыря. */
@Composable
private fun ReplyQuote(text: String) {
    Row(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
    ) {
        Box(
            Modifier
                .padding(vertical = 4.dp)
                .width(3.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

/** Картиночная/медийная часть пузыря (если есть). */
@Composable
private fun BubbleMedia(content: TdApi.MessageContent?, onOpenMedia: (MediaTarget) -> Unit) {
    when (content) {
        is TdApi.MessagePhoto -> PhotoContent(content.photo) { onOpenMedia(MediaTarget.Photo(content.photo)) }
        is TdApi.MessageVideo -> VideoContent(content.video) { onOpenMedia(MediaTarget.Video(content.video)) }
        is TdApi.MessageAnimation -> AnimationContent(content.animation)
        is TdApi.MessageVoiceNote -> VoiceContent(content.voiceNote, mine = false)
        is TdApi.MessageDocument -> DocumentContent(content.document)
        else -> Unit
    }
}

@Composable
private fun BubbleText(
    text: String,
    time: String,
    status: app.fork.messenger.OutStatus,
    hasMediaAbove: Boolean,
    showText: Boolean,
) {
    if (!showText) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Spacer(Modifier.weight(1f))
            TimeStatus(time, status)
        }
        return
    }
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = if (hasMediaAbove) 4.dp else 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.padding(start = 8.dp))
        TimeStatus(time, status)
    }
}

/** Время + галочка статуса (для своих сообщений). */
@Composable
private fun TimeStatus(time: String, status: app.fork.messenger.OutStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        val icon = when (status) {
            app.fork.messenger.OutStatus.SENDING -> app.fork.messenger.ui.ForkIcons.Clock
            app.fork.messenger.OutStatus.SENT -> app.fork.messenger.ui.ForkIcons.Check
            app.fork.messenger.OutStatus.READ -> app.fork.messenger.ui.ForkIcons.CheckDouble
            app.fork.messenger.OutStatus.FAILED -> app.fork.messenger.ui.ForkIcons.Clock
            app.fork.messenger.OutStatus.NONE -> null
        }
        if (icon != null) {
            Spacer(Modifier.width(3.dp))
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (status == app.fork.messenger.OutStatus.READ)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
    val enterToSend by SettingsStore.enterToSend.collectAsStateWithLifecycle()

    fun submit() {
        if (text.isNotBlank()) {
            MessageStore.sendText(text)
            text = ""
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Поднимаем поле ввода над клавиатурой (когда открыта) и над полосой
                // системной навигации (когда закрыта) — берётся максимум из двух отступов.
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        ) {
            if (reply != null) {
                ReplyBar(reply!!)
            }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(
                onClick = {
                    pickMedia.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                    )
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    app.fork.messenger.ui.ForkIcons.Attach,
                    contentDescription = "вложение",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Сообщение") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = if (enterToSend) ImeAction.Send else ImeAction.Default,
                ),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                maxLines = 5,
            )
            Spacer(Modifier.padding(start = 6.dp))
            if (text.isNotBlank()) {
                FilledIconButton(
                    onClick = { submit() },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        app.fork.messenger.ui.ForkIcons.Send,
                        contentDescription = "отправить",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            } else {
                VoiceButton(context)
            }
        }
        }
    }
}

/** Кнопка-микрофон: удерживай для записи голосового, отпусти — отправить. */
@Composable
private fun VoiceButton(context: android.content.Context) {
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

    FilledIconButton(
        onClick = {},
        modifier = Modifier
            .size(48.dp)
            .pointerInput(hasPermission) {
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
            },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        ),
    ) {
        Icon(
            app.fork.messenger.ui.ForkIcons.Mic,
            contentDescription = "записать голосовое",
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** Выбранное из галереи медиа, ждёт подтверждения отправки. */
data class PendingMedia(val path: String, val isVideo: Boolean, val width: Int, val height: Int, val duration: Int)

/** Полноэкранный предпросмотр перед отправкой: медиа + подпись + Отправить. */
@Composable
private fun MediaPreviewDialog(media: PendingMedia, onCancel: () -> Unit, onSend: (String) -> Unit) {
    var caption by rememberSaveable { mutableStateOf("") }
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
                        app.fork.messenger.ui.ForkIcons.Play,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Видео · ${app.fork.messenger.media.formatDuration(media.duration)}",
                        color = Color.White,
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
                Icon(app.fork.messenger.ui.ForkIcons.Close, contentDescription = "отмена", tint = Color.White)
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
                TextField(
                    value = caption,
                    onValueChange = { caption = it },
                    placeholder = { Text("Добавить подпись…") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    maxLines = 3,
                )
                Spacer(Modifier.width(6.dp))
                FilledIconButton(
                    onClick = { onSend(caption) },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        app.fork.messenger.ui.ForkIcons.Send,
                        contentDescription = "отправить",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/** Панель «отвечаю на …» над полем ввода. */
@Composable
private fun ReplyBar(reply: app.fork.messenger.ReplyDraft) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Ответ ${reply.sender?.let { "· $it" } ?: ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
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
                app.fork.messenger.ui.ForkIcons.Close,
                contentDescription = "отменить ответ",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
