package app.fork.messenger

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.fork.messenger.media.MediaTarget
import app.fork.messenger.ui.ForkIcons
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Обёртка над пузырём сообщения: свайп вправо для ответа + долгое нажатие для меню
 * (ответить / копировать / удалить). Жесты сделаны лёгкими, чтобы список не лагал.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageRow(
    message: UiMessage,
    onOpenMedia: (MediaTarget) -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onOpenStickerSet: (Long) -> Unit = {},
    animateStickers: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var menuOpen by remember { mutableStateOf(false) }
    var reactionPicker by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    // Режим выбора: тап и долгое нажатие переключают отметку, меню не открывается.
    if (selectionMode) {
        Box(
            modifier = Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .background(
                    if (selected) app.fork.messenger.ui.forkTokens.checkCyan.copy(alpha = 0.14f)
                    else androidx.compose.ui.graphics.Color.Transparent,
                )
                .combinedClickable(
                    onClick = { onToggleSelect?.invoke() },
                    onLongClick = { onToggleSelect?.invoke() },
                ),
        ) {
            MessageBubble(message, onOpenMedia, onOpenStickerSet, animateStickers)
        }
        return
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val triggerPx = with(density) { 56.dp.toPx() }
    val maxPx = with(density) { 88.dp.toPx() }
    // Мёртвая зона: случайные горизонтальные движения не дёргают свайп-ответ.
    val deadZonePx = with(density) { 30.dp.toPx() }

    Box {
        // Иконка ответа в круге проявляется и растёт по мере свайпа (Fork Design Spec §6).
        val swipeProgress = (offsetX.value / triggerPx).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .size(32.dp)
                .graphicsLayer {
                    alpha = swipeProgress
                    scaleX = 0.5f + 0.5f * swipeProgress
                    scaleY = 0.5f + 0.5f * swipeProgress
                }
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ForkIcons.Reply,
                contentDescription = null,
                tint = app.fork.messenger.ui.forkTokens.checkCyan,
                modifier = Modifier.size(18.dp),
            )
        }

        Box(
            modifier = Modifier
                .graphicsLayer { translationX = offsetX.value }
                .pointerInput(message.id) {
                    var raw = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { raw = 0f },
                        onDragCancel = { scope.launch { offsetX.animateTo(0f) } },
                        onDragEnd = {
                            if (offsetX.value > triggerPx) MessageStore.startReply(message.id)
                            scope.launch { offsetX.animateTo(0f) }
                        },
                    ) { change, drag ->
                        raw += drag
                        // Смещение начинается после мёртвой зоны, с сопротивлением ×0.75.
                        val next = ((raw - deadZonePx).coerceAtLeast(0f) * 0.75f).coerceAtMost(maxPx)
                        if (next > 0f || offsetX.value > 0f) {
                            change.consume()
                            scope.launch { offsetX.snapTo(next) }
                        }
                    }
                }
                .combinedClickable(
                    onClick = {},
                    onLongClick = { menuOpen = true },
                    onDoubleClick = { MessageStore.toggleReaction(message.id, SettingsStore.quickReaction.value) },
                ),
        ) {
            MessageBubble(message, onOpenMedia, onOpenStickerSet, animateStickers)

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                // Быстрый ряд эмодзи-реакций.
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    listOf("👍", "❤️", "🔥", "😁", "😢", "👏").forEach { emoji ->
                        Text(
                            emoji,
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .clickable {
                                    MessageStore.toggleReaction(message.id, emoji)
                                    menuOpen = false
                                }
                                .padding(6.dp),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
                DropdownMenuItem(
                    text = { Text("Ещё реакции…") },
                    onClick = { menuOpen = false; reactionPicker = true },
                )
                DropdownMenuItem(
                    text = { Text("Ответить") },
                    onClick = { MessageStore.startReply(message.id); menuOpen = false },
                )
                DropdownMenuItem(
                    text = { Text("Переслать") },
                    onClick = {
                        ForwardBus.start(MessageStore.currentChatId(), longArrayOf(message.id))
                        menuOpen = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("В избранное") },
                    onClick = { MessageStore.forwardToSaved(message.id); menuOpen = false },
                )
                if (message.content is org.drinkless.tdlib.TdApi.MessageVoiceNote ||
                    message.content is org.drinkless.tdlib.TdApi.MessageVideoNote
                ) {
                    DropdownMenuItem(
                        text = { Text("Расшифровать") },
                        onClick = { MessageStore.recognizeSpeech(message.id); menuOpen = false },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Копировать ссылку") },
                    onClick = {
                        MessageStore.messageLink(message.id) { link ->
                            link?.let { l -> scope.launch { clipboard.setText(AnnotatedString(l)) } }
                        }
                        menuOpen = false
                    },
                )
                (message.content as? org.drinkless.tdlib.TdApi.MessageSticker)?.let { st ->
                    DropdownMenuItem(
                        text = { Text("В избранные стикеры") },
                        onClick = { MessageStore.favoriteSticker(st.sticker); menuOpen = false },
                    )
                }
                (message.content as? org.drinkless.tdlib.TdApi.MessageAnimation)?.let { anim ->
                    DropdownMenuItem(
                        text = { Text("Сохранить GIF") },
                        onClick = { MessageStore.saveAnimation(anim.animation); menuOpen = false },
                    )
                }
                if (message.isMine && message.text.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text("Изменить") },
                        onClick = { MessageStore.startEdit(message.id); menuOpen = false },
                    )
                }
                if (message.text.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text("Копировать") },
                        onClick = {
                            clipboard.setText(AnnotatedString(message.text))
                            menuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Перевести") },
                        onClick = { MessageStore.translateMessage(message.id); menuOpen = false },
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (message.isPinned) "Открепить" else "Закрепить") },
                    onClick = {
                        MessageStore.togglePinMessage(message.id, !message.isPinned)
                        menuOpen = false
                    },
                )
                if (onToggleSelect != null) {
                    DropdownMenuItem(
                        text = { Text("Выбрать") },
                        onClick = { onToggleSelect(); menuOpen = false },
                    )
                }
                if (message.canDeleteForAll) {
                    DropdownMenuItem(
                        text = { Text("Удалить у всех") },
                        onClick = { MessageStore.deleteMessage(message.id, true); menuOpen = false },
                    )
                }
                if (message.canDelete) {
                    DropdownMenuItem(
                        text = { Text("Удалить у себя") },
                        onClick = { MessageStore.deleteMessage(message.id, false); menuOpen = false },
                    )
                }
            }
        }
    }

    if (reactionPicker) {
        ReactionPickerSheet(
            onPick = { emoji -> MessageStore.toggleReaction(message.id, emoji); reactionPicker = false },
            onDismiss = { reactionPicker = false },
        )
    }
}

/** Полный пикер реакций — сетка популярных эмодзи (как «+» в Telegram). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReactionPickerSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val reactions = remember {
        listOf(
            "👍", "👎", "❤️", "🔥", "🥰", "👏", "😁", "🤔", "🤯", "😱", "🤬", "😢",
            "🎉", "🤩", "🙏", "👌", "🕊", "🤡", "🥱", "🥴", "😍", "🐳", "❤️‍🔥", "🌚",
            "💯", "🤣", "⚡", "🍌", "🏆", "💔", "🤨", "😐", "🍓", "🍾", "💋", "🖕",
            "😈", "😴", "😭", "🤓", "👻", "👀", "🙈", "😇", "🤝", "✍️", "🫡", "🗿",
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .heightIn(max = 320.dp),
        ) {
            items(reactions) { emoji ->
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clickable { onPick(emoji) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 26.sp)
                }
            }
        }
    }
}
