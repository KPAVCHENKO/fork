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
    /** Last time we re-selected a proxy; gates re-selection so it cannot churn. */
    @Volatile private var lastSelectionMs = 0L
    /** True once we've connected DIRECTLY while a VPN is active — makes direct sticky so
     *  a brief blip never auto-switches us to a proxy (that broke loading under VPN). */
    @Volatile private var vpnDirectConnected = false

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
                    // We connected. If a VPN is active, this is a DIRECT connection —
                    // mark it sticky so we never auto-switch to a proxy afterwards.
                    if (::appContext.isInitialized) {
                        if (app.fork.messenger.net.NetworkMonitor.isVpnActive()) {
                            vpnDirectConnected = true
                        }
                        app.fork.messenger.net.ProxyPool.refreshEntries(appContext)
                    }
                } else {
                    armReconnect(initialDelayMs = 12_000)
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

            is TdApi.AuthorizationStateClosed -> {
                // LogOut завершён: TDLib закрыт и локальные данные удалены. Создаём
                // нового клиента — он заново инициализируется и покажет экран входа.
                client = Client.create(
                    { obj -> onUpdate(obj) },
                    { e -> Log.e(TAG, "exception in update handler", e) },
                    { e -> Log.e(TAG, "exception in result handler", e) },
                )
                client?.send(TdApi.GetOption("version"), null)
            }

            else -> _authState.value = AuthUiState.Unsupported(state.javaClass.simpleName)
        }
    }

    /** Выход из аккаунта: TDLib удаляет локальные данные и закрывается (см. Closed). */
    fun logOut() {
        send(TdApi.LogOut())
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
        if (app.fork.messenger.net.NetworkMonitor.isVpnActive()) {
            // A VPN already bypasses the block — connect directly (no proxy) to avoid
            // the extra proxy hop's latency. Fall back to the primary proxy if direct
            // doesn't come up (some VPNs don't actually route Telegram).
            app.fork.messenger.net.ProxyPool.goDirect(appContext)
            watchdogJob?.cancel()
            armVpnFallback()
        } else {
            // Enable the primary proxy IMMEDIATELY (no round-trip) so it is active for
            // TDLib's very first connection attempt — avoids a doomed direct attempt
            // that times out on RF DPI. Stale-proxy cleanup is deferred to after Ready.
            app.fork.messenger.net.ProxyPool.enablePrimaryFast(appContext)
        }
    }

    /**
     * VPN toggled. When ON, connect directly (the VPN bypasses the block, so a proxy
     * only adds latency); the primary proxy is re-enabled if direct doesn't come up.
     * When OFF, re-enable the proxy (needed again against the DPI block).
     */
    fun onVpnChanged(vpn: Boolean) {
        if (!::appContext.isInitialized) return
        watchdogJob?.cancel()
        lastSelectionMs = 0L
        vpnDirectConnected = false
        if (vpn) {
            app.fork.messenger.net.ProxyPool.goDirect(appContext)
            armVpnFallback() // direct, with a patient one-time proxy fallback
        } else {
            app.fork.messenger.net.ProxyPool.enablePrimaryFast(appContext)
            armReconnect(initialDelayMs = 12_000)
        }
    }

    /**
     * Reconnect strategy, VPN-aware. Without a VPN we churn the pool to find any proxy
     * that beats the DPI block. WITH a VPN we go DIRECT and keep it: once a direct
     * connection has come up we never auto-switch to a proxy (a brief blip recovers on
     * its own — switching to a proxy under a VPN was what broke loading).
     */
    private fun armReconnect(initialDelayMs: Long) {
        if (!::appContext.isInitialized) return
        if (watchdogJob?.isActive == true) return
        if (app.fork.messenger.net.NetworkMonitor.isVpnActive()) {
            // Already connected directly once → leave it alone, TDLib recovers itself.
            if (vpnDirectConnected) return
            armVpnFallback()
            return
        }
        watchdogJob = watchdogScope.launch {
            kotlinx.coroutines.delay(initialDelayMs)
            while (_connectionState.value != "подключено") {
                if (System.currentTimeMillis() - lastSelectionMs > 25_000) {
                    lastSelectionMs = System.currentTimeMillis()
                    app.fork.messenger.net.ProxyPool.selectWorking(appContext, "stall")
                }
                kotlinx.coroutines.delay(8_000)
            }
        }
    }

    /**
     * VPN fallback: we went direct. A VPN that routes Telegram connects directly within
     * a few seconds, so we wait patiently; ONLY if direct has never come up after a long
     * grace do we try the primary proxy once. Once direct connects ([vpnDirectConnected]),
     * this never switches us away — that auto-switch is what stopped everything loading.
     */
    private fun armVpnFallback() {
        if (!::appContext.isInitialized) return
        watchdogJob = watchdogScope.launch {
            kotlinx.coroutines.delay(20_000)
            if (!vpnDirectConnected && _connectionState.value != "подключено") {
                Log.i(TAG, "vpn: direct never connected in 20s; trying primary proxy once")
                app.fork.messenger.net.ProxyPool.enablePrimaryFast(appContext)
            }
        }
    }

    /**
     * Called by NetworkMonitor when the device network changes (incl. VPN on/off).
     * NetworkMonitor already pushed the new network type. If the active proxy stops
     * working on the new network, re-select soon (VPN-aware via [armReconnect]).
     */
    fun onNetworkChanged() {
        if (!::appContext.isInitialized) return
        if (_connectionState.value == "подключено") return // active proxy still works
        watchdogJob?.cancel()
        lastSelectionMs = 0L // a real network change justifies an immediate re-select
        armReconnect(initialDelayMs = 5_000)
    }

    private fun humanReadableError(error: TdApi.Error): String = when (error.message) {
        "PHONE_NUMBER_INVALID" -> "Неверный формат номера. Пример: +79161234567"
        "PHONE_CODE_INVALID" -> "Неверный код"
        "PHONE_CODE_EXPIRED" -> "Код устарел, запросите новый"
        "PASSWORD_HASH_INVALID" -> "Неверный пароль"
        else -> "${error.code}: ${error.message}"
    }
}
