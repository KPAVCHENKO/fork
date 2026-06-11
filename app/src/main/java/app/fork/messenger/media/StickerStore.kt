package app.fork.messenger.media

import app.fork.messenger.TdClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/** Раздел панели стикеров: заголовок набора + его стикеры. */
data class StickerSection(val title: String, val stickers: List<TdApi.Sticker>)

/** Загружает недавние и установленные наборы стикеров для панели. */
object StickerStore {
    private val _sections = MutableStateFlow<List<StickerSection>>(emptyList())
    val sections: StateFlow<List<StickerSection>> = _sections.asStateFlow()

    @Volatile
    private var loaded = false

    fun loadOnce() {
        if (loaded) return
        loaded = true
        reload()
    }

    fun reload() {
        val collected = LinkedHashMap<String, List<TdApi.Sticker>>()

        TdClient.send(TdApi.GetRecentStickers(false)) { recent ->
            if (recent is TdApi.Stickers && recent.stickers.isNotEmpty()) {
                collected["Недавние"] = recent.stickers.toList()
                publish(collected)
            }
            TdClient.send(TdApi.GetInstalledStickerSets(TdApi.StickerTypeRegular())) { result ->
                if (result !is TdApi.StickerSets) return@send
                // Берём первые наборы, чтобы не грузить всё разом.
                result.sets.take(12).forEach { info ->
                    TdClient.send(TdApi.GetStickerSet(info.id)) { set ->
                        if (set is TdApi.StickerSet) {
                            collected[set.title] = set.stickers.toList()
                            publish(collected)
                        }
                    }
                }
            }
        }
    }

    private fun publish(map: LinkedHashMap<String, List<TdApi.Sticker>>) {
        _sections.value = map.map { (title, stickers) -> StickerSection(title, stickers) }
    }
}
