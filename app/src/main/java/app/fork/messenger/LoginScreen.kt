package app.fork.messenger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Экран входа: ведёт по шагам номер -> код -> пароль 2FA. */
@Composable
fun LoginScreen() {
    val authState by TdClient.authState.collectAsStateWithLifecycle()
    val connection by TdClient.connectionState.collectAsStateWithLifecycle()
    val tdVersion by TdClient.tdVersion.collectAsStateWithLifecycle()
    val error by TdClient.lastError.collectAsStateWithLifecycle()
    val busy by TdClient.busy.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Fork", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Состояние: $connection",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(32.dp))

        when (val state = authState) {
            AuthUiState.Initializing -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Инициализация TDLib…")
            }

            AuthUiState.WaitPhone -> PhoneStep(busy)

            is AuthUiState.WaitCode -> CodeStep(state.sentTo, busy)

            is AuthUiState.WaitPassword -> PasswordStep(state.hint, busy)

            AuthUiState.Ready -> {
                // MainActivity переключит экран; сюда попадаем на долю секунды.
                CircularProgressIndicator()
            }

            is AuthUiState.Unsupported -> Text(
                "Состояние «${state.stateName}» пока не поддерживается.\n" +
                    "Напиши разработчику (то есть себе 🙂).",
                textAlign = TextAlign.Center,
            )
        }

        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(48.dp))
        Text(
            "TDLib $tdVersion · трафик через MTProto-прокси",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun PhoneStep(busy: Boolean) {
    var phone by rememberSaveable { mutableStateOf("+7") }

    Text("Введите номер телефона", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        label = { Text("Номер телефона") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { TdClient.sendPhone(phone) },
        enabled = !busy && phone.length >= 10,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (busy) "Отправка…" else "Получить код")
    }
}

@Composable
private fun CodeStep(sentTo: String, busy: Boolean) {
    var code by rememberSaveable { mutableStateOf("") }

    Text("Код отправлен", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        "Проверь Telegram на других устройствах или SMS ($sentTo)",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = code,
        onValueChange = { code = it },
        label = { Text("Код") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { TdClient.sendCode(code) },
        enabled = !busy && code.length >= 4,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (busy) "Проверка…" else "Войти")
    }
}

@Composable
private fun PasswordStep(hint: String, busy: Boolean) {
    var password by rememberSaveable { mutableStateOf("") }

    Text("Пароль двухфакторной защиты", style = MaterialTheme.typography.titleMedium)
    if (hint.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Text("Подсказка: $hint", style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Пароль") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { TdClient.sendPassword(password) },
        enabled = !busy && password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (busy) "Проверка…" else "Подтвердить")
    }
}
