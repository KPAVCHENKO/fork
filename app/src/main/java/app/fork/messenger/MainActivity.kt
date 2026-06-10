package app.fork.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
}

/** Простая навигация: список чатов <-> открытый чат <-> настройки. */
@Composable
private fun MainNavigation() {
    var openChatId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

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
