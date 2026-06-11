package app.fork.messenger

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fork.messenger.media.inlineSize
import app.fork.messenger.ui.avatarBrush
import coil.compose.AsyncImage
import java.io.File
import org.drinkless.tdlib.TdApi

/** Профиль чата или пользователя: аватар, имя, @имя, телефон, статус, без звука. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoScreen(chatId: Long, onBack: () -> Unit, onOpenChat: (Long) -> Unit = {}) {
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
    var mediaTarget by remember { mutableStateOf<app.fork.messenger.media.MediaTarget?>(null) }

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

            MembersSection(chat = chat, onOpenChat = onOpenChat)

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            SharedMediaSection(chatId = chatId, onOpenMedia = { mediaTarget = it })

            Spacer(Modifier.height(24.dp))
        }
    }

    mediaTarget?.let { target ->
        app.fork.messenger.media.MediaViewer(target = target, onClose = { mediaTarget = null })
    }
}

/** Участники группы (для каналов список обычно скрыт сервером — тогда секции нет). */
@Composable
private fun MembersSection(chat: TdApi.Chat, onOpenChat: (Long) -> Unit) {
    var memberIds by remember(chat.id) { mutableStateOf<List<Long>>(emptyList()) }
    val revision by ChatStore.revision.collectAsStateWithLifecycle()

    LaunchedEffect(chat.id) {
        when (val type = chat.type) {
            is TdApi.ChatTypeBasicGroup ->
                TdClient.send(TdApi.GetBasicGroupFullInfo(type.basicGroupId)) { result ->
                    if (result is TdApi.BasicGroupFullInfo) {
                        memberIds = result.members.orEmpty()
                            .mapNotNull { (it.memberId as? TdApi.MessageSenderUser)?.userId }
                    }
                }
            is TdApi.ChatTypeSupergroup ->
                TdClient.send(TdApi.GetSupergroupMembers(type.supergroupId, null, 0, 200)) { result ->
                    if (result is TdApi.ChatMembers) {
                        memberIds = result.members.orEmpty()
                            .mapNotNull { (it.memberId as? TdApi.MessageSenderUser)?.userId }
                    }
                }
            else -> Unit
        }
    }

    if (memberIds.isEmpty()) return

    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Text(
        "Участники: ${memberIds.size}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
    // revision: имена достраиваются по мере ответов GetUser.
    remember(revision) { revision }
    Column(Modifier.fillMaxWidth()) {
        memberIds.take(200).forEach { userId ->
            val user = UserCache.user(userId)
            val name = if (user != null) {
                listOf(user.firstName, user.lastName).filter { it.isNotBlank() }
                    .joinToString(" ").ifBlank { "Без имени" }
            } else {
                UserCache.firstName(userId) // дозапросит пользователя
                "…"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { ContactsStore.openChat(userId) { onOpenChat(it) } }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                app.fork.messenger.ui.ForkAvatar(
                    size = 42.dp,
                    avatarPath = null,
                    initials = MessageFormat.initials(name),
                    seed = userId,
                    online = UserCache.isOnline(userId),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        UserCache.statusText(userId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Вложения чата: вкладки Медиа / Файлы / Ссылки / Голосовые (как в Telegram). */
@Composable
private fun SharedMediaSection(
    chatId: Long,
    onOpenMedia: (app.fork.messenger.media.MediaTarget) -> Unit,
) {
    val tokens = app.fork.messenger.ui.forkTokens
    var tab by remember(chatId) { mutableStateOf(SharedMediaStore.Tab.MEDIA) }
    val tabs by SharedMediaStore.tabs.collectAsStateWithLifecycle()
    val state = tabs[tab] ?: SharedMediaStore.TabState()

    LaunchedEffect(chatId) { SharedMediaStore.open(chatId) }
    LaunchedEffect(chatId, tab) { SharedMediaStore.load(tab) }

    // Чипы вкладок в дизайн-языке Fork.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SharedMediaStore.Tab.entries.forEach { t ->
            val active = t == tab
            Box(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (active) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .clickable { tab = t }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (state.messages.isEmpty() && !state.loading) {
        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            Text(
                "Пока пусто",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        when (tab) {
            SharedMediaStore.Tab.MEDIA -> MediaGrid(state.messages, onOpenMedia)
            SharedMediaStore.Tab.FILES -> FilesList(state.messages)
            SharedMediaStore.Tab.LINKS -> LinksList(state.messages)
            SharedMediaStore.Tab.VOICE -> VoiceList(state.messages)
        }
    }

    if (state.loading) {
        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = tokens.checkCyan,
            )
        }
    } else if (!state.ended && state.messages.isNotEmpty()) {
        Text(
            "Показать ещё",
            style = MaterialTheme.typography.labelLarge,
            color = tokens.checkCyan,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .clickable { SharedMediaStore.load(tab) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** Сетка медиа 3 в ряд, квадратные ячейки. */
@Composable
private fun MediaGrid(
    messages: List<TdApi.Message>,
    onOpenMedia: (app.fork.messenger.media.MediaTarget) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        messages.chunked(3).forEach { rowItems ->
            Row(Modifier.fillMaxWidth()) {
                rowItems.forEach { msg ->
                    Box(Modifier.weight(1f).padding(1.dp)) {
                        MediaCell(msg, onOpenMedia)
                    }
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Квадратная ячейка фото/видео в сетке вложений. */
@Composable
private fun MediaCell(
    message: TdApi.Message,
    onOpenMedia: (app.fork.messenger.media.MediaTarget) -> Unit,
) {
    when (val content = message.content) {
        is TdApi.MessagePhoto -> {
            val photo = content.photo
            val size = photo.inlineSize() ?: return
            SquareThumb(
                file = size.photo,
                mini = photo.minithumbnail,
                onClick = { onOpenMedia(app.fork.messenger.media.MediaTarget.Photo(photo)) },
            )
        }
        is TdApi.MessageVideo -> {
            val video = content.video
            Box {
                SquareThumb(
                    file = video.thumbnail?.file,
                    mini = video.minithumbnail,
                    onClick = { onOpenMedia(app.fork.messenger.media.MediaTarget.Video(video)) },
                )
                Text(
                    app.fork.messenger.media.formatDuration(video.duration),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color(0x8C050912))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        else -> Unit
    }
}

/** Квадратный тамбнейл: мини-превью сразу, полный файл — по мере загрузки. */
@Composable
private fun SquareThumb(file: TdApi.File?, mini: TdApi.Minithumbnail?, onClick: () -> Unit) {
    val state = app.fork.messenger.media.rememberFileState(file, autoDownload = true, priority = 18)
    val miniBitmap = app.fork.messenger.media.rememberMiniThumb(mini)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        if (miniBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = miniBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        state.path?.let { path ->
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** Список файлов. */
@Composable
private fun FilesList(messages: List<TdApi.Message>) {
    Column(Modifier.fillMaxWidth()) {
        messages.forEach { msg ->
            val doc = (msg.content as? TdApi.MessageDocument)?.document ?: return@forEach
            Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                app.fork.messenger.media.DocumentContent(doc, mine = false)
            }
        }
    }
}

/** Список ссылок: домен + заголовок превью (если есть). */
@Composable
private fun LinksList(messages: List<TdApi.Message>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        messages.forEach { msg ->
            val text = msg.content as? TdApi.MessageText ?: return@forEach
            val url = text.linkPreview?.url
                ?: firstUrl(text.text)
                ?: return@forEach
            val title = text.linkPreview?.title?.takeIf { it.isNotBlank() }
                ?: text.linkPreview?.siteName?.takeIf { it.isNotBlank() }
                ?: url
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(url),
                                ),
                            )
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    color = app.fork.messenger.ui.forkTokens.checkCyan,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Первая ссылка из текста сообщения (по entity TDLib). */
private fun firstUrl(text: TdApi.FormattedText): String? {
    val entity = text.entities?.firstOrNull {
        it.type is TdApi.TextEntityTypeUrl || it.type is TdApi.TextEntityTypeTextUrl
    } ?: return null
    return when (val type = entity.type) {
        is TdApi.TextEntityTypeTextUrl -> type.url
        else -> text.text.substring(entity.offset, entity.offset + entity.length)
    }
}

/** Список голосовых. */
@Composable
private fun VoiceList(messages: List<TdApi.Message>) {
    Column(Modifier.fillMaxWidth()) {
        messages.forEach { msg ->
            val voice = (msg.content as? TdApi.MessageVoiceNote)?.voiceNote ?: return@forEach
            Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                app.fork.messenger.media.VoiceContent(voice, mine = false)
            }
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
