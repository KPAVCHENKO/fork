package app.fork.messenger

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape



import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import app.fork.messenger.ui.senderColor
import kotlinx.coroutines.flow.distinctUntilChanged
import org.drinkless.tdlib.TdApi

/** Экран переписки. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatId: Long, onBack: () -> Unit) {
    val messages by MessageStore.messages.collectAsStateWithLifecycle()
    val title by MessageStore.title.collectAsStateWithLifecycle()
    val loading by MessageStore.loadingHistory.collectAsStateWithLifecycle()

    DisposableEffect(chatId) {
        MessageStore.open(chatId)
        onDispose { MessageStore.close() }
    }
    BackHandler(onBack = onBack)

    var mediaTarget by remember { mutableStateOf<MediaTarget?>(null) }
    val listState = rememberLazyListState()
    val reversed = messages.asReversed()

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
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = { MessageInput() },
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
                    MessageBubble(message, onOpenMedia = { mediaTarget = it })
                }
                if (loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }

    mediaTarget?.let { target ->
        MediaViewer(target = target, onClose = { mediaTarget = null })
    }
}

@Composable
private fun MessageBubble(message: UiMessage, onOpenMedia: (MediaTarget) -> Unit) {
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

                BubbleMedia(content, onOpenMedia)

                val caption = captionText(content)
                BubbleText(
                    text = if (caption != null) caption else message.text,
                    time = message.time,
                    hasMediaAbove = content !is TdApi.MessageText,
                    showText = content is TdApi.MessageText || caption != null,
                )
            }
        }
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
private fun BubbleText(text: String, time: String, hasMediaAbove: Boolean, showText: Boolean) {
    if (!showText) {
        // Только время в углу медиа без подписи.
        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
            Spacer(Modifier.weight(1f))
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
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
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
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

    // Выбор фото из галереи (системный пикер, без доступа ко всей галерее).
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val file = MediaSend.copyToCache(context, uri, "jpg")
            if (file != null) {
                val (w, h) = MediaSend.imageSize(file.absolutePath)
                MessageStore.sendPhoto(file.absolutePath, w, h)
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Поднимаем поле ввода над клавиатурой (когда открыта) и над полосой
                // системной навигации (когда закрыта) — берётся максимум из двух отступов.
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(
                onClick = {
                    pickPhoto.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
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
                maxLines = 5,
            )
            Spacer(Modifier.padding(start = 6.dp))
            FilledIconButton(
                onClick = {
                    MessageStore.sendText(text)
                    text = ""
                },
                enabled = text.isNotBlank(),
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
