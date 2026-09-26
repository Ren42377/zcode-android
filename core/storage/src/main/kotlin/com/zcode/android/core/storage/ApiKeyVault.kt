package com.zcode.android.core.storage

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Stores API keys encrypted with an AES/GCM key that lives inside the Android
// Keystore. Ciphertext (with its IV) is persisted in app-private preferences;
// the plaintext key never reaches a file, log, or backup. See ADR 0003.
class ApiKeyVault(
    private val context: Context,
) {
    @Synchronized
    fun save(
        providerId: String,
        apiKey: String,
    ) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        val iv = cipher.iv
        val encrypted = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        preferences()
            .edit()
            .putString(entryKey(providerId), encode(iv, encrypted))
            .apply()
    }

    @Synchronized
    fun load(providerId: String): String? {
        val stored = preferences().getString(entryKey(providerId), null) ?: return null
        val (iv, encrypted) = decode(stored)
        val cipher =
            Cipher
                .getInstance(
                    TRANSFORMATION,
                ).apply { init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(GCM_TAG_BITS, iv)) }
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    @Synchronized
    fun clear(providerId: String) {
        preferences().edit().remove(entryKey(providerId)).apply()
    }

    private fun preferences() = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun entryKey(providerId: String) = "api_key_$providerId"

    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let {
            return it
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            android.security.keystore.KeyGenParameterSpec
                .Builder(
                    KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encode(
        iv: ByteArray,
        encrypted: ByteArray,
    ): String =
        Base64.encodeToString(
            iv + encrypted,
            Base64.NO_WRAP,
        )

    private fun decode(stored: String): Pair<ByteArray, ByteArray> {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, GCM_IV_BYTES)
        val encrypted = bytes.copyOfRange(GCM_IV_BYTES, bytes.size)
        return Pair(iv, encrypted)
    }

    private companion object {
        const val PREFERENCES_NAME = "zcode_secure"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "zcode_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
