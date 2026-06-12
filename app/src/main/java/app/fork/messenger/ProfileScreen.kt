package app.fork.messenger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fork.messenger.ui.ForkAvatar
import app.fork.messenger.ui.ForkIcons
import app.fork.messenger.ui.forkTokens
import org.drinkless.tdlib.TdApi

/** Вкладка «Профиль» (как в TG): аватар, имя, статус, телефон/username/био, ссылки. */
@Composable
fun ProfileScreen(onOpenChat: (Long) -> Unit, onOpenSettings: () -> Unit) {
    var user by remember { mutableStateOf<TdApi.User?>(null) }
    var bio by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        TdClient.send(TdApi.GetMe()) { res ->
            (res as? TdApi.User)?.let { u ->
                user = u
                TdClient.send(TdApi.GetUserFullInfo(u.id)) { fi ->
                    (fi as? TdApi.UserFullInfo)?.bio?.text?.let { bio = it }
                }
            }
        }
    }

    val u = user
    val name = u?.let {
        listOf(it.firstName, it.lastName).filter { p -> p.isNotBlank() }.joinToString(" ")
    }?.ifBlank { "Профиль" } ?: "Профиль"
    val online = u?.status is TdApi.UserStatusOnline
    val phone = u?.phoneNumber?.let { if (it.startsWith("+")) it else "+$it" }
    val username = u?.usernames?.activeUsernames?.firstOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        ForkAvatar(
            size = 104.dp,
            avatarPath = null,
            initials = MessageFormat.initials(name),
            seed = (u?.id ?: name.hashCode().toLong()),
        )
        Spacer(Modifier.height(14.dp))
        Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            if (online) "в сети" else "был(а) недавно",
            style = MaterialTheme.typography.bodyMedium,
            color = if (online) forkTokens.checkCyan else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(22.dp))

        // Информация
        ProfileCard {
            if (phone != null) InfoRow("Телефон", phone)
            if (username != null) {
                if (phone != null) Divider()
                InfoRow("Имя пользователя", "@$username")
            }
            if (bio.isNotBlank()) {
                if (phone != null || username != null) Divider()
                InfoRow("О себе", bio)
            }
            if (phone == null && username == null && bio.isBlank()) {
                InfoRow("Профиль", "Загрузка…")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Действия
        ProfileCard {
            ActionRow(ForkIcons.Archive, "Избранное") {
                ContactsStore.openSavedMessages(onOpenChat)
            }
            Divider()
            ActionRow(ForkIcons.Settings, "Настройки", onClick = onOpenSettings)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Fork ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProfileCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(start = 0.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    )
}
