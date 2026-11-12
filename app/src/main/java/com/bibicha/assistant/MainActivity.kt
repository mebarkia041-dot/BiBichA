package com.bibicha.assistant

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
            addView(configTitle)
            addView(configHint)
            addView(workerUrl)
            addView(token)
            addView(saveConfig)
            addView(clearHistory)
        }
        setContentView(ScrollView(this).apply { addView(content) })
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

    companion object { private const val REQUEST_ASSISTANT = 41; private const val REQUEST_CALL_SCREENING = 43 }
}
