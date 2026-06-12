package app.fork.messenger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fork.messenger.ui.ForkAvatar
import app.fork.messenger.ui.ForkIcons
import app.fork.messenger.ui.forkTokens
import org.drinkless.tdlib.TdApi

/** Вкладка «Профиль» (как в TG): аватар, имя, статус, телефон/username/био + действия. */
@Composable
fun ProfileScreen(
    onOpenChat: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    var user by remember { mutableStateOf<TdApi.User?>(null) }
    var bio by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var showEdit by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
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
    // Реальное фото профиля (качается через TDLib, до готовности — инициалы).
    val avatarPath = app.fork.messenger.media
        .rememberFileState(u?.profilePhoto?.small, autoDownload = true, priority = 8).path

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
            avatarPath = avatarPath,
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
            ActionRow(ForkIcons.Edit, "Изменить профиль") { showEdit = true }
            Divider()
            ActionRow(ForkIcons.Archive, "Избранное") {
                ContactsStore.openSavedMessages(onOpenChat)
            }
            Divider()
            ActionRow(ForkIcons.Settings, "Настройки", onClick = onOpenSettings)
        }

        Spacer(Modifier.height(16.dp))

        ProfileCard {
            ActionRow(ForkIcons.Close, "Выйти из аккаунта", danger = true) { showLogout = true }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Fork ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp + bottomInset))
    }

    if (showEdit && u != null) {
        EditProfileDialog(
            firstName = u.firstName,
            lastName = u.lastName,
            bio = bio,
            onDismiss = { showEdit = false },
            onSave = { f, l, b ->
                TdClient.send(TdApi.SetName(f.trim(), l.trim()))
                TdClient.send(TdApi.SetBio(b.trim()))
                showEdit = false
                // Перечитываем профиль с небольшим запасом на применение.
                refresh++
            },
        )
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("Выйти из аккаунта?") },
            text = { Text("Локальные данные на этом устройстве будут удалены. Войти обратно можно по номеру телефона.") },
            confirmButton = {
                TextButton(onClick = { showLogout = false; TdClient.logOut() }) {
                    Text("Выйти", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun EditProfileDialog(
    firstName: String,
    lastName: String,
    bio: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var f by remember { mutableStateOf(firstName) }
    var l by remember { mutableStateOf(lastName) }
    var b by remember { mutableStateOf(bio) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить профиль") },
        text = {
            Column {
                OutlinedTextField(value = f, onValueChange = { f = it }, label = { Text("Имя") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = l, onValueChange = { l = it }, label = { Text("Фамилия") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = b, onValueChange = { b = it }, label = { Text("О себе") }, maxLines = 3)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(f, l, b) }, enabled = f.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
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
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    )
}
