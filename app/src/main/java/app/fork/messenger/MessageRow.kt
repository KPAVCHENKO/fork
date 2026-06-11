package app.fork.messenger

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var menuOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val triggerPx = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
    val maxPx = with(androidx.compose.ui.platform.LocalDensity.current) { 96.dp.toPx() }

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
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX.value > triggerPx) MessageStore.startReply(message.id)
                            scope.launch { offsetX.animateTo(0f) }
                        },
                    ) { change, drag ->
                        change.consume()
                        val next = (offsetX.value + drag).coerceIn(0f, maxPx)
                        scope.launch { offsetX.snapTo(next) }
                    }
                }
                .combinedClickable(
                    onClick = {},
                    onLongClick = { menuOpen = true },
                ),
        ) {
            MessageBubble(message, onOpenMedia)

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Ответить") },
                    onClick = { MessageStore.startReply(message.id); menuOpen = false },
                )
                if (message.text.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text("Копировать") },
                        onClick = {
                            clipboard.setText(AnnotatedString(message.text))
                            menuOpen = false
                        },
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
}
