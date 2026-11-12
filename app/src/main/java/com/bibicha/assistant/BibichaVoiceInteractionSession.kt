package com.bibicha.assistant

import android.Manifest
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.content.Intent
import android.net.Uri
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
    private var pendingSpeech: String? = null
    private var recognitionErrorCount = 0
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
                    val languageResult = if (ar < TextToSpeech.LANG_AVAILABLE) {
                        speaker?.setLanguage(Locale("ar")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                    } else ar
                    speakerReady = languageResult >= TextToSpeech.LANG_AVAILABLE
                    if (speakerReady) {
                        pendingSpeech?.let { queued ->
                            speaker?.speak(queued, TextToSpeech.QUEUE_FLUSH, null, "bibicha-response")
                            pendingSpeech = null
                        }
                    }
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
                    recognitionErrorCount = 0
                    statusView?.text = "نسمع في كلامك..."
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    statusView?.text = "لحظة نفهمك..."
                }
                override fun onError(error: Int) {
                    if (!sessionActive) return
                    // Retry only a small number of times; never leave a hidden endless listener running.
                    if (error != SpeechRecognizer.ERROR_CLIENT &&
                        error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS &&
                        recognitionErrorCount < 2
                    ) {
                        recognitionErrorCount++
                        statusView?.text = "ما سمعتكش مليح، نعاود نحاول..."
                        mainHandler.postDelayed({ if (sessionActive) startListening() }, 900L)
                    } else {
                        statusView?.text = "ما قدرتش نسمعك. اضغط للاستدعاء وعاود."
                        say("ما قدرتش نسمعك، عاود استدعيني كي تكون واجد.")
                    }
                }
                override fun onResults(results: Bundle?) {
                    recognitionErrorCount = 0
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                    if (spoken.isBlank()) {
                        say("ما وصلني حتى كلام واضح. عاود قولّي واش تحتاج.")
                    } else {
                        handleCommand(spoken)
                    }
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
        if (command.contains("ابعثي موقعي") || command.contains("ابعث موقعي") || command.contains("موقعي للطوارئ")) {
            val emergency = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_EMERGENCY_SHARE, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(emergency)
            say("نفتحلك خطوة مشاركة الموقع. راجع الرسالة واضغط إرسال بنفسك.")
            return
        }
        if (listOf("ابعثي رسالة", "ابعث رسالة", "أرسلي رسالة", "ارسل رسالة", "send message").any { command.contains(it) }) {
            val phoneMatch = Regex("[+0-9٠-٩]{8,}").find(raw)
            if (phoneMatch == null) {
                say("باش نحضرلك رسالة، قوللي رقم الهاتف مع الكلام اللي حاب تبعثو.")
                return
            }
            val phone = phoneMatch.value.map { digit ->
                when (digit) {
                    '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                    '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                    else -> digit
                }
            }.joinToString("")
            val bodyMarker = listOf("قولي", "قوليلو", "الرسالة", "محتوى").firstOrNull { command.contains(it) }
            val body = if (bodyMarker != null) raw.substringAfter(bodyMarker).trim() else raw.substringAfter(phoneMatch.value).trim()
            if (body.isBlank()) {
                say("عطيني نص الرسالة اللي تحب تبعثها.")
                return
            }
            val sendIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
                putExtra("sms_body", body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (sendIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(sendIntent)
                say("حضرتلك الرسالة. راجع الرقم والكلام واضغط إرسال كي تتأكد.")
            } else {
                say("ما لقيتش تطبيق رسائل في هاتفك.")
            }
            return
        }
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
        val systemAction: Intent? = when {
            listOf("منبه", "المنبه", "ساعة", "alarm").any { command.contains(it) } ->
                Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
            listOf("التقويم", "رزنامة", "calendar").any { command.contains(it) } ->
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
            listOf("جهات الاتصال", "كونتاكت", "contacts").any { command.contains(it) } ->
                Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI)
            listOf("واي فاي", "wifi", "ويفي").any { command.contains(it) } ->
                Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            listOf("بلوتوث", "bluetooth").any { command.contains(it) } ->
                Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            listOf("الصوت", "الصوتيات", "volume").any { command.contains(it) } ->
                Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)
            listOf("البطارية", "battery").any { command.contains(it) } ->
                Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS)
            listOf("ابحثي في الانترنت", "ابحث في الانترنت", "فتشي في غوغل", "search web").any { command.contains(it) } -> {
                val query = raw.substringAfterLast("عن", raw).trim()
                Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, query)
            }
            else -> null
        }
        if (systemAction != null) {
            try {
                systemAction.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (systemAction.resolveActivity(context.packageManager) != null) {
                    context.startActivity(systemAction)
                    say("حاضر، نفتحلك المطلوب.")
                } else {
                    say("هذا الخيار ما هوش متاح في هاتفك.")
                }
            } catch (_: Exception) {
                say("ما قدرتش نفتح هذا الخيار. جرّب من إعدادات الهاتف.")
            }
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
                val payload = JSONObject().put("message", message)
                    .put("history", priorHistory)
                    .put("memory", SecureConfig.memories(context))
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
        if (speakerReady) {
            speaker?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "bibicha-response")
        } else {
            pendingSpeech = message
        }
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
