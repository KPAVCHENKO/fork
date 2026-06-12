package app.fork.messenger.media

import app.fork.messenger.TdClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/** Загружает сохранённые GIF (анимации) пользователя для вкладки GIF в панели. */
object AnimationsStore {
    private val _saved = MutableStateFlow<List<TdApi.Animation>>(emptyList())
    val saved: StateFlow<List<TdApi.Animation>> = _saved.asStateFlow()

    @Volatile
    private var loaded = false

    fun loadOnce() {
        if (loaded) return
        loaded = true
        reload()
    }

    fun reload() {
        TdClient.send(TdApi.GetSavedAnimations()) { result ->
            if (result is TdApi.Animations) {
                _saved.value = result.animations.filterNotNull()
            }
        }
    }
}
