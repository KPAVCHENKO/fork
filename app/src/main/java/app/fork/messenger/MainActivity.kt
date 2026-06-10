package app.fork.messenger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fork.messenger.notify.NotificationsCenter
import app.fork.messenger.service.ConnectionService
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
        ConnectionService.start(this)
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

/** Простая навигация: список чатов <-> открытый чат <-> настройки. */
@Composable
private fun MainNavigation() {
    var openChatId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // Открытие чата по тапу на уведомление.
    val pending by Navigator.pendingChat.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let {
            openChatId = it
            showSettings = false
            Navigator.consume()
        }
    }

    val chatId = openChatId
    when {
        showSettings -> SettingsScreen(onBack = { showSettings = false })
        chatId != null -> ChatScreen(chatId = chatId, onBack = { openChatId = null })
        else -> ChatListScreen(
            onChatClick = { openChatId = it },
            onSettings = { showSettings = true },
        )
    }
}
