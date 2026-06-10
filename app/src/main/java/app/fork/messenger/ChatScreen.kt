package app.fork.messenger

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fork.messenger.ui.senderColor
import kotlinx.coroutines.flow.distinctUntilChanged

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
                    MessageBubble(message)
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
}

@Composable
private fun MessageBubble(message: UiMessage) {
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
            Column(Modifier.widthIn(max = 300.dp).padding(horizontal = 12.dp, vertical = 7.dp)) {
                if (message.showSender && message.senderName != null) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = senderColor(message.senderSeed),
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.padding(start = 8.dp))
                    Text(
                        text = message.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageInput() {
    var text by rememberSaveable { mutableStateOf("") }

    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
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
