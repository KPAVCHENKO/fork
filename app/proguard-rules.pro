# --- TDLib (КРИТИЧНО) ---
# JNI-биндинг TDLib находит классы, поля и конструкторы по именам через
# рефлексию из нативного кода. Урезать/переименовывать нельзя НИЧЕГО.
-keep class org.drinkless.tdlib.** { *; }

# Нативные методы не переименовывать (JNI-линковка по имени).
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# --- Kotlin coroutines: служебные поля для отладки не нужны ---
-dontwarn kotlinx.coroutines.debug.**

# --- Enum'ы настроек восстанавливаются по имени из SharedPreferences ---
-keepclassmembers enum app.fork.messenger.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- GitHub Releases JSON разбирается органически (org.json) — правил не требует ---
