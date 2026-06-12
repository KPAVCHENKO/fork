package app.fork.messenger.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.drinkless.tdlib.TdApi

/** Панель стикеров: наборы с заголовками, сетка стикеров, тап — отправить. */
@Composable
fun StickerPanel(onPick: (TdApi.Sticker) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        StickerGrid(onPick)
    }
}

/**
 * Сетка стикеров без фиксированной высоты/фона — заполняет родителя. Используется
 * как вкладка «Стикеры» в объединённой панели эмодзи/GIF/стикеры.
 */
@Composable
fun StickerGrid(onPick: (TdApi.Sticker) -> Unit) {
    LaunchedEffect(Unit) { StickerStore.loadOnce() }
    val sections by StickerStore.sections.collectAsStateWithLifecycle()

    // Разворачиваем секции в плоский список: заголовок занимает всю строку.
    val items = rememberFlatten(sections)

    Box(Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Text(
                "Загрузка стикеров…",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Box
        }
        LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxSize()) {
            items(items, span = { item ->
                if (item is PanelItem.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1)
            }) { item ->
                when (item) {
                    is PanelItem.Header -> Text(
                        item.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp),
                    )
                    is PanelItem.Cell -> Box(
                        Modifier
                            .height(96.dp)
                            .clickable { onPick(item.sticker) }
                            .padding(6.dp),
                    ) {
                        // Статичное превью — без анимации, чтобы сетка не вешала систему.
                        StickerThumb(item.sticker, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

private sealed interface PanelItem {
    data class Header(val title: String) : PanelItem
    data class Cell(val sticker: TdApi.Sticker) : PanelItem
}

@Composable
private fun rememberFlatten(sections: List<StickerSection>): List<PanelItem> {
    return androidx.compose.runtime.remember(sections) {
        buildList {
            sections.forEach { section ->
                add(PanelItem.Header(section.title))
                section.stickers.forEach { add(PanelItem.Cell(it)) }
            }
        }
    }
}
