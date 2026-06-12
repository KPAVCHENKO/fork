package app.fork.messenger

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    // Сторож подключения: если долго не можем подключиться через активный прокси —
    // перевыбираем самый быстрый рабочий из пула (авто-failover).
    private val watchdogScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
    )
    private var watchdogJob: kotlinx.coroutines.Job? = null
    @Volatile private var failoverTried = false

    /** Wall-clock at process start, for per-phase connection timing logs. */
    @Volatile private var connectStartMs = 0L
    @Volatile private var channelRefreshed = false

    @Synchronized
    fun start(context: Context) {
        if (client != null) return
        appContext = context.applicationContext
        connectStartMs = System.currentTimeMillis()

        // CRITICAL perf fix: TDLib defaults to a very chatty verbosity and writes
        // EVERY internal event to Android's log (tag DLTD). Measured on-device at
        // ~21,000 lines/sec (levels 3-4) — native string formatting + JNI + the
        // logcat pipe per line is a serious CPU/IO drain on weak phones, slowing both
        // cold-connect and scrolling. Drop to level 1 (errors/warnings only).
        runCatching { Client.execute(TdApi.SetLogVerbosityLevel(1)) }

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
                // Per-phase timing so we can see WHERE the cold connect spends time
                // (filter logcat by "ForkConnect"): each line shows ms since process start.
                val elapsed = if (connectStartMs == 0L) 0 else System.currentTimeMillis() - connectStartMs
                Log.i("ForkConnect", "${obj.state.javaClass.simpleName} @ +${elapsed}ms")
                if (obj.state is TdApi.ConnectionStateReady) {
                    watchdogJob?.cancel()
                    failoverTried = false // fresh episode next time we drop
                    // Connection is up — now safe to tidy up stale proxies (deferred so
                    // it never delays the handshake).
                    if (::appContext.isInitialized) {
                        app.fork.messenger.net.ProxyPool.cleanupKeepingPrimary(appContext)
                    }
                } else {
                    armProxyWatchdog()
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
                // Refresh the proxy pool from the public channel, but LATER: doing it
                // immediately stormed dozens of TLS handshakes (TestProxy) that competed
                // with the initial chat sync. Defer 90s and run once.
                if (::appContext.isInitialized && !channelRefreshed) {
                    channelRefreshed = true
                    watchdogScope.launch {
                        kotlinx.coroutines.delay(90_000)
                        app.fork.messenger.net.ProxyPool.refreshFromChannel(appContext)
                    }
                }
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

        // Актуальный тип сети: TDLib подстраивает таймауты, а смена типа
        // мгновенно переоткрывает соединения (см. NetworkMonitor).
        client?.send(TdApi.SetNetworkType(app.fork.messenger.net.NetworkMonitor.current()), null)
    }

    /**
     * Держим клиента «онлайн», пока приложение открыто: Telegram быстрее
     * доставляет новые сообщения активной сессии. Вызывается из App при
     * переходе приложения на передний/задний план.
     */
    fun setOnline(online: Boolean) {
        client?.send(TdApi.SetOption("online", TdApi.OptionValueBoolean(online)), null)
        // Возврат на передний план при «висящем» соединении: форсируем
        // переоткрытие, не дожидаясь таймаута бэкофа TDLib.
        if (online && _connectionState.value != "подключено") {
            app.fork.messenger.net.NetworkMonitor.push("foreground")
        }
    }

    /**
     * Приводит список прокси TDLib к одному нужному: MTProto-прокси из конфига,
     * включён. Уже сохранённый и включённый не трогаем (список хранится в БД TDLib).
     */
    /** Пере-применить прокси (например, после обновления конфига с GitHub). */
    fun reapplyProxy() {
        // GitHub config changed: full reconcile (drop old proxy, enable the new one).
        if (::appContext.isInitialized) {
            app.fork.messenger.net.ProxyPool.cleanupKeepingPrimary(appContext)
        }
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
        if (!::appContext.isInitialized) return
        // Enable the primary proxy IMMEDIATELY (no round-trip) so it is active for
        // TDLib's very first connection attempt — avoids a doomed direct attempt that
        // times out on RF DPI and eats ~10s. Cleanup of stale proxies is deferred to
        // after ConnectionStateReady.
        app.fork.messenger.net.ProxyPool.enablePrimaryFast(appContext)
    }

    /**
     * Conservative failover: only if we still aren't connected 30s after going
     * offline, try the saved proxy pool ONCE (e.g. primary blocked / VPN). Never
     * loops — runs at most once per disconnect episode, so it cannot interrupt a
     * handshake that is simply taking a while.
     */
    private fun armProxyWatchdog() {
        if (watchdogJob?.isActive == true || failoverTried) return
        if (!::appContext.isInitialized) return
        watchdogJob = watchdogScope.launch {
            kotlinx.coroutines.delay(30_000)
            if (_connectionState.value != "подключено") {
                failoverTried = true
                Log.w(TAG, "still not connected after 30s — trying proxy failover")
                app.fork.messenger.net.ProxyPool.selectBestAndEnable(appContext)
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
