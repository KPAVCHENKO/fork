package app.fork.messenger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fork.messenger.ui.ChatWallpaper
import app.fork.messenger.ui.ChatWallpaperCanvas
import app.fork.messenger.ui.ForkIcons
import app.fork.messenger.ui.forkTokens

/**
 * Шторка «Фон чата» (Fork Design Spec §3.8): сетка превью 3×2, затемнение,
 * «Применить» (на чат) и «Для всех чатов». chatId == null — настройка по умолчанию.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperSheet(chatId: Long?, onDismiss: () -> Unit) {
    val tokens = forkTokens
    val amoled by SettingsStore.amoled.collectAsStateWithLifecycle()
    val currentId = remember(chatId) {
        if (chatId != null) SettingsStore.wallpaperFor(chatId) else SettingsStore.defaultWallpaper.value
    }
    var selected by remember { mutableStateOf(ChatWallpaper.byId(currentId)) }
    var dim by remember { mutableFloatStateOf(SettingsStore.wallpaperDim.value) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Фон чата", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))

            ChatWallpaper.entries.chunked(3).forEach { rowItems ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowItems.forEach { wp ->
                        WallpaperThumb(
                            wallpaper = wp,
                            dark = tokens.dark,
                            amoled = amoled,
                            active = wp == selected,
                            onClick = { selected = wp },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Затемнение",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(dim * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = dim,
                onValueChange = { dim = it },
                valueRange = 0f..0.6f,
                colors = SliderDefaults.colors(
                    thumbColor = tokens.checkCyan,
                    activeTrackColor = tokens.checkCyan,
                ),
            )

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(tokens.brandGradient)
                    .clickable {
                        SettingsStore.setWallpaperDim(dim)
                        if (chatId != null) {
                            SettingsStore.setChatWallpaper(chatId, selected.id)
                        } else {
                            SettingsStore.setDefaultWallpaper(selected.id)
                        }
                        onDismiss()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("Применить", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }

            if (chatId != null) {
                Text(
                    "Для всех чатов",
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.checkCyan,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable {
                            SettingsStore.setWallpaperDim(dim)
                            SettingsStore.setDefaultWallpaper(selected.id)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Превью обоев 104×140, активные — контур 2dp циан + чек-бейдж (§3.8). */
@Composable
private fun WallpaperThumb(
    wallpaper: ChatWallpaper,
    dark: Boolean,
    amoled: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = forkTokens
    val shape = RoundedCornerShape(16.dp)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(shape)
                .then(if (active) Modifier.border(2.dp, tokens.checkCyan, shape) else Modifier)
                .clickable(onClick = onClick),
        ) {
            ChatWallpaperCanvas(
                wallpaper = wallpaper,
                dark = dark,
                amoled = amoled,
                dim = 0f,
                modifier = Modifier.matchParentSize(),
            )
            if (active) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(tokens.checkCyan),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        ForkIcons.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            wallpaper.title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
