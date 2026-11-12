package com.bibicha.assistant

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private var chatLogView: TextView? = null
    private var chatStatusView: TextView? = null
    private var chatSendButton: Button? = null
    private var chatTts: TextToSpeech? = null
    private var chatTtsReady = false
    private var speakNextReply = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatTts = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                val arabic = chatTts?.setLanguage(Locale("ar", "DZ")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                val language = if (arabic < TextToSpeech.LANG_AVAILABLE) chatTts?.setLanguage(Locale("ar")) ?: TextToSpeech.LANG_NOT_SUPPORTED else arabic
                chatTtsReady = language >= TextToSpeech.LANG_AVAILABLE
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 42)
        }

        val title = TextView(this).apply {
            text = "💜 بيبيشا\nمساعدتك الذكية"
            textSize = 28f
            setPadding(24, 36, 24, 16)
        }
        val status = TextView(this).apply {
            text = "نسخة أولية: أوامر صوتية لفتح التطبيقات وقراءة إشعارات المراسلة بعد تفعيل الإذن."
            textSize = 16f
            setPadding(24, 12, 24, 16)
        }
        val activate = Button(this).apply {
            text = "تعيين بيبيشا كمساعد الهاتف"
            setOnClickListener { requestAssistantRole() }
        }
        val notifications = Button(this).apply {
            text = "تفعيل قراءة الإشعارات بصوت"
            setOnClickListener {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
        }
        val callerId = Button(this).apply {
            text = "تفعيل إعلان المكالمات الواردة"
            setOnClickListener { requestCallScreeningRole() }
        }

        val notificationPrivacy = Button(this).apply {
            fun refreshLabel() {
                text = if (SecureConfig.notificationReadingEnabled(this@MainActivity)) {
                    "إيقاف قراءة محتوى الإشعارات"
                } else {
                    "السماح لبيبيشا بقراءة الإشعارات"
                }
            }
            refreshLabel()
            setOnClickListener {
                val enabled = !SecureConfig.notificationReadingEnabled(this@MainActivity)
                SecureConfig.setNotificationReadingEnabled(this@MainActivity, enabled)
                refreshLabel()
                Toast.makeText(
                    this@MainActivity,
                    if (enabled) "تفعّلت القراءة الصوتية. يلزم أيضاً إذن الإشعارات من إعدادات Android."
                    else "توقفت بيبيشا عن قراءة محتوى الإشعارات.",
                    Toast.LENGTH_LONG
                ).show()
                if (enabled) {
                    startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                }
            }
        }
        val emergencyTitle = TextView(this).apply {
            text = "مشاركة الموقع في الطوارئ"
            textSize = 20f
            setPadding(8, 20, 8, 8)
        }
        val emergencyNumber = EditText(this).apply {
            hint = "رقم شخص موثوق للطوارئ"
            inputType = InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
            setText(SecureConfig.emergencyNumber(this@MainActivity))
        }
        val saveEmergency = Button(this).apply {
            text = "حفظ رقم الطوارئ"
            setOnClickListener {
                try {
                    SecureConfig.saveEmergencyNumber(this@MainActivity, emergencyNumber.text.toString())
                    Toast.makeText(this@MainActivity, "تم حفظ رقم الطوارئ مشفّراً.", Toast.LENGTH_LONG).show()
                } catch (error: Exception) {
                    Toast.makeText(this@MainActivity, error.message ?: "رقم غير صالح.", Toast.LENGTH_LONG).show()
                }
            }
        }
        val shareLocation = Button(this).apply {
            text = "تحضير رسالة موقعي للطوارئ"
            setOnClickListener { requestEmergencyLocation() }
        }

        val configTitle = TextView(this).apply {
            text = "ربط الذكاء الاصطناعي"
            textSize = 20f
            setPadding(8, 20, 8, 8)
        }
        val configHint = TextView(this).apply {
            text = "أدخل رابط Worker ورمز الربط الذي تضبطه في Cloudflare. لا تضع مفتاح OpenAI هنا."
            textSize = 14f
            setPadding(8, 0, 8, 8)
        }
        val workerUrl = EditText(this).apply {
            hint = "https://اسم-العامل.workers.dev"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(SecureConfig.workerUrl(this@MainActivity))
        }
        val token = EditText(this).apply {
            hint = "رمز ربط Android"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val saveConfig = Button(this).apply {
            text = "حفظ إعدادات الربط بأمان"
            setOnClickListener {
                try {
                    SecureConfig.save(this@MainActivity, workerUrl.text.toString(), token.text.toString())
                    token.text?.clear()
                    Toast.makeText(this@MainActivity, "تم حفظ الرابط والرمز مشفّراً على الهاتف.", Toast.LENGTH_LONG).show()
                } catch (error: Exception) {
                    Toast.makeText(this@MainActivity, error.message ?: "تعذر حفظ الإعدادات.", Toast.LENGTH_LONG).show()
                }
            }
        }
        val testConnection = Button(this).apply {
            text = "اختبار اتصال خادم بيبيشا"
            setOnClickListener { testWorkerConnection(workerUrl.text.toString(), status) }
        }
        val clearHistory = Button(this).apply {
            text = "مسح ذاكرة المحادثة المحلية"
            setOnClickListener {
                SecureConfig.clearHistory(this@MainActivity)
                chatLogView?.text = "تم مسح المحادثة. نبدأو من جديد 💜"
                Toast.makeText(this@MainActivity, "تم مسح سجل المحادثة المحفوظ على الهاتف.", Toast.LENGTH_LONG).show()
            }
        }

        val memoryTitle = TextView(this).apply {
            text = "الذاكرة الدائمة المشفّرة"
            textSize = 20f
            setPadding(8, 20, 8, 8)
        }
        val memoryInput = EditText(this).apply {
            hint = "مثلاً: أفضل الردود بالدارجة الجزائرية"
            minLines = 2
        }
        val saveMemory = Button(this).apply {
            text = "احفظ المعلومة في ذاكرة بيبيشا"
            setOnClickListener {
                try {
                    SecureConfig.addMemory(this@MainActivity, memoryInput.text.toString())
                    memoryInput.text?.clear()
                    Toast.makeText(this@MainActivity, "تحفظت المعلومة بأمان.", Toast.LENGTH_LONG).show()
                } catch (error: Exception) {
                    Toast.makeText(this@MainActivity, error.message ?: "تعذر حفظ المعلومة.", Toast.LENGTH_LONG).show()
                }
            }
        }
        val clearMemory = Button(this).apply {
            text = "مسح الذاكرة الدائمة"
            setOnClickListener {
                SecureConfig.clearMemories(this@MainActivity)
                Toast.makeText(this@MainActivity, "تم مسح الذاكرة الدائمة.", Toast.LENGTH_LONG).show()
            }
        }

        val chatTitle = TextView(this).apply {
            text = "محادثة بيبيشا 💜"
            textSize = 22f
            setPadding(8, 24, 8, 8)
        }
        val chatStatus = TextView(this).apply {
            text = "اكتبي رسالة لبيبيشا بعد إعداد رابط الخادم."
            textSize = 14f
            setPadding(8, 4, 8, 8)
        }
        val chatLog = TextView(this).apply {
            textSize = 16f
            setPadding(12, 12, 12, 12)
            setTextIsSelectable(true)
        }
        val chatInput = EditText(this).apply {
            hint = "اكتبي رسالتك هنا..."
            minLines = 2
            maxLines = 4
        }
        val sendChat = Button(this).apply {
            text = "إرسال إلى بيبيشا"
            setOnClickListener {
                val message = chatInput.text.toString().trim()
                if (message.isBlank()) {
                    chatInput.error = "اكتبي رسالة أولاً"
                } else {
                    chatInput.text?.clear()
                    sendChatMessage(message, chatLog, chatStatus, this)
                }
            }
        }
        val voiceChat = Button(this).apply {
            text = "🎙️ تكلمي مع بيبيشا"
            setOnClickListener { startVoiceInput() }
        }
        chatLogView = chatLog
        chatStatusView = chatStatus
        chatSendButton = sendChat
        fun refreshChatLog() {
            val history = SecureConfig.history(this@MainActivity)
            val lines = ArrayList<String>()
            for (index in 0 until history.length()) {
                val item = history.optJSONObject(index) ?: continue
                val role = if (item.optString("role") == "user") "أنت" else "بيبيشا"
                lines.add("$role: ${item.optString("content")}")
            }
            chatLog.text = lines.joinToString("\n\n").ifBlank { "مازال ما بديناش المحادثة. قوليلي سلام 💜" }
        }
        refreshChatLog()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 24)
            addView(title)
            addView(chatTitle)
            addView(chatStatus)
            addView(chatLog)
            addView(chatInput)
            addView(sendChat)
            addView(voiceChat)
            addView(status)
            addView(activate)
            addView(notifications)
            addView(notificationPrivacy)
            addView(callerId)
            addView(emergencyTitle)
            addView(emergencyNumber)
            addView(saveEmergency)
            addView(shareLocation)
            addView(configTitle)
            addView(configHint)
            addView(workerUrl)
            addView(token)
            addView(saveConfig)
            addView(testConnection)
            addView(clearHistory)
            addView(memoryTitle)
            addView(memoryInput)
            addView(saveMemory)
            addView(clearMemory)
        }
        setContentView(ScrollView(this).apply { addView(content) })
        if (intent.getBooleanExtra(EXTRA_EMERGENCY_SHARE, false)) requestEmergencyLocation()
    }

    private fun testWorkerConnection(inputUrl: String, statusView: TextView) {
        val base = inputUrl.trim().trimEnd('/').removeSuffix("/chat")
        if (!base.startsWith("https://") || !base.endsWith(".workers.dev")) {
            statusView.text = "أدخل رابط Worker صحيحاً ينتهي بـ .workers.dev."
            return
        }
        statusView.text = "راني نختبر اتصال الخادم..."
        Thread {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("$base/health").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Cache-Control", "no-cache")
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val data = JSONObject(body.ifBlank { "{}" })
                if (code !in 200..299) throw IllegalStateException("الخادم رجّع الخطأ $code.")
                val configured = data.optBoolean("configured", false)
                runOnUiThread {
                    statusView.text = if (configured) {
                        "الخادم متصل ومفتاح الذكاء الاصطناعي موجود. هذا الاختبار لا يتحقق من رمز الهاتف."
                    } else {
                        "الخادم متصل، لكن مفتاح OpenAI API غير مضبوط في Cloudflare."
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    statusView.text = error.message ?: "تعذر الوصول للخادم. تحقق من الرابط والإنترنت."
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun startVoiceInput() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
            chatStatusView?.text = "اسمح باستعمال الميكروفون ثم عاود الضغط."
            return
        }
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            chatStatusView?.text = "التعرف على الصوت غير متاح في هذا الهاتف."
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-DZ")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-DZ")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "تكلم مع بيبيشا")
        }
        try {
            startActivityForResult(intent, REQUEST_VOICE_CHAT)
        } catch (_: Exception) {
            chatStatusView?.text = "ما قدرناش نفتحو التعرف على الصوت."
        }
    }

    private fun sendChatMessage(message: String, chatLog: TextView, chatStatus: TextView, sendButton: Button) {
        val worker = SecureConfig.workerUrl(this)
        val token = try { SecureConfig.token(this) } catch (_: Exception) { "" }
        if (worker.isBlank() || token.isBlank()) {
            chatStatus.text = "أكملي إعداد رابط Worker ورمز Android أولاً."
            return
        }
        sendButton.isEnabled = false
        chatStatus.text = "بيبيشا راهي تكتبلك..."
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
                if (code !in 200..299) {
                    throw IllegalStateException(json.optString("error", "تعذر الاتصال بالخادم ($code)."))
                }
                val reply = json.optString("reply").trim()
                if (reply.isBlank()) throw IllegalStateException("ما وصلنيش رد واضح من بيبيشا.")
                SecureConfig.addHistory(this, "user", message)
                SecureConfig.addHistory(this, "assistant", reply)
                runOnUiThread {
                    val history = SecureConfig.history(this)
                    val lines = ArrayList<String>()
                    for (index in 0 until history.length()) {
                        val item = history.optJSONObject(index) ?: continue
                        val role = if (item.optString("role") == "user") "أنت" else "بيبيشا"
                        lines.add("$role: ${item.optString("content")}")
                    }
                    chatLog.text = lines.joinToString("\n\n")
                    chatStatus.text = "وصل الرد 💜"
                    if (speakNextReply && chatTtsReady) {
                        chatTts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "bibicha-chat-reply")
                    }
                    speakNextReply = false
                }
            } catch (error: Exception) {
                runOnUiThread {
                    chatStatus.text = error.message ?: "صار خطأ في الاتصال. عاودي المحاولة."
                    speakNextReply = false
                }
            } finally {
                connection?.disconnect()
                runOnUiThread { sendButton.isEnabled = true }
            }
        }.start()
    }

    override fun onDestroy() {
        chatTts?.stop()
        chatTts?.shutdown()
        chatTts = null
        super.onDestroy()
    }

    private fun requestEmergencyLocation() {
        if (SecureConfig.emergencyNumber(this).isBlank()) {
            Toast.makeText(this, "احفظ رقم شخص موثوق للطوارئ أولاً.", Toast.LENGTH_LONG).show()
            return
        }
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_LOCATION
            )
            return
        }
        findCurrentLocation()
    }

    private fun findCurrentLocation() {
        val manager = getSystemService(LocationManager::class.java) ?: return
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val recent = try {
            providers.mapNotNull { provider -> manager.getLastKnownLocation(provider) }
                .filter { System.currentTimeMillis() - it.time <= 120_000L }
                .maxByOrNull { it.time }
        } catch (_: SecurityException) { null }
        if (recent != null) {
            prepareEmergencyMessage(recent)
            return
        }

        val timeoutHandler = Handler(Looper.getMainLooper())
        var timeoutAction: Runnable? = null
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                timeoutAction?.let { timeoutHandler.removeCallbacks(it) }
                try { manager.removeUpdates(this) } catch (_: Exception) {}
                prepareEmergencyMessage(location)
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated by Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        var requested = false
        for (provider in providers) {
            try {
                if (manager.isProviderEnabled(provider)) {
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    requested = true
                }
            } catch (_: Exception) {}
        }
        if (requested) {
            timeoutAction = Runnable {
                try { manager.removeUpdates(listener) } catch (_: Exception) {}
                Toast.makeText(this, "ما قدرناش نحددو موقعك. فعّل GPS وعاود.", Toast.LENGTH_LONG).show()
            }
            timeoutHandler.postDelayed(timeoutAction!!, 20_000L)
        } else {
            Toast.makeText(this, "فعّل GPS أو خدمة الموقع ثم عاود.", Toast.LENGTH_LONG).show()
        }
    }

    private fun prepareEmergencyMessage(location: Location) {
        val number = SecureConfig.emergencyNumber(this).map { digit ->
            when (digit) {
                '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                else -> digit
            }
        }.joinToString("")
        val mapLink = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
        val message = "أنا في حالة طارئة، هذا موقعي: $mapLink"
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
            putExtra("sms_body", message)
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
            Toast.makeText(this, "حضّرنا رسالة الموقع. اضغط إرسال بعد التأكد من المستلم.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "ما لقيناش تطبيق رسائل لإرسال الموقع.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.any { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                findCurrentLocation()
            } else {
                Toast.makeText(this, "لازم تسمح بالموقع باش نحضّر رسالة الطوارئ.", Toast.LENGTH_LONG).show()
            }
        }
        if (requestCode == REQUEST_MICROPHONE) {
            if (grantResults.any { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                startVoiceInput()
            } else {
                chatStatusView?.text = "الميكروفون ما تفعلش. تقدر تسمح به من إعدادات التطبيق."
            }
        }
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, "ميزة إعلان المتصل تحتاج Android 10 أو أحدث.", Toast.LENGTH_LONG).show()
            return
        }
        val roles = getSystemService(RoleManager::class.java)
        if (roles == null || !roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, "هذا الهاتف لا يتيح دور فحص المكالمات.", Toast.LENGTH_LONG).show()
            return
        }
        if (roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, "بيبيشا مفعّلة بالفعل لإعلان المكالمات.", Toast.LENGTH_SHORT).show()
            return
        }
        @Suppress("DEPRECATION")
        startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQUEST_CALL_SCREENING)
    }

    private fun requestAssistantRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, "تعيين المساعد الافتراضي بهذه الطريقة يحتاج Android 10 أو أحدث.", Toast.LENGTH_LONG).show()
            return
        }
        val roles = getSystemService(RoleManager::class.java)
        if (roles == null || !roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            Toast.makeText(this, "هذا الجهاز لا يتيح دور المساعد عبر هذه الواجهة.", Toast.LENGTH_LONG).show()
            return
        }
        if (roles.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            Toast.makeText(this, "بيبيشا معيّنة بالفعل كمساعد الهاتف.", Toast.LENGTH_SHORT).show()
            return
        }
        @Suppress("DEPRECATION")
        startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), REQUEST_ASSISTANT)
    }

    @Deprecated("Android role result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VOICE_CHAT) {
            if (resultCode == RESULT_OK) {
                val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
                if (spoken.isNotBlank()) {
                    chatStatusView?.text = "سمعتك: $spoken"
                    speakNextReply = true
                    val log = chatLogView
                    val status = chatStatusView
                    val button = chatSendButton
                    if (log != null && status != null && button != null) sendChatMessage(spoken, log, status, button)
                } else {
                    chatStatusView?.text = "ما وصلنيش كلام واضح. عاود."
                }
            } else {
                chatStatusView?.text = "توقّف الاستماع. تقدر تعاود وقت تحب."
            }
        }
        if (requestCode == REQUEST_ASSISTANT) {
            val message = if (resultCode == RESULT_OK) "تم قبول تعيين بيبيشا كمساعد." else "لم يتم تغيير المساعد."
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        if (requestCode == REQUEST_CALL_SCREENING) {
            val message = if (resultCode == RESULT_OK) "تم تفعيل إعلان المكالمات الواردة." else "لم يتم تفعيل ميزة إعلان المكالمات."
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    companion object { private const val REQUEST_ASSISTANT = 41; private const val REQUEST_CALL_SCREENING = 43; private const val REQUEST_LOCATION = 44; private const val REQUEST_VOICE_CHAT = 45; private const val REQUEST_MICROPHONE = 46; const val EXTRA_EMERGENCY_SHARE = "bibicha_emergency_share" }
}
