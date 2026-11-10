package com.alex.minilauncher

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.*

fun tintSwitch(sw: Switch, fg: Int) {
    val on = intArrayOf(android.R.attr.state_checked)
    val off = intArrayOf(-android.R.attr.state_checked)
    sw.thumbTintList = ColorStateList(arrayOf(on, off), intArrayOf(fg, 0xFF8A8A8A.toInt()))
    sw.trackTintList = ColorStateList(arrayOf(on, off),
        intArrayOf(Color.argb(0xAA, Color.red(fg), Color.green(fg), Color.blue(fg)), Color.argb(0x66, 0x70, 0x70, 0x70)))
}

class SettingsActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val s = prefs(); val fg = setupWindow(true)
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
            sb.progressTintList = ColorStateList.valueOf(fg); sb.thumbTintList = ColorStateList.valueOf(fg)
            col.addView(t); col.addView(sb)
        }
        fun sw(label: String, key: String, def: Boolean, reload: Boolean) = Switch(this).apply {
            text = label; setTextColor(fg); isChecked = s.getBoolean(key, def); setPadding(0, dp(14), 0, dp(14))
            tintSwitch(this, fg)
            setOnCheckedChangeListener { _, c -> s.edit().putBoolean(key, c).apply(); if (reload) recreate() }
        }

        fun picker(): View {
            val cur = s.getString("wall", null) ?: if (s.getBoolean("black", false)) "black" else "system"
            return HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(LinearLayout(context).apply {
                    Wall.opts.forEach { o ->
                        addView(LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                            addView(FrameLayout(context).apply {
                                setPadding(dp(3), dp(3), dp(3), dp(3))
                                background = GradientDrawable().apply {
                                    cornerRadius = dp(14).toFloat()
                                    setStroke(dp(2), if (o.id == cur) fg else Color.TRANSPARENT)
                                }
                                addView(ImageView(context).apply {
                                    setImageBitmap(Wall.thumb(o.id)); scaleType = ImageView.ScaleType.CENTER_CROP
                                    outlineProvider = object : ViewOutlineProvider() {
                                        override fun getOutline(v: View, out: Outline) { out.setRoundRect(0, 0, v.width, v.height, dp(11).toFloat()) }
                                    }
                                    clipToOutline = true
                                }, FrameLayout.LayoutParams(dp(64), dp(114)))
                            })
                            addView(TextView(context).apply {
                                text = o.name; setTextColor(fg); textSize = 11f; gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0)
                            })
                            setOnClickListener { s.edit().putString("wall", o.id).apply(); recreate() }
                            pressAnim()
                            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) }
                        })
                    }
                })
            }
        }

        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(Ic(context, 2, fg).apply { setOnClickListener { finish() }; pressAnim() }, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(TextView(context).apply { text = "Cài đặt"; setTextColor(fg); textSize = 22f; setPadding(dp(8), 0, 0, 0) })
        })
        col.addView(sw("Chế độ sáng", "light", false, true))
        col.addView(TextView(this).apply { text = "Hình nền"; setTextColor(fg); alpha = .7f; textSize = 14f; setPadding(0, dp(18), 0, dp(8)) })
        col.addView(picker())
        seek("Độ mờ nền", "dim", 30, 100, 65, "%", true)
        seek("Số cột", "cols", 3, 6, 4, "", false)
        seek("Cỡ biểu tượng", "icon", 36, 80, 56, "dp", false)
        col.addView(sw("Hiện tên ứng dụng", "labels", true, false))
        col.addView(sw("Hiệu ứng chuyển động", "anim", true, false))
        col.addView(sw("Hiện đồng hồ", "clock", true, false))
        setContentView(ScrollView(this).apply { addView(col) })
    }
}
