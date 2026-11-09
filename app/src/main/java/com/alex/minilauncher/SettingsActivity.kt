package com.alex.minilauncher

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.*

class SettingsActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val s = prefs(); val fg = setupWindow()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(28), dp(20), dp(32)) }

        fun seek(label: String, key: String, lo: Int, hi: Int, def: Int, unit: String, reload: Boolean) {
            val v = s.getInt(key, def)
            val t = TextView(this).apply { setTextColor(fg); text = "$label: $v$unit"; setPadding(0, dp(18), 0, 0) }
            val sb = SeekBar(this).apply {
                max = hi - lo; progress = v - lo
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, p: Int, u: Boolean) { t.text = "$label: ${p + lo}$unit" }
                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) {
                        s.edit().putInt(key, bar!!.progress + lo).apply(); if (reload) recreate()
                    }
                })
            }
            col.addView(t); col.addView(sb)
        }
        fun sw(label: String, key: String, def: Boolean, reload: Boolean) = Switch(this).apply {
            text = label; setTextColor(fg); isChecked = s.getBoolean(key, def); setPadding(0, dp(14), 0, dp(14))
            setOnCheckedChangeListener { _, c -> s.edit().putBoolean(key, c).apply(); if (reload) recreate() }
        }

        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(Ic(context, 2, fg).apply { setOnClickListener { finish() }; pressAnim() }, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(TextView(context).apply { text = "Cài đặt"; setTextColor(fg); textSize = 22f; setPadding(dp(8), 0, 0, 0) })
        })
        col.addView(sw("Chế độ sáng", "light", false, true))
        seek("Độ mờ nền", "dim", 30, 100, 65, "%", true)
        seek("Số cột", "cols", 3, 6, 4, "", false)
        seek("Cỡ biểu tượng", "icon", 36, 80, 56, "dp", false)
        col.addView(sw("Hiện tên ứng dụng", "labels", true, false))
        col.addView(sw("Hiệu ứng chuyển động", "anim", true, false))
        setContentView(ScrollView(this).apply { addView(col) })
    }
}
