package com.bibicha.assistant

import android.app.Notification
import android.app.KeyguardManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.util.Locale

class BibichaNotificationListener : NotificationListenerService() {
    private var tts: TextToSpeech? = null
    private var ready = false

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val arabic = tts?.setLanguage(Locale("ar", "DZ")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                val language = if (arabic < TextToSpeech.LANG_AVAILABLE) tts?.setLanguage(Locale("ar")) ?: TextToSpeech.LANG_NOT_SUPPORTED else arabic
                ready = language >= TextToSpeech.LANG_AVAILABLE
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || !ready || !SecureConfig.notificationReadingEnabled(this)) return
        val messagingApps = setOf(
            "com.whatsapp",
            "com.google.android.apps.messaging",
            "com.facebook.orca",
            "org.telegram.messenger"
        )
        if (sbn.packageName !in messagingApps) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val combined = "$title $text".lowercase(Locale.ROOT)
        if (listOf("رمز التحقق", "رمز التأكيد", "verification code", "one-time password", "otp").any { combined.contains(it) }) return
        val app = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Exception) { "تطبيق" }
        val announcement = buildString {
            append("إشعار جديد من ")
            append(if (title.isNotBlank()) title else app)
            if (text.isNotBlank()) {
                append(". ")
                append(text.take(350))
            }
        }
        tts?.speak(announcement, TextToSpeech.QUEUE_ADD, null, "notification-${sbn.key}")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
