package com.alex.minilauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*

class SettingsActivity : Activity() {
    val BGS = listOf(0xFF101418, 0xFF000000, 0xFF1E1B2E, 0xFF0F2A24, 0xFF2B2B2B, 0xFFF5F5F0).map { it.toInt() }
    val ACCS = listOf(0xFF4FC3F7, 0xFFFF7043, 0xFF81C784, 0xFFBA68C8, 0xFFFFD54F, 0xFFEF5350).map { it.toInt() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val s = prefs()
        val bg = s.getInt("bg", DEF_BG); val ac = s.getInt("acc", DEF_ACC)
        val fg = if (isLight(bg)) Color.BLACK else Color.WHITE
        applyBars(bg)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(24), dp(20), dp(24)) }

        fun title(t: String) = TextView(this).apply {
            text = t; setTextColor(ac); textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(20), 0, dp(8))
        }
        fun swatches(list: List<Int>, key: String, cur: Int) = HorizontalScrollView(this).apply {
            addView(LinearLayout(context).apply {
                list.forEach { c ->
                    addView(View(context).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL; setColor(c)
                            setStroke(dp(if (c == cur) 3 else 1), if (c == cur) fg else Color.GRAY)
                        }
                        setOnClickListener { s.edit().putInt(key, c).apply(); recreate() }
                    }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(12) })
                }
            })
        }
        fun seek(label: String, key: String, lo: Int, hi: Int, def: Int, unit: String) {
            val v = s.getInt(key, def)
            val t = TextView(this).apply { setTextColor(fg); text = "$label: $v$unit" }
            val sb = SeekBar(this).apply {
                max = hi - lo; progress = v - lo
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, p: Int, u: Boolean) { t.text = "$label: ${p + lo}$unit" }
                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) { s.edit().putInt(key, bar!!.progress + lo).apply() }
                })
            }
            col.addView(t); col.addView(sb)
        }

        col.addView(TextView(this).apply { text = "Cài đặt giao diện"; setTextColor(fg); textSize = 22f })
        col.addView(title("Màu nền")); col.addView(swatches(BGS, "bg", bg))
        col.addView(title("Màu nhấn")); col.addView(swatches(ACCS, "acc", ac))
        col.addView(title("Bố cục"))
        seek("Số cột", "cols", 3, 6, 4, "")
        seek("Cỡ biểu tượng", "icon", 36, 80, 56, "dp")
        col.addView(Switch(this).apply {
            text = "Hiện tên ứng dụng"; setTextColor(fg); isChecked = s.getBoolean("labels", true)
            setPadding(0, dp(16), 0, dp(16))
            setOnCheckedChangeListener { _, c -> s.edit().putBoolean("labels", c).apply() }
        })
        col.addView(pill("Đặt làm màn hình chính mặc định", ac, fg) {
            try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (e: Exception) {}
        })
        setContentView(ScrollView(this).apply { setBackgroundColor(bg); addView(col) })
    }
}
