package com.bibicha.assistant

import android.Manifest
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.service.voice.VoiceInteractionSession
import android.view.View
import android.widget.TextView
import java.util.Locale

class BibichaVoiceInteractionSession(context: android.content.Context) : VoiceInteractionSession(context) {
    private var recognizer: SpeechRecognizer? = null
    private var speaker: TextToSpeech? = null
    private var statusView: TextView? = null
    private var speakerReady = false
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var sessionActive = false

    override fun onCreateContentView(): View {
        statusView = TextView(context).apply {
            text = "💜 بيبيشا\nأنا نسمع فيك..."
            textSize = 22f
            setPadding(32, 32, 32, 32)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        return statusView!!
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        sessionActive = true
        if (speaker == null) {
            speaker = TextToSpeech(context) { result ->
                if (result == TextToSpeech.SUCCESS) {
                    val ar = speaker?.setLanguage(Locale("ar", "DZ")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                    if (ar < TextToSpeech.LANG_AVAILABLE) speaker?.setLanguage(Locale("ar"))
                    speakerReady = true
                }
            }
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            say("التعرف على الصوت غير متاح في هذا الهاتف.")
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            say("اسمح لبيبيشا باستعمال الميكروفون من إعدادات التطبيق أولاً.")
            return
        }
        startListening()
    }

    private fun startListening() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { speech ->
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    statusView?.text = "💜 بيبيشا\nراني نسمع..."
                }
                override fun onBeginningOfSpeech() {
                    statusView?.text = "نسمع في كلامك..."
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    statusView?.text = "لحظة نفهمك..."
                }
                override fun onError(error: Int) {
                    statusView?.text = "ما سمعتكش مليح. عاود ناديني."
                    say("ما سمعتكش مليح، عاود ناديني.")
                }
                override fun onResults(results: Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    handleCommand(spoken)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-DZ")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-DZ")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "تكلم مع بيبيشا")
            }
            speech.startListening(intent)
        }
    }

    private fun handleCommand(raw: String) {
        val command = raw.lowercase(Locale.ROOT)
        statusView?.text = "قلت: $raw"
        val targets = listOf(
            listOf("واتساب", "whatsapp") to "com.whatsapp",
            listOf("فيسبوك", "facebook") to "com.facebook.katana",
            listOf("انستغرام", "انستجرام", "instagram") to "com.instagram.android",
            listOf("يوتيوب", "youtube") to "com.google.android.youtube",
            listOf("خرائط", "maps", "غوغل مابس") to "com.google.android.apps.maps",
            listOf("كروم", "chrome", "متصفح") to "com.android.chrome",
            listOf("رسائل", "messages", "ميساج") to "com.google.android.apps.messaging",
            listOf("الهاتف", "اتصال", "phone") to "com.google.android.dialer"
        )
        if (command.contains("كاميرا") || command.contains("تصوير") || command.contains("camera")) {
            val camera = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            if (camera.resolveActivity(context.packageManager) != null) {
                camera.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(camera)
                say("حاضر، نفتحلك الكاميرا.")
            } else {
                say("ما قدرتش نلقى تطبيق كاميرا متاح.")
            }
            return
        }
        val target = targets.firstOrNull { pair -> pair.first.any { command.contains(it) } }
        if (target != null) {
            val launch = context.packageManager.getLaunchIntentForPackage(target.second)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                say("حاضر، نفتحلك التطبيق.")
                return
            }
            say("ما لقيتش هذا التطبيق مثبت في هاتفك.")
            return
        }
        if (command.contains("إعدادات") || command.contains("settings")) {
            context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            say("نفتحلك الإعدادات.")
            return
        }
        askBiBichA(raw)
    }

    private fun askBiBichA(message: String) {
        val worker = SecureConfig.workerUrl(context)
        val token = try { SecureConfig.token(context) } catch (_: Exception) { "" }
        if (worker.isBlank() || token.isBlank()) {
            say("باش نهدر معاك بالذكاء الحقيقي، لازم تكمل إعداد رابط الخادم ورمز الربط في تطبيق بيبيشا.")
            return
        }
        statusView?.text = "بيبيشا راهي تفكر..."
        Thread {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("$worker/chat").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer $token")
                }
                val priorHistory = SecureConfig.history(context)
                val payload = JSONObject().put("message", message).put("history", priorHistory)
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val json = JSONObject(response.ifBlank { "{}" })
                val answer = if (code in 200..299) {
                    json.optString("reply", "ما وصلنيش رد واضح.")
                } else {
                    json.optString("error", "تعذر الاتصال بالخادم. تحقق من إعدادات الربط.")
                }
                if (code in 200..299) {
                    SecureConfig.addHistory(context, "user", message)
                    SecureConfig.addHistory(context, "assistant", answer)
                }
                mainHandler.post { if (sessionActive) say(answer) }
            } catch (_: Exception) {
                mainHandler.post {
                    if (sessionActive) say("ما قدرتش نتصل بالذكاء الاصطناعي. تحقق من الإنترنت ورابط الخادم.")
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun say(message: String) {
        statusView?.text = "💜 بيبيشا\n$message"
        if (speakerReady) speaker?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "bibicha-response")
    }

    override fun onHide() {
        sessionActive = false
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        super.onHide()
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        speaker?.stop()
        speaker?.shutdown()
        speaker = null
        super.onDestroy()
    }
}
