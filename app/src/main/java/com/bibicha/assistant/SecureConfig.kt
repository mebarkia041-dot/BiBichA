package com.bibicha.assistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureConfig {
    private const val PREFS = "bibicha_secure_config"
    private const val KEY_ALIAS = "bibicha_mobile_token_key"
    private const val URL_KEY = "worker_url"
    private const val TOKEN_KEY = "mobile_token_encrypted"

    fun save(context: Context, workerUrl: String, token: String) {
        val normalizedUrl = workerUrl.trim().trimEnd('/')
        require(normalizedUrl.startsWith("https://") && normalizedUrl.endsWith(".workers.dev")) {
            "أدخل رابط Cloudflare Worker الصحيح الذي يبدأ بـ https:// وينتهي بـ .workers.dev"
        }
        require(token.trim().length >= 24) { "رمز الربط يجب أن يكون أطول من 24 حرفاً." }
        val encrypted = encrypt(token.trim())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(URL_KEY, normalizedUrl)
            .putString(TOKEN_KEY, encrypted)
            .apply()
    }

    fun workerUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(URL_KEY, "").orEmpty()

    fun token(context: Context): String {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TOKEN_KEY, null)
            ?: return ""
        return decrypt(encoded)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val combined = Base64.decode(value, Base64.NO_WRAP)
        require(combined.size > 12) { "رمز الربط المخزن غير صالح." }
        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }
}
