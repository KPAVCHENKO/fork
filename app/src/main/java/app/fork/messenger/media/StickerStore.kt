package app.fork.messenger.media

import app.fork.messenger.TdClient
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/** Раздел панели стикеров: заголовок набора + его стикеры. */
data class StickerSection(val title: String, val stickers: List<TdApi.Sticker>)

/**
 * Стикеры панели с ЖИВОЙ синхронизацией (как в TG): «Избранные» (макс 5), «Недавние»
 * (отправленный стикер сразу встаёт первым) и установленные наборы. Подписана на апдейты
 * TDLib (UpdateFavoriteStickers/UpdateRecentStickers/UpdateInstalledStickerSets) — секции
 * обновляются в реальном времени.
 */
object StickerStore {
    private const val MAX_FAVORITES = 5

    private val _sections = MutableStateFlow<List<StickerSection>>(emptyList())
    val sections: StateFlow<List<StickerSection>> = _sections.asStateFlow()

    private val _searchResults = MutableStateFlow<List<TdApi.Sticker>>(emptyList())
    val searchResults: StateFlow<List<TdApi.Sticker>> = _searchResults.asStateFlow()

    @Volatile private var favorites: List<TdApi.Sticker> = emptyList()
    @Volatile private var recent: List<TdApi.Sticker> = emptyList()
    // Наборы: id -> (название, стикеры), порядок сохраняем отдельным списком id.
    private val sets = ConcurrentHashMap<Long, StickerSection>()
    @Volatile private var setOrder: List<Long> = emptyList()

    @Volatile private var loaded = false

    fun loadOnce() {
        if (loaded) return
        loaded = true
        reload()
    }

    /** Полная перезагрузка всех секций (избранные/недавние/наборы). */
    fun reload() {
        reloadFavorites()
        reloadRecent()
        reloadSets()
    }

    /** Маршрутизируется из TdClient.onUpdate — даёт живой синхрон. */
    fun handleUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateFavoriteStickers -> reloadFavorites()
            is TdApi.UpdateRecentStickers -> if (!obj.isAttached) reloadRecent()
            is TdApi.UpdateInstalledStickerSets -> if (obj.stickerType is TdApi.StickerTypeRegular) reloadSets()
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        TdClient.send(TdApi.SearchStickers(TdApi.StickerTypeRegular(), query, "", null, 0, 50)) { res ->
            if (res is TdApi.Stickers) _searchResults.value = res.stickers.filterNotNull()
        }
    }

    private fun reloadFavorites() {
        TdClient.send(TdApi.GetFavoriteStickers()) { res ->
            if (res is TdApi.Stickers) {
                favorites = res.stickers.filterNotNull().take(MAX_FAVORITES)
                publish()
            }
        }
    }

    private fun reloadRecent() {
        TdClient.send(TdApi.GetRecentStickers(false)) { res ->
            if (res is TdApi.Stickers) {
                recent = res.stickers.filterNotNull()
                publish()
            }
        }
    }

    private fun reloadSets() {
        TdClient.send(TdApi.GetInstalledStickerSets(TdApi.StickerTypeRegular())) { result ->
            if (result !is TdApi.StickerSets) return@send
            val order = result.sets.take(12).map { it.id }
            setOrder = order
            order.forEach { id ->
                TdClient.send(TdApi.GetStickerSet(id)) { set ->
                    if (set is TdApi.StickerSet) {
                        sets[id] = StickerSection(set.title, set.stickers.filterNotNull())
                        publish()
                    }
                }
            }
        }
    }

    private fun publish() {
        _sections.value = buildList {
            if (favorites.isNotEmpty()) add(StickerSection("Избранные", favorites))
            if (recent.isNotEmpty()) add(StickerSection("Недавние", recent))
            setOrder.forEach { id -> sets[id]?.let { add(it) } }
        }
    }
}
