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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
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
        else -> HomeShell(
            onOpenChat = { openChatId = it },
            onOpenArchive = { showArchive = true },
            onOpenProxy = { showProxy = true },
            onNewChat = { showNewChat = true },
        )
    }
}

/** Корневая оболочка с нижней навигацией (как в TG): Чаты · Контакты · Настройки · Профиль. */
@Composable
private fun HomeShell(
    onOpenChat: (Long) -> Unit,
    onOpenArchive: () -> Unit,
    onOpenProxy: () -> Unit,
    onNewChat: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    // История вкладок: «Назад» ведёт на ПРЕДЫДУЩУЮ вкладку (Профиль→Настройки→назад=Профиль),
    // а не сразу в Чаты.
    val backStack = remember { androidx.compose.runtime.mutableStateListOf<Int>() }
    fun goTab(t: Int) {
        if (t != tab) { backStack.add(tab); tab = t }
    }
    fun popTab() {
        tab = if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) else 0
    }
    androidx.activity.compose.BackHandler(enabled = tab != 0 || backStack.isNotEmpty()) { popTab() }

    // Контент уходит ПОД плавающую панель (она прозрачная/размытая), но скроллится с этим
    // нижним отступом, чтобы последние элементы вставали над панелью, а не прятались.
    val bottomInset = 86.dp
    val hazeState = remember { HazeState() }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
            when (tab) {
                0 -> ChatListScreen(
                    onChatClick = onOpenChat,
                    onSettings = { goTab(2) },
                    onOpenArchive = onOpenArchive,
                    onOpenProxy = onOpenProxy,
                    bottomInset = bottomInset,
                )
                1 -> ContactsScreen(onOpenChat = onOpenChat, bottomInset = bottomInset)
                2 -> SettingsScreen(onBack = { popTab() }, bottomInset = bottomInset)
                3 -> ProfileScreen(onOpenChat = onOpenChat, onOpenSettings = { goTab(2) }, bottomInset = bottomInset)
            }
        }
        if (tab == 0) {
            NewChatFab(
                onClick = onNewChat,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 74.dp),
            )
        }
        ForkBottomBar(tab, hazeState, Modifier.align(Alignment.BottomCenter)) { goTab(it) }
    }
}

/** Плавающая «стеклянная» нижняя навигация (как в TG): скруглённая, ПРОЗРАЧНАЯ
 *  (контент виден за ней), компактная — занимает мало места и парит над контентом. */
@Composable
private fun ForkBottomBar(
    current: Int,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val items = listOf(
        Triple(0, app.fork.messenger.ui.ForkIcons.Chats, "Чаты"),
        Triple(1, app.fork.messenger.ui.ForkIcons.Group, "Контакты"),
        Triple(2, app.fork.messenger.ui.ForkIcons.Settings, "Настройки"),
        Triple(3, app.fork.messenger.ui.ForkIcons.Person, "Профиль"),
    )
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 26.dp, vertical = 7.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp) // на ~15% компактнее (было 58)
                .clip(shape)
                // Матовое стекло: размываем контент за панелью (Haze) + лёгкий тинт. На слабых
                // устройствах (PerfClass.LOW) размытие выключено — вместо него плотнее тинт.
                .then(if (PerfClass.blurEnabled) Modifier.hazeEffect(hazeState) else Modifier)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh
                        .copy(alpha = if (PerfClass.blurEnabled) 0.40f else 0.74f),
                )
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Суммарные непрочитанные для бейджа на «Чатах» (без чатов «без звука»).
            val chats by app.fork.messenger.ChatStore.chatList.collectAsStateWithLifecycle()
            val totalUnread = remember(chats) { chats.filter { !it.isMuted }.sumOf { it.unread } }
            items.forEach { (i, icon, label) ->
                val selected = current == i
                val tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(shape)
                        .clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box {
                        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(21.dp))
                        if (i == 0 && totalUnread > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 10.dp, y = (-4).dp)
                                    .height(14.dp)
                                    .widthIn(min = 14.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (totalUnread > 99) "99+" else "$totalUnread",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(label, color = tint, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                }
            }
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
