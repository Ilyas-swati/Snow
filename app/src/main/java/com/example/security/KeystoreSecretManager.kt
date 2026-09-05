package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreSecretManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("snow_secure_vault", Context.MODE_PRIVATE)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "SnowAgentMasterSecretKey"
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_SEPARATOR = "]"
    }

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            Log.w("KeystoreSecretManager", "Android KeyStore init warning (will fallback to obfuscated storage if needed): ${e.message}")
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            entry?.secretKey
        } catch (e: Exception) {
            Log.e("KeystoreSecretManager", "Failed to retrieve secret key", e)
            null
        }
    }

    fun storeSecret(key: String, secret: String) {
        if (secret.isBlank()) {
            prefs.edit().remove(key).apply()
            return
        }

        val secretKey = getSecretKey()
        if (secretKey == null) {
            // Obfuscated fallback
            val encoded = Base64.encodeToString(secret.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            prefs.edit().putString(key, "OBF:$encoded").apply()
            return
        }

        try {
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))

            val ivString = Base64.encodeToString(iv, Base64.NO_WRAP)
            val cipherString = Base64.encodeToString(cipherText, Base64.NO_WRAP)
            val combined = "ENC:$ivString$IV_SEPARATOR$cipherString"

            prefs.edit().putString(key, combined).apply()
        } catch (e: Exception) {
            Log.e("KeystoreSecretManager", "Encryption failed, saving obfuscated fallback", e)
            val encoded = Base64.encodeToString(secret.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            prefs.edit().putString(key, "OBF:$encoded").apply()
        }
    }

    fun getSecret(key: String): String {
        val stored = prefs.getString(key, null) ?: return ""
        if (stored.isBlank()) return ""

        if (stored.startsWith("OBF:")) {
            return try {
                val data = Base64.decode(stored.removePrefix("OBF:"), Base64.NO_WRAP)
                String(data, Charsets.UTF_8)
            } catch (e: Exception) {
                ""
            }
        }

        if (stored.startsWith("ENC:")) {
            val secretKey = getSecretKey() ?: return ""
            return try {
                val payload = stored.removePrefix("ENC:")
                val parts = payload.split(IV_SEPARATOR)
                if (parts.size != 2) return ""

                val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)

                val cipher = Cipher.getInstance(AES_MODE)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                val plainTextBytes = cipher.doFinal(cipherBytes)
                String(plainTextBytes, Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e("KeystoreSecretManager", "Decryption failed for key: $key", e)
                ""
            }
        }

        // Backward compatibility if raw string was stored
        return stored
    }

    fun removeSecret(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clearAllSecrets() {
        prefs.edit().clear().apply()
    }
}
