package app.fork.messenger

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/** Состояние процесса входа, на которое реагирует UI. */
sealed interface AuthUiState {
    /** TDLib инициализируется / соединяется. */
    data object Initializing : AuthUiState

    /** Ждём номер телефона. */
    data object WaitPhone : AuthUiState

    /** Код отправлен, ждём ввода. */
    data class WaitCode(val sentTo: String) : AuthUiState

    /** Включена двухфакторная аутентификация, ждём пароль. */
    data class WaitPassword(val hint: String) : AuthUiState

    /** Авторизация завершена. */
    data object Ready : AuthUiState

    /** Состояние, которое приложение пока не умеет обрабатывать. */
    data class Unsupported(val stateName: String) : AuthUiState
}

/**
 * Держатель TDLib-клиента. Обработчик апдейтов вызывается на потоке TDLib,
 * поэтому всё состояние публикуется через потокобезопасные StateFlow.
 *
 * Порядок при старте: WaitTdlibParameters -> SetTdlibParameters -> сразу
 * настраиваем MTProto-прокси (ДО любых действий логина, т.к. прямой доступ
 * к серверам Telegram заблокирован) -> WaitPhoneNumber -> ...
 */
object TdClient {
    private const val TAG = "TdClient"

    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Initializing)
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val _connectionState = MutableStateFlow("—")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    private val _tdVersion = MutableStateFlow("…")
    val tdVersion: StateFlow<String> = _tdVersion.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** true, пока ждём ответа на отправленный запрос (блокируем кнопки). */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Имя залогиненного пользователя (после Ready). */
    private val _myName = MutableStateFlow<String?>(null)
    val myName: StateFlow<String?> = _myName.asStateFlow()

    @Volatile
    private var client: Client? = null
    private lateinit var appContext: Context

    @Synchronized
    fun start(context: Context) {
        if (client != null) return
        appContext = context.applicationContext

        Client.setLogMessageHandler(1) { verbosityLevel, message ->
            Log.e("TDLib", "[$verbosityLevel] $message")
        }

        client = Client.create(
            { obj -> onUpdate(obj) },
            { e -> Log.e(TAG, "exception in update handler", e) },
            { e -> Log.e(TAG, "exception in result handler", e) },
        )

        // TDLib не шлёт апдейты, пока не получит первый запрос.
        client?.send(TdApi.GetOption("version")) { result ->
            if (result is TdApi.OptionValueString) _tdVersion.value = result.value
        }
    }

    // ---------- Действия пользователя ----------

    fun sendPhone(phone: String) = sendAuthRequest(TdApi.SetAuthenticationPhoneNumber(phone.trim(), null))

    fun sendCode(code: String) = sendAuthRequest(TdApi.CheckAuthenticationCode(code.trim()))

    fun sendPassword(password: String) = sendAuthRequest(TdApi.CheckAuthenticationPassword(password))

    fun clearError() {
        _lastError.value = null
    }

    /** Общий канал запросов к TDLib для остальных модулей (ChatStore, MessageStore…). */
    fun send(query: TdApi.Function<*>, onResult: ((TdApi.Object) -> Unit)? = null) {
        val handler = onResult?.let { cb -> Client.ResultHandler { obj -> cb(obj) } }
        client?.send(query, handler)
    }

    private fun sendAuthRequest(query: TdApi.Function<TdApi.Ok>) {
        _lastError.value = null
        _busy.value = true
        client?.send(query) { result ->
            _busy.value = false
            if (result is TdApi.Error) {
                _lastError.value = humanReadableError(result)
            }
            // Успех придёт отдельным UpdateAuthorizationState.
        }
    }

    // ---------- Обработка апдейтов TDLib ----------

    /** Ошибка в одном обработчике не должна лишать остальных этого апдейта. */
    private inline fun safely(tag: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.e(TAG, "update handler failed: $tag", t)
        }
    }

    private fun onUpdate(obj: TdApi.Object) {
        safely("UserCache") { UserCache.handleUpdate(obj) }
        safely("FileHub") { app.fork.messenger.media.FileHub.handleUpdate(obj) }
        safely("TypingTracker") { TypingTracker.handleUpdate(obj) }
        safely("ChatStore") { ChatStore.handleUpdate(obj) }
        safely("MessageStore") { MessageStore.handleUpdate(obj) }
        safely("Notifications") { app.fork.messenger.notify.NotificationsCenter.handleUpdate(obj) }

        when (obj) {
            is TdApi.UpdateAuthorizationState -> onAuthorizationState(obj.authorizationState)

            is TdApi.UpdateConnectionState -> {
                _connectionState.value = when (obj.state) {
                    is TdApi.ConnectionStateWaitingForNetwork -> "ожидание сети"
                    is TdApi.ConnectionStateConnectingToProxy -> "подключение к прокси…"
                    is TdApi.ConnectionStateConnecting -> "подключение…"
                    is TdApi.ConnectionStateUpdating -> "обновление…"
                    is TdApi.ConnectionStateReady -> "подключено"
                    else -> obj.state.javaClass.simpleName
                }
            }

            is TdApi.UpdateOption -> {
                if (obj.name == "version") {
                    (obj.value as? TdApi.OptionValueString)?.let { _tdVersion.value = it.value }
                }
            }
        }
    }

    private fun onAuthorizationState(state: TdApi.AuthorizationState) {
        Log.i(TAG, "auth state: ${state.javaClass.simpleName}")
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                sendTdlibParameters()
                // Прокси настраивается сразу после параметров, ДО логина:
                // весь дальнейший трафик (включая отправку номера) идёт через него.
                ensureProxy()
            }

            is TdApi.AuthorizationStateWaitPhoneNumber -> _authState.value = AuthUiState.WaitPhone

            is TdApi.AuthorizationStateWaitCode ->
                _authState.value = AuthUiState.WaitCode(state.codeInfo.phoneNumber)

            is TdApi.AuthorizationStateWaitPassword ->
                _authState.value = AuthUiState.WaitPassword(state.passwordHint.orEmpty())

            is TdApi.AuthorizationStateReady -> {
                _authState.value = AuthUiState.Ready
                ChatStore.loadChats()
                ChatStore.loadArchive()
                // Подписка на пуши: дальше сервер Telegram будит нас через Firebase.
                runCatching {
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                        .addOnSuccessListener { token -> registerPushToken(token) }
                }.onFailure { Log.w(TAG, "FCM unavailable: ${it.message}") }
                client?.send(TdApi.GetMe()) { result ->
                    if (result is TdApi.User) {
                        _myName.value = listOf(result.firstName, result.lastName)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                    }
                }
            }

            else -> _authState.value = AuthUiState.Unsupported(state.javaClass.simpleName)
        }
    }

    private fun sendTdlibParameters() {
        val params = TdApi.SetTdlibParameters().apply {
            useTestDc = false
            databaseDirectory = File(appContext.filesDir, "tdlib/db").absolutePath
            filesDirectory = File(appContext.filesDir, "tdlib/files").absolutePath
            databaseEncryptionKey = DatabaseKeyStore.getOrCreate(appContext)
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = false
            apiId = BuildConfig.TG_API_ID
            apiHash = BuildConfig.TG_API_HASH
            systemLanguageCode = Locale.getDefault().language.ifBlank { "ru" }
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            systemVersion = "Android ${Build.VERSION.RELEASE}"
            applicationVersion = BuildConfig.VERSION_NAME
        }
        client?.send(params) { result ->
            if (result is TdApi.Error) {
                _lastError.value = "Параметры TDLib: ${humanReadableError(result)}"
            }
        }

        // Подсказываем TDLib тип сети — он подстраивает таймауты и стратегию докачки.
        client?.send(TdApi.SetNetworkType(TdApi.NetworkTypeOther()), null)
    }

    /**
     * Держим клиента «онлайн», пока приложение открыто: Telegram быстрее
     * доставляет новые сообщения активной сессии. Вызывается из App при
     * переходе приложения на передний/задний план.
     */
    fun setOnline(online: Boolean) {
        client?.send(TdApi.SetOption("online", TdApi.OptionValueBoolean(online)), null)
    }

    /**
     * Приводит список прокси TDLib к одному нужному: MTProto-прокси из конфига,
     * включён. Уже сохранённый и включённый не трогаем (список хранится в БД TDLib).
     */
    /** Пере-применить прокси (например, после обновления конфига с GitHub). */
    fun reapplyProxy() {
        if (::appContext.isInitialized) ensureProxy()
    }

    /** Регистрирует FCM-токен: после этого сервер Telegram шлёт пуши сам. */
    fun registerPushToken(token: String) {
        client?.send(
            TdApi.RegisterDevice(TdApi.DeviceTokenFirebaseCloudMessaging(token, true), LongArray(0)),
        ) { result ->
            if (result is TdApi.Error) {
                Log.w(TAG, "registerDevice failed: ${result.message}")
            } else {
                Log.i(TAG, "push token registered")
            }
        }
    }

    /** Передаёт TDLib полезную нагрузку пуша (зашифрованную Telegram). */
    fun processPushPayload(payload: String) {
        client?.send(TdApi.ProcessPushNotification(payload), null)
    }

    private fun ensureProxy() {
        val endpoint = app.fork.messenger.net.ProxyConfig.current(appContext)
        val host = endpoint.host
        val port = endpoint.port
        val secret = endpoint.secret
        if (host.isBlank() || port == 0 || secret.isBlank()) {
            Log.w(TAG, "proxy is not configured, skipping (direct connection)")
            return
        }

        client?.send(TdApi.GetProxies()) { result ->
            if (result !is TdApi.AddedProxies) return@send
            val existing = result.proxies.firstOrNull {
                it.proxy.server == host && it.proxy.port == port && it.proxy.type is TdApi.ProxyTypeMtproto
            }
            when {
                existing == null -> {
                    // Старые/чужие прокси убираем, чтобы не накапливались.
                    result.proxies.forEach { client?.send(TdApi.RemoveProxy(it.id), null) }
                    val proxy = TdApi.Proxy(host, port, TdApi.ProxyTypeMtproto(secret))
                    client?.send(TdApi.AddProxy(proxy, true, "Railway")) { added ->
                        if (added is TdApi.Error) {
                            _lastError.value = "Прокси: ${humanReadableError(added)}"
                        } else {
                            Log.i(TAG, "proxy added and enabled")
                        }
                    }
                }

                !existing.isEnabled -> client?.send(TdApi.EnableProxy(existing.id), null)

                else -> Log.i(TAG, "proxy already configured and enabled")
            }
        }
    }

    private fun humanReadableError(error: TdApi.Error): String = when (error.message) {
        "PHONE_NUMBER_INVALID" -> "Неверный формат номера. Пример: +79161234567"
        "PHONE_CODE_INVALID" -> "Неверный код"
        "PHONE_CODE_EXPIRED" -> "Код устарел, запросите новый"
        "PASSWORD_HASH_INVALID" -> "Неверный пароль"
        else -> "${error.code}: ${error.message}"
    }
}
