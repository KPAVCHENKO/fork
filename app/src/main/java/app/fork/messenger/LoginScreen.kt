package app.fork.messenger

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fork.messenger.ui.BrandCyan
import app.fork.messenger.ui.BrandIndigo
import app.fork.messenger.ui.ForkIcons
import app.fork.messenger.ui.GradientButton

/**
 * Экран входа (Fork Design Spec §4.1): полноэкранный «дышащий» градиент бренда,
 * бренд-блок сверху, нижняя шторка с шагами номер → код → пароль 2FA.
 */
@Composable
fun LoginScreen() {
    val authState by TdClient.authState.collectAsStateWithLifecycle()
    val connection by TdClient.connectionState.collectAsStateWithLifecycle()
    val tdVersion by TdClient.tdVersion.collectAsStateWithLifecycle()
    val error by TdClient.lastError.collectAsStateWithLifecycle()
    val busy by TdClient.busy.collectAsStateWithLifecycle()

    // «Дыхание» градиента: стопы медленно плывут по диагонали, tween 6000ms реверс.
    val breath = rememberInfiniteTransition(label = "breath")
    val t by breath.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "t",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(BrandIndigo, BrandCyan),
                        start = Offset(-size.width * 0.2f * t, size.height * 0.25f * t),
                        end = Offset(size.width * (1f + 0.2f * t), size.height * (1f - 0.25f * t)),
                    ),
                )
            }
            .imePadding(),
    ) {
        // Бренд-блок на градиенте
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                ForkIcons.ForkMark,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(74.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Fork",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Мессенджер для своих",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "$connection · TDLib $tdVersion · MTProto-прокси",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
            )
        }

        // Нижняя шторка с шагом авторизации
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                AnimatedContent(
                    targetState = authState,
                    transitionSpec = {
                        (slideInHorizontally(tween(300)) { it / 6 } + fadeIn(tween(300)))
                            .togetherWith(slideOutHorizontally(tween(300)) { -it / 6 } + fadeOut(tween(200)))
                    },
                    contentKey = { it::class },
                    label = "authStep",
                ) { state ->
                    Column {
                        when (state) {
                            AuthUiState.Initializing -> InitStep()
                            AuthUiState.WaitPhone -> PhoneStep(busy)
                            is AuthUiState.WaitCode -> CodeStep(state.sentTo, busy)
                            is AuthUiState.WaitPassword -> PasswordStep(state.hint, busy)
                            AuthUiState.Ready -> InitStep() // MainActivity сейчас переключит экран
                            is AuthUiState.Unsupported -> Text(
                                "Состояние «${state.stateName}» пока не поддерживается.\n" +
                                    "Напиши разработчику (то есть себе 🙂).",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun InitStep() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(
            "Подключение…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Поле ввода 56dp, радиус 18 (Fork Design Spec §4.1). */
@Composable
private fun LoginField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    password: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (password) PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PhoneStep(busy: Boolean) {
    var phone by rememberSaveable { mutableStateOf("+7") }

    Text("Ваш телефон", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(6.dp))
    Text(
        "Номер аккаунта Telegram — войдёте в свои чаты",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
    LoginField(
        value = phone,
        onValue = { phone = it },
        placeholder = "+7 900 000-00-00",
        keyboardType = KeyboardType.Phone,
    )
    Spacer(Modifier.height(16.dp))
    GradientButton(
        text = "Продолжить",
        onClick = { TdClient.sendPhone(phone) },
        enabled = phone.length >= 10,
        busy = busy,
    )
}

@Composable
private fun CodeStep(sentTo: String, busy: Boolean) {
    var code by rememberSaveable { mutableStateOf("") }
    // Код пришёл целиком — отправляем сами, без кнопки.
    LaunchedEffect(code) {
        if (code.length == 5 && !busy) TdClient.sendCode(code)
    }

    Text("Код подтверждения", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(6.dp))
    Text(
        "Отправлен в Telegram на других устройствах или по SMS ($sentTo)",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
    CodeCells(code = code, onCode = { code = it })
    Spacer(Modifier.height(16.dp))
    GradientButton(
        text = "Войти",
        onClick = { TdClient.sendCode(code) },
        enabled = code.length >= 4,
        busy = busy,
    )
}

/** 5 ячеек кода 52×60dp с автопереходом (Fork Design Spec §4.1). */
@Composable
private fun CodeCells(code: String, onCode: (String) -> Unit) {
    BasicTextField(
        value = code,
        onValueChange = { v -> onCode(v.filter { it.isDigit() }.take(5)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        decorationBox = { inner ->
            Box {
                // Невидимое настоящее поле, поверх — ячейки.
                Box(Modifier.size(1.dp)) { inner() }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    repeat(5) { i ->
                        val char = code.getOrNull(i)?.toString() ?: ""
                        val active = i == code.length || char.isNotEmpty()
                        val shape = RoundedCornerShape(16.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp)
                                .clip(shape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(
                                    width = 1.5.dp,
                                    color = if (active) MaterialTheme.colorScheme.primary
                                    else Color.Transparent,
                                    shape = shape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(char, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun PasswordStep(hint: String, busy: Boolean) {
    var password by rememberSaveable { mutableStateOf("") }

    Text("Пароль", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(6.dp))
    Text(
        if (hint.isNotBlank()) "Двухфакторная защита · подсказка: $hint"
        else "Двухфакторная защита аккаунта",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
    LoginField(
        value = password,
        onValue = { password = it },
        placeholder = "Пароль",
        keyboardType = KeyboardType.Password,
        password = true,
    )
    Spacer(Modifier.height(16.dp))
    GradientButton(
        text = "Подтвердить",
        onClick = { TdClient.sendPassword(password) },
        enabled = password.isNotEmpty(),
        busy = busy,
    )
}
