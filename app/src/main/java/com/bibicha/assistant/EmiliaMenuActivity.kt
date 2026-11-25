package com.bibicha.assistant

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class EmiliaMenuActivity : Activity() {
    private val purple = Color.rgb(142, 73, 210)
    private val panel = Color.rgb(19, 18, 22)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(18), dp(18), dp(18), dp(16))
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val close = TextView(this).apply {
            text = "×"; textSize = 30f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
            background = shape(Color.rgb(35,35,39), 100)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setOnClickListener { finish() }
        }
        val account = TextView(this).apply {
            text = "Emilia\nمساعدتك الذكية"; textSize = 19f; setTextColor(Color.WHITE)
            gravity = Gravity.END; setTypeface(null, Typeface.BOLD)
        }
        top.addView(close)
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(account)
        root.addView(top)
        val pro = TextView(this).apply {
            text = "✦   إميليا الذكية\n      مساعدتك الشخصية بالصوت والذكاء الاصطناعي"
            textSize = 15f; setTextColor(Color.WHITE); setPadding(dp(18), dp(18), dp(18), dp(18))
            background = shape(Color.rgb(48, 29, 78), 22)
        }
        val proParams = LinearLayout.LayoutParams(-1, -2); proParams.topMargin = dp(24); proParams.bottomMargin = dp(20)
        root.addView(pro, proParams)
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        section(body, "الدردشة", listOf(
            "✎" to "محادثة جديدة",
            "▢" to "وضع المراسلة",
            "◷" to "سجل المحادثات",
            "🏆" to "السلاسل والأوسمة"
        ))
        section(body, "الأدوات", listOf(
            "◉" to "استكشف",
            "☷" to "تفعيل الأدوات",
            "♫" to "صوت إميليا"
        ))
        section(body, "المجتمع", listOf(
            "☆" to "أعطِ 5 نجوم",
            "✉" to "راسل المطوّر",
            "▣" to "تابع تحديثات إميليا"
        ))
        section(body, "الإعدادات", listOf(
            "⚙" to "الذكاء الاصطناعي والربط الآمن",
            "🧠" to "الذاكرة والمحادثات",
            "🎙" to "الصوت والأذونات",
            "💾" to "النسخ الاحتياطي والاسترجاع",
            "⏰" to "التذكيرات"
        ))
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun section(parent: LinearLayout, heading: String, items: List<Pair<String,String>>) {
        val label = TextView(this).apply {
            text = heading; textSize = 13f; setTextColor(Color.GRAY)
            setPadding(dp(8), dp(14), dp(8), dp(8))
        }
        parent.addView(label)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(panel, 22)
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }
        items.forEachIndexed { index, item ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(13), dp(8), dp(13))
                isClickable = true
                isFocusable = true
                setOnClickListener { startActivity(Intent(this@EmiliaMenuActivity, MainActivity::class.java)) }
            }
            val icon = TextView(this).apply {
                text = item.first; textSize = 20f; setTextColor(purple); gravity = Gravity.CENTER
                background = shape(Color.rgb(43, 28, 57), 14)
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            }
            val title = TextView(this).apply {
                text = item.second; textSize = 16f; setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD); gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val arrow = TextView(this).apply {
                text = "‹"; textSize = 25f; setTextColor(Color.GRAY)
                setPadding(dp(8), 0, dp(8), 0)
            }
            row.addView(arrow); row.addView(View(this), LinearLayout.LayoutParams(dp(8), 1)); row.addView(title)
            row.addView(View(this), LinearLayout.LayoutParams(dp(12), 1)); row.addView(icon)
            card.addView(row)
            if (index != items.lastIndex) {
                card.addView(View(this).apply { setBackgroundColor(Color.rgb(39,39,42)) },
                    LinearLayout.LayoutParams(-1, dp(1)))
            }
        }
        val params = LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(8)
        parent.addView(card, params)
    }

    private fun shape(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
}