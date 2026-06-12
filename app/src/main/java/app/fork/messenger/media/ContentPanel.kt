package app.fork.messenger.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fork.messenger.ui.ForkIcons
import coil.compose.AsyncImage
import java.io.File
import org.drinkless.tdlib.TdApi

/**
 * Объединённая панель ввода контента — как в Telegram: одна панель с нижними
 * вкладками «Эмодзи / GIF / Стикеры». Кнопка в поле ввода переключает её с
 * клавиатурой. Эмодзи вставляются в текст, GIF и стикеры отправляются сразу.
 */
private enum class ContentTab { EMOJI, GIF, STICKERS }

/** Запоминает последнюю открытую вкладку панели между открытиями (как в TG). */
private var lastContentTab = ContentTab.STICKERS

@Composable
fun ContentPanel(
    onSticker: (TdApi.Sticker) -> Unit,
    onGif: (TdApi.Animation) -> Unit,
    onEmoji: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    var tab by remember { mutableStateOf(lastContentTab) }

    Column(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                ContentTab.EMOJI -> EmojiGrid(onEmoji)
                ContentTab.GIF -> GifGrid(onGif)
                ContentTab.STICKERS -> StickerTab(onSticker)
            }
        }
        BottomTabs(tab, onTab = { tab = it; lastContentTab = it }, onBackspace = onBackspace)
    }
}

/** Вкладка стикеров: поиск по эмодзи сверху + сетка (наборы или результаты поиска). */
@Composable
private fun StickerTab(onSticker: (TdApi.Sticker) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results by StickerStore.searchResults.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                ForkIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "Поиск по эмодзи 😀",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { query = it; StickerStore.search(it) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (query.isBlank()) {
            StickerGrid(onSticker)
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxSize()) {
                items(results) { st ->
                    Box(
                        Modifier
                            .height(80.dp)
                            .clickable { onSticker(st) }
                            .padding(6.dp),
                    ) {
                        StickerThumb(st, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomTabs(tab: ContentTab, onTab: (ContentTab) -> Unit, onBackspace: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabIcon(ForkIcons.Smile, tab == ContentTab.EMOJI) { onTab(ContentTab.EMOJI) }
        TabIcon(ForkIcons.Gif, tab == ContentTab.GIF) { onTab(ContentTab.GIF) }
        TabIcon(ForkIcons.Sticker, tab == ContentTab.STICKERS) { onTab(ContentTab.STICKERS) }
        Box(Modifier.weight(1f))
        // Backspace только на вкладке эмодзи (как в TG).
        if (tab == ContentTab.EMOJI) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onBackspace() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    ForkIcons.Backspace,
                    contentDescription = "удалить",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun TabIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

// ---------------- Emoji ----------------

@Composable
private fun EmojiGrid(onEmoji: (String) -> Unit) {
    val flat = remember { flattenEmoji() }
    LazyVerticalGrid(columns = GridCells.Fixed(8), modifier = Modifier.fillMaxSize()) {
        items(flat, span = { e ->
            if (e.header != null) GridItemSpan(maxLineSpan) else GridItemSpan(1)
        }) { e ->
            if (e.header != null) {
                Text(
                    e.header,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 2.dp),
                )
            } else {
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEmoji(e.emoji) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(e.emoji, fontSize = 26.sp)
                }
            }
        }
    }
}

private class EmojiItem(val emoji: String, val header: String?)

private fun flattenEmoji(): List<EmojiItem> = buildList {
    EMOJI_CATEGORIES.forEach { (title, list) ->
        add(EmojiItem("", title))
        list.forEach { add(EmojiItem(it, null)) }
    }
}

// ---------------- GIF ----------------

@Composable
private fun GifGrid(onGif: (TdApi.Animation) -> Unit) {
    LaunchedEffect(Unit) { AnimationsStore.loadOnce() }
    val gifs by AnimationsStore.saved.collectAsStateWithLifecycle()

    if (gifs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Нет сохранённых GIF",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(gifs) { gif -> GifCell(gif, onGif) }
    }
}

@Composable
private fun GifCell(gif: TdApi.Animation, onGif: (TdApi.Animation) -> Unit) {
    val thumb = gif.thumbnail?.file
    val path = if (thumb != null) rememberFileState(thumb, autoDownload = true, priority = 12).path else null
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onGif(gif) },
    ) {
        if (path != null) {
            AsyncImage(
                model = File(path),
                contentDescription = "GIF",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// Базовый набор эмодзи по категориям (для быстрого ввода без сторонней клавиатуры).
private val EMOJI_CATEGORIES: List<Pair<String, List<String>>> = listOf(
    "Смайлы" to "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🥳 😏 😒 😞 😔 😟 😕 🙁 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😴 🤤 😪 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕".split(" "),
    "Жесты" to "👍 👎 👌 🤌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 🖖 👋 🤝 🙏 💪 👏 🙌 👐 🤲 🤜 🤛 ✊ 👊".split(" "),
    "Любовь" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❤️‍🔥 💕 💞 💓 💗 💖 💘 💝 💟".split(" "),
    "Животные" to "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐢 🐍 🐙 🦀 🐠 🐟 🐬 🐳 🐋 🦈 🐅 🐆 🦓 🦍 🐘 🐪 🦒 🐄 🐎 🐖 🐑 🐐 🦌 🐕 🐈".split(" "),
    "Еда" to "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🥦 🥕 🌽 🥔 🍠 🥐 🍞 🧀 🥚 🍳 🥞 🥓 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍣 🍱 🍤 🍙 🍚 🍦 🍰 🎂 🍫 🍿 🍩 🍪 ☕ 🍵 🥤 🍺 🍻 🥂 🍷 🍸".split(" "),
    "Активности" to "⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🎱 🏓 🏸 🥅 🏒 🏑 🏏 ⛳ 🏹 🎣 🥊 🥋 ⛸ 🎿 🏂 🏋️ 🤸 🤺 🏌️ 🏇 🧘 🏄 🏊 🚣 🚴 🏆 🥇 🥈 🥉 🏅 🎯 🎳 🎮 🎲 🎸 🎹 🎺 🎷 🥁 🎤 🎧 🎨 🎬".split(" "),
    "Путешествия" to "🚗 🚕 🚙 🚌 🏎 🚓 🚑 🚒 🚐 🚚 🚜 🛴 🚲 🛵 🏍 ✈️ 🚀 🛸 🚁 ⛵ 🚤 🛳 ⛴ 🚢 ⚓ ⛽ 🗺 🗽 🗼 🏰 🎡 🎢 🎠 ⛲ 🏖 🏝 🌋 ⛰ 🏔 🗻 🏕 ⛺ 🏠 🏡 🏙".split(" "),
    "Символы" to "❤️ 💯 ✅ ❌ ⭕ 🔥 ⭐ 🌟 ✨ ⚡ ☀️ 🌙 🌈 ❄️ 💧 🎉 🎊 🎈 🎁 🔔 💤 💢 💣 💥 💬 💭 ➕ ➖ ✔️ 💲 ©️ ®️ ™️ ♠️ ♣️ ♥️ ♦️ 🔝 🆗 🆕 🆒".split(" "),
)
