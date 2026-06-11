package app.fork.messenger.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.fork.messenger.TdClient
import app.fork.messenger.ui.forkTokens
import org.drinkless.tdlib.TdApi

/**
 * Шторка стикерпака (тап по стикеру в чате): название, сетка стикеров,
 * установка/удаление набора; тап по стикеру — отправить.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerSetSheet(
    setId: Long,
    onDismiss: () -> Unit,
    onPick: (TdApi.Sticker) -> Unit,
) {
    val tokens = forkTokens
    var set by remember(setId) { mutableStateOf<TdApi.StickerSet?>(null) }
    var installed by remember(setId) { mutableStateOf(false) }

    LaunchedEffect(setId) {
        TdClient.send(TdApi.GetStickerSet(setId)) { result ->
            if (result is TdApi.StickerSet) {
                set = result
                installed = result.isInstalled
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        val current = set
        if (current == null) {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@ModalBottomSheet
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                current.title.ifBlank { "Стикеры" },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "Стикеров: ${current.stickers.orEmpty().size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
            ) {
                items(current.stickers.orEmpty().filterNotNull(), key = { it.sticker.id }) { sticker ->
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onPick(sticker)
                                onDismiss()
                            }
                            .padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        StickerThumb(sticker, Modifier.fillMaxWidth())
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .then(
                        if (installed) {
                            Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        } else {
                            Modifier.background(tokens.brandGradient)
                        },
                    )
                    .clickable {
                        val target = !installed
                        installed = target
                        TdClient.send(TdApi.ChangeStickerSet(setId, target, false))
                        app.fork.messenger.media.StickerStore.reload()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (installed) "Удалить из стикеров" else "Добавить стикеры",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (installed) MaterialTheme.colorScheme.error else Color.White,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
