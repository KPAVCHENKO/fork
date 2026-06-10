# Fork

Собственный Android-клиент Telegram на [TDLib](https://core.telegram.org/tdlib) для личного и семейного использования.

Подключение к Telegram идёт через MTProto-прокси (Fake TLS), поэтому работает там, где прямой доступ к серверам Telegram заблокирован. Распространяется сайдлоадом и умеет обновляться из GitHub Releases.

## Структура

- `app/` — приложение (Kotlin, Jetpack Compose)
- `tdlib/` — модуль с Java-биндингом TDLib (`Client`, `TdApi`) и нативными библиотеками (`libtdjni.so` для arm64-v8a и x86_64)
- `scripts/tdlib-build-env.sh` — окружение для пересборки TDLib
- `third_party/td` — клон [tdlib/td](https://github.com/tdlib/td) (не в git, нужен только для пересборки TDLib)

## Секреты

Все секреты живут в `local.properties` (не коммитится). Шаблон с пояснениями — в `config.example.properties`:

- `tg.apiId` / `tg.apiHash` — с https://my.telegram.org
- `proxy.host` / `proxy.port` / `proxy.secret` — параметры MTProto-прокси
- `update.repo` — GitHub-репозиторий для обновлений (`owner/repo`)

## Сборка приложения

```
./gradlew :app:assembleDebug
```

APK появится в `app/build/outputs/apk/debug/`.

## Пересборка TDLib (по необходимости)

TDLib собирается официальными скриптами из `third_party/td/example/android` под Git Bash:

```bash
source scripts/tdlib-build-env.sh
cd third_party/td/example/android
./build-openssl-2abi.sh "C:/Android" "<версия NDK>"
./build-tdlib-2abi.sh "C:/Android" "<версия NDK>"
```

Скрипты `*-2abi.sh` — копии официальных, собирающие только arm64-v8a и x86_64.
Результат (`tdlib/java/...` и `tdlib/libs/...`) копируется в модуль `tdlib/` проекта.
Требуемые портативные инструменты лежат в `C:\tools` (см. комментарии в `scripts/tdlib-build-env.sh`).
