package com.bibicha.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class VoiceHomeActivity : Activity() {
    private var recognizer: SpeechRecognizer? = null
    private var speaker: TextToSpeech? = null
    private var speakerReady = false
    private lateinit var status: TextView
    private lateinit var orb: EmiliaOrbView
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        speaker = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                val candidates = listOf(Locale("ar", "DZ"), Locale("ar"))
                speakerReady = candidates.any { locale ->
                    (speaker?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
                }
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.BLACK)
            setPadding(24, 26, 24, 18)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val sparkle = TextView(this).apply {
            text = "✦"
            textSize = 27f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.rgb(167, 54, 210))
            }
            layoutParams = LinearLayout.LayoutParams(54, 54)
        }
        val brand = TextView(this).apply {
            text = "Emilia  ●\nمساعدتك الذكية"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        header.addView(sparkle)
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(brand)
        root.addView(header, LinearLayout.LayoutParams(-1, 60))

        val spacerTop = View(this)
        root.addView(spacerTop, LinearLayout.LayoutParams(1, 0, 1f))

        orb = EmiliaOrbView(this).apply {
            setOnClickListener { startListening() }
            contentDescription = "اضغط للتحدث مع إميليا"
        }
        root.addView(orb, LinearLayout.LayoutParams(280, 280))

        status = TextView(this).apply {
            text = "أنا هنا، احكيلي واش تحتاج ❤️"
            textSize = 18f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(12, 26, 12, 12)
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2))

        val hint = TextView(this).apply {
            text = "اضغط على الكرة وابدأ الكلام"
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }
        root.addView(hint, LinearLayout.LayoutParams(-1, 42))

        val settings = Button(this).apply {
            text = "☰  الإعدادات والذاكرة والربط"
            setOnClickListener { startActivity(Intent(this@VoiceHomeActivity, MainActivity::class.java)) }
        }
        root.addView(settings, LinearLayout.LayoutParams(-1, 52))

        val spacerBottom = View(this)
        root.addView(spacerBottom, LinearLayout.LayoutParams(1, 0, 0.45f))
        setContentView(root)
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 701)
            status.text = "اسمحلي بالميكروفون باش نسمعك"
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "التعرف على الصوت غير متاح في هذا الهاتف"
            return
        }
        recognizer?.destroy()
        status.text = "راني نسمع فيك..."
        orb.setListening(true)
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { speech ->
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status.text = "تفضّل، راني نسمع 💜" }
                override fun onBeginningOfSpeech() { orb.setListening(true) }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status.text = "لحظة نفهمك..." }
                override fun onError(error: Int) {
                    orb.setListening(false)
                    status.text = "ما سمعتكش مليح. اضغط على الكرة وعاود."
                }
                override fun onResults(results: Bundle?) {
                    orb.setListening(false)
                    val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (phrase.isBlank()) {
                        status.text = "ما وصلنيش كلام واضح، عاود من فضلك."
                    } else {
                        status.text = "قلت: $phrase"
                        handlePhrase(phrase)
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
                putExtra(RecognizerIntent.EXTRA_PROMPT, "تكلم مع إميليا")
            }
            speech.startListening(intent)
        }
    }

    private fun handlePhrase(phrase: String) {
        val normalized = phrase.lowercase(Locale.ROOT)
        if (normalized.contains("يوتيوب") || normalized.contains("youtube")) {
            val launch = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            if (launch != null) {
                startActivity(launch)
                speak("حاضر، نفتحلك يوتيوب.")
            } else speak("تطبيق يوتيوب ما لقيتوش في هاتفك.")
            return
        }
        if (normalized.contains("الإعدادات") || normalized.contains("اعدادات")) {
            startActivity(Intent(this, MainActivity::class.java))
            speak("نفتحلك إعدادات إميليا.")
            return
        }
        askCloudAssistant(phrase)
    }

    private fun askCloudAssistant(message: String) {
        val worker = SecureConfig.workerUrl(this).trimEnd('/')
        val token = try { SecureConfig.token(this) } catch (_: Exception) { "" }
        if (token.isBlank()) {
            status.text = "الذكاء الاصطناعي يحتاج إعداد رمز الربط من الإعدادات"
            speak("كمّل إعداد الربط من صفحة الإعدادات باش نقدر نجاوبك بذكاء اصطناعي حقيقي.")
            return
        }
        status.text = "إميليا راهي تفكر..."
        orb.setListening(true)
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
                val payload = JSONObject()
                    .put("message", message)
                    .put("history", SecureConfig.history(this))
                    .put("memory", SecureConfig.memories(this))
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val json = JSONObject(body.ifBlank { "{}" })
                val answer = if (code in 200..299) json.optString("reply", "ما وصلنيش رد واضح.")
                    else json.optString("error", "تعذر الاتصال بالخادم.")
                if (code in 200..299) {
                    SecureConfig.addHistory(this, "user", message)
                    SecureConfig.addHistory(this, "assistant", answer)
                }
                main.post {
                    orb.setListening(false)
                    status.text = answer
                    speak(answer)
                }
            } catch (_: Exception) {
                main.post {
                    orb.setListening(false)
                    status.text = "ما قدرتش نتصل بالخادم. تحقق من الإنترنت وإعدادات الربط."
                    speak("ما قدرتش نتصل بالذكاء الاصطناعي. تحقق من الإنترنت وإعدادات الربط.")
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun speak(message: String) {
        if (speakerReady) speaker?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "emilia-voice-reply")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 701 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
    }

    override fun onDestroy() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        speaker?.stop()
        speaker?.shutdown()
        speaker = null
        super.onDestroy()
    }
}

private class EmiliaOrbView(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var listening = false
    private val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2400
        repeatCount = android.animation.ValueAnimator.INFINITE
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init { animator.start() }

    fun setListening(active: Boolean) {
        listening = active
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val base = minOf(width, height) * 0.34f
        val pulse = if (listening) 1f + 0.055f * kotlin.math.sin(phase * Math.PI * 2).toFloat()
                    else 1f + 0.018f * kotlin.math.sin(phase * Math.PI * 2).toFloat()
        val radius = base * pulse

        paint.shader = RadialGradient(cx, cy, radius * 1.75f,
            intArrayOf(Color.argb(80, 0, 140, 255), Color.argb(0, 0, 90, 220)),
            floatArrayOf(0.35f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius * 1.75f, paint)
        paint.shader = null

        val path = Path()
        val points = 72
        for (i in 0 until points) {
            val angle = i * Math.PI * 2 / points
            val wobble = 1f + 0.035f * kotlin.math.sin(angle * 3 + phase * Math.PI * 2).toFloat() +
                0.018f * kotlin.math.cos(angle * 5 - phase * Math.PI * 2).toFloat()
            val x = cx + kotlin.math.cos(angle).toFloat() * radius * wobble
            val y = cy + kotlin.math.sin(angle).toFloat() * radius * wobble
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        paint.shader = LinearGradient(cx - radius, cy - radius, cx + radius, cy + radius,
            Color.rgb(29, 142, 239), Color.rgb(24, 112, 214), Shader.TileMode.CLAMP)
        canvas.drawPath(path, paint)
        paint.shader = null

        paint.shader = RadialGradient(cx - radius * 0.25f, cy - radius * 0.28f, radius * 0.8f,
            intArrayOf(Color.argb(95, 180, 225, 255), Color.argb(0, 50, 160, 255)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
