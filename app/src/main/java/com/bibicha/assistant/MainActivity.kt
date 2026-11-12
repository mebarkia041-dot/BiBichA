package com.bibicha.assistant

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 42)
        }
        val title = TextView(this).apply {
            text = "💜 بيبيشا\nمساعدتك الذكية"
            textSize = 28f
            setPadding(24, 48, 24, 24)
        }
        val status = TextView(this).apply {
            text = "هذه نسخة Android أولية. يمكنك طلب تعيين بيبيشا كمساعد الهاتف إذا كان جهازك يدعم ذلك."
            textSize = 16f
            setPadding(24, 16, 24, 24)
        }
        val activate = Button(this).apply {
            text = "تعيين بيبيشا كمساعد الهاتف"
            setOnClickListener { requestAssistantRole() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(title)
            addView(status)
            addView(activate)
        })
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
    }

    companion object { private const val REQUEST_ASSISTANT = 41 }
}
