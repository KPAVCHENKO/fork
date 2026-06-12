# --- TDLib (КРИТИЧНО) ---
# JNI-биндинг TDLib находит классы, поля и конструкторы по именам через
# рефлексию из нативного кода. Урезать/переименовывать нельзя НИЧЕГО.
-keep class org.drinkless.tdlib.** { *; }

# Нативные методы не переименовывать (JNI-линковка по имени).
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# rlottie JNI: C++ ищет Java_app_fork_messenger_media_RLottie_* по точному имени —
# класс переименовывать нельзя.
-keep class app.fork.messenger.media.RLottie { *; }

# --- Kotlin coroutines: служебные поля для отладки не нужны ---
-dontwarn kotlinx.coroutines.debug.**

# --- Enum'ы настроек восстанавливаются по имени из SharedPreferences ---
-keepclassmembers enum app.fork.messenger.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- GitHub Releases JSON разбирается органически (org.json) — правил не требует ---
