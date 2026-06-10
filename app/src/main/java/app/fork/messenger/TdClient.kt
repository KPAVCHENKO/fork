package app.fork.messenger

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/**
 * Держатель TDLib-клиента. Обработчик апдейтов вызывается на потоке TDLib,
 * поэтому всё состояние публикуется через потокобезопасные StateFlow,
 * а UI просто подписывается на них.
 */
object TdClient {
    private const val TAG = "TdClient"

    private val _tdVersion = MutableStateFlow("ожидание…")
    val tdVersion: StateFlow<String> = _tdVersion.asStateFlow()

    private val _authStateName = MutableStateFlow("клиент не создан")
    val authStateName: StateFlow<String> = _authStateName.asStateFlow()

    private val _updateLog = MutableStateFlow<List<String>>(emptyList())
    val updateLog: StateFlow<List<String>> = _updateLog.asStateFlow()

    @Volatile
    private var client: Client? = null

    @Synchronized
    fun start(context: Context) {
        if (client != null) return

        // Сообщения внутреннего лога TDLib (уровень 1 = только ошибки) -> logcat
        Client.setLogMessageHandler(1) { verbosityLevel, message ->
            Log.e("TDLib", "[$verbosityLevel] $message")
        }

        val newClient = Client.create(
            { obj -> onUpdate(obj) },
            { e -> Log.e(TAG, "exception in update handler", e) },
            { e -> Log.e(TAG, "exception in result handler", e) },
        )
        client = newClient
        _authStateName.value = "клиент создан, ждём первый апдейт…"

        // TDLib не шлёт апдейты, пока не получит первый запрос, —
        // «будим» клиент запросами версии и состояния авторизации.
        newClient.send(TdApi.GetOption("version")) { result ->
            if (result is TdApi.OptionValueString) {
                _tdVersion.value = result.value
            }
        }
        newClient.send(TdApi.GetAuthorizationState()) { result ->
            if (result is TdApi.AuthorizationState) {
                _authStateName.value = result.javaClass.simpleName
            }
        }
    }

    private fun onUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateOption -> {
                if (obj.name == "version") {
                    val value = obj.value
                    if (value is TdApi.OptionValueString) {
                        _tdVersion.value = value.value
                    }
                }
            }

            is TdApi.UpdateAuthorizationState -> {
                _authStateName.value = obj.authorizationState.javaClass.simpleName
            }
        }
        _updateLog.update { (it + obj.javaClass.simpleName).takeLast(30) }
    }
}
