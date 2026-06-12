package app.fork.messenger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fork.messenger.notify.NotificationsCenter
import app.fork.messenger.ui.ForkTheme

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        TdClient.setOnline(true)
    }

    override fun onStop() {
        super.onStop()
        TdClient.setOnline(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        ensureNotificationPermission()
        handleIntent(intent)

        setContent {
            ForkTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val authState by TdClient.authState.collectAsStateWithLifecycle()
                    if (authState == AuthUiState.Ready) {
                        MainNavigation()
                    } else {
                        LoginScreen()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val chatId = intent?.getLongExtra(NotificationsCenter.EXTRA_CHAT_ID, 0L) ?: 0L
        if (chatId != 0L) Navigator.requestOpenChat(chatId)
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
    }
}

/** Навигация: список ↔ чат ↔ профиль ↔ настройки ↔ новый чат ↔ пересылка. */
@Composable
private fun MainNavigation() {
    var openChatId by rememberSaveable { mutableStateOf<Long?>(null) }
    var infoChatId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showNewChat by rememberSaveable { mutableStateOf(false) }
    var showArchive by rememberSaveable { mutableStateOf(false) }
    var showProxy by rememberSaveable { mutableStateOf(false) }
    // null — не показывать; false — группа; true — канал.
    var createChannel by rememberSaveable { mutableStateOf<Boolean?>(null) }

    // Открытие чата по тапу на уведомление.
    val pending by Navigator.pendingChat.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let {
            openChatId = it
            infoChatId = null
            showSettings = false
            showNewChat = false
            Navigator.consume()
        }
    }

    // Запрос пересылки из меню сообщения.
    val forward by ForwardBus.request.collectAsStateWithLifecycle()

    val chatId = openChatId
    val info = infoChatId
    when {
        forward != null -> ForwardPickerScreen(
            onBack = { ForwardBus.clear() },
            onPick = { target ->
                forward?.let { MessageStore.forwardMessages(it.fromChatId, target, it.messageIds) }
                ForwardBus.clear()
                openChatId = target
            },
        )
        createChannel != null -> CreateChatScreen(
            isChannel = createChannel == true,
            onBack = { createChannel = null },
            onCreated = { createChannel = null; showNewChat = false; openChatId = it },
        )
        showNewChat -> NewChatScreen(
            onBack = { showNewChat = false },
            onOpenChat = { showNewChat = false; openChatId = it },
            onCreateGroup = { createChannel = false },
            onCreateChannel = { createChannel = true },
        )
        showArchive -> ArchiveScreen(
            onBack = { showArchive = false },
            onOpenChat = { showArchive = false; openChatId = it },
        )
        showProxy -> ProxyScreen(onBack = { showProxy = false })
        showSettings -> SettingsScreen(onBack = { showSettings = false })
        info != null -> ChatInfoScreen(
            chatId = info,
            onBack = { infoChatId = null },
            onOpenChat = { infoChatId = null; openChatId = it },
        )
        chatId != null -> ChatScreen(
            chatId = chatId,
            onBack = { openChatId = null },
            onOpenInfo = { infoChatId = it },
        )
        else -> Box(Modifier.fillMaxSize()) {
            ChatListScreen(
                onChatClick = { openChatId = it },
                onSettings = { showSettings = true },
                onOpenArchive = { showArchive = true },
                onOpenProxy = { showProxy = true },
            )
            NewChatFab(
                onClick = { showNewChat = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(20.dp),
            )
        }
    }
}

/** Круглая градиентная кнопка «новый чат» (Fork Design Spec). */
@Composable
private fun NewChatFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(app.fork.messenger.ui.forkTokens.brandGradient)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            app.fork.messenger.ui.ForkIcons.Edit,
            contentDescription = "новый чат",
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
    }
}
