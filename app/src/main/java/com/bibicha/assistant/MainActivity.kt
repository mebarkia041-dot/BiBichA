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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        val clearHistory = Button(this).apply {
            text = "مسح ذاكرة المحادثة المحلية"
            setOnClickListener {
                SecureConfig.clearHistory(this@MainActivity)
                Toast.makeText(this@MainActivity, "تم مسح سجل المحادثة المحفوظ على الهاتف.", Toast.LENGTH_LONG).show()
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 24)
            addView(title)
            addView(status)
            addView(activate)
            addView(notifications)
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
            addView(clearHistory)
        }
        setContentView(ScrollView(this).apply { addView(content) })
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

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
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
            Handler(Looper.getMainLooper()).postDelayed({
                try { manager.removeUpdates(listener) } catch (_: Exception) {}
                Toast.makeText(this, "ما قدرناش نحددو موقعك. فعّل GPS وعاود.", Toast.LENGTH_LONG).show()
            }, 20_000L)
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
        if (requestCode == REQUEST_ASSISTANT) {
            val message = if (resultCode == RESULT_OK) "تم قبول تعيين بيبيشا كمساعد." else "لم يتم تغيير المساعد."
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        if (requestCode == REQUEST_CALL_SCREENING) {
            val message = if (resultCode == RESULT_OK) "تم تفعيل إعلان المكالمات الواردة." else "لم يتم تفعيل ميزة إعلان المكالمات."
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    companion object { private const val REQUEST_ASSISTANT = 41; private const val REQUEST_CALL_SCREENING = 43; private const val REQUEST_LOCATION = 44 }
}
