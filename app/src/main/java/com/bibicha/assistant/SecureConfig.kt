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
import org.json.JSONArray
import org.json.JSONObject

object SecureConfig {
    private const val PREFS = "bibicha_secure_config"
    private const val KEY_ALIAS = "bibicha_mobile_token_key"
    private const val URL_KEY = "worker_url"
    private const val TOKEN_KEY = "mobile_token_encrypted"
    private const val HISTORY_KEY = "conversation_history_encrypted"
    private const val MEMORY_KEY = "personal_memory_encrypted"
    private const val EMERGENCY_NUMBER_KEY = "emergency_number_encrypted"
    private const val NOTIFICATION_READING_KEY = "notification_reading_enabled"

    fun save(context: Context, workerUrl: String, token: String) {
        val normalizedUrl = workerUrl.trim().trimEnd('/')
        require(normalizedUrl.startsWith("https://") && normalizedUrl.endsWith(".workers.dev")) {
            "أدخل رابط Cloudflare Worker الصحيح الذي يبدأ بـ https:// وينتهي بـ .workers.dev"
        }
        require(token.trim().length >= 32) { "رمز الربط يجب أن يكون 32 حرفاً على الأقل." }
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

    fun history(context: Context): JSONArray {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(HISTORY_KEY, null)
            ?: return JSONArray()
        return try { JSONArray(decrypt(encoded)) } catch (_: Exception) { JSONArray() }
    }

    fun addHistory(context: Context, role: String, content: String) {
        if (role !in setOf("user", "assistant") || content.isBlank()) return
        val items = history(context)
        items.put(JSONObject().put("role", role).put("content", content.take(4000)))
        val trimmed = JSONArray()
        val start = (items.length() - 12).coerceAtLeast(0)
        for (index in start until items.length()) trimmed.put(items.getJSONObject(index))
        val encrypted = encrypt(trimmed.toString())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(HISTORY_KEY, encrypted)
            .apply()
    }

    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(HISTORY_KEY).apply()
    }

    fun memories(context: Context): JSONArray {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(MEMORY_KEY, null) ?: return JSONArray()
        return try { JSONArray(decrypt(encoded)) } catch (_: Exception) { JSONArray() }
    }

    fun addMemory(context: Context, value: String) {
        val cleaned = value.trim().take(500)
        require(cleaned.length >= 2) { "اكتب معلومة واضحة للحفظ." }
        val items = memories(context)
        if ((0 until items.length()).any { items.optString(it).equals(cleaned, ignoreCase = true) }) return
        items.put(cleaned)
        val trimmed = JSONArray()
        val start = (items.length() - 30).coerceAtLeast(0)
        for (index in start until items.length()) trimmed.put(items.getString(index))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(MEMORY_KEY, encrypt(trimmed.toString())).apply()
    }

    fun clearMemories(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(MEMORY_KEY).apply()
    }

    fun notificationReadingEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(NOTIFICATION_READING_KEY, false)

    fun setNotificationReadingEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(NOTIFICATION_READING_KEY, enabled)
            .apply()
    }

    fun saveEmergencyNumber(context: Context, number: String) {
        val normalized = number.trim()
        require(normalized.matches(Regex("[+0-9٠-٩]{8,16}"))) { "أدخل رقم طوارئ صحيحاً." }
        val encrypted = encrypt(normalized)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(EMERGENCY_NUMBER_KEY, encrypted)
            .apply()
    }

    fun emergencyNumber(context: Context): String {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(EMERGENCY_NUMBER_KEY, null)
            ?: return ""
        return try { decrypt(encoded) } catch (_: Exception) { "" }
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
