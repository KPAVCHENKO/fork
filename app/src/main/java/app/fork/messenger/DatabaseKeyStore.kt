package app.fork.messenger

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Хранит 32-байтовый ключ шифрования локальной БД TDLib (databaseEncryptionKey).
 *
 * Сам ключ лежит в SharedPreferences в зашифрованном виде; шифруется он
 * AES-ключом из аппаратного Android Keystore, который нельзя извлечь
 * с устройства. Ключ генерируется один раз и переживает перезапуски.
 */
object DatabaseKeyStore {
    private const val KEYSTORE_ALIAS = "fork_db_key_wrapper"
    private const val PREFS = "td_secure"
    private const val PREF_KEY = "db_key"
    private const val GCM_IV_LENGTH = 12

    fun getOrCreate(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wrapper = getOrCreateWrapperKey()

        prefs.getString(PREF_KEY, null)?.let { stored ->
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            val iv = blob.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = blob.copyOfRange(GCM_IV_LENGTH, blob.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, wrapper, GCMParameterSpec(128, iv))
            return cipher.doFinal(ciphertext)
        }

        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrapper)
        val blob = cipher.iv + cipher.doFinal(key)
        prefs.edit().putString(PREF_KEY, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
        return key
    }

    private fun getOrCreateWrapperKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
