package com.alex.minilauncher

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

typealias LP = LinearLayout.LayoutParams

/** Công tắc to, bo tròn, bật = sáng rõ, tắt = xám. */
class Toggle(ctx: Context, var on: Boolean, val fg: Int, val onChange: (Boolean) -> Unit) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = if (on) 1f else 0f
    private val ev = ArgbEvaluator()
    init {
        setOnClickListener {
            on = !on; onChange(on)
            ValueAnimator.ofFloat(t, if (on) 1f else 0f).apply {
                duration = 200
                addUpdateListener { t = it.animatedValue as Float; invalidate() }
            }.start()
        }
    }
    override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(context.dp(62), context.dp(36)) }
    override fun onDraw(canvas: Canvas) {
        val W = width.toFloat(); val H = height.toFloat()
        val bright = Color.red(fg) > 128
        p.color = ev.evaluate(t, Color.argb(0x55, 0x90, 0x90, 0x90), alphaOf(fg, 0xDD)) as Int
        canvas.drawRoundRect(0f, 0f, W, H, H / 2, H / 2, p)
        p.color = ev.evaluate(t, 0xFFB4B4B4.toInt(), if (bright) 0xFF111111.toInt() else Color.WHITE) as Int
        canvas.drawCircle(H / 2 + t * (W - H), H / 2, H / 2 - context.dp(4), p)
    }
}

fun styleSeek(sb: SeekBar, fg: Int) {
    val ctx = sb.context
    val track = GradientDrawable().apply { cornerRadius = ctx.dp(6).toFloat(); setColor(alphaOf(fg, 0x44)); setSize(0, ctx.dp(8)) }
    val prog = ClipDrawable(GradientDrawable().apply { cornerRadius = ctx.dp(6).toFloat(); setColor(fg); setSize(0, ctx.dp(8)) },
        Gravity.START, ClipDrawable.HORIZONTAL)
    sb.progressDrawable = LayerDrawable(arrayOf(track, prog)).apply { setId(0, android.R.id.background); setId(1, android.R.id.progress) }
    sb.thumb = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(fg); setSize(ctx.dp(26), ctx.dp(26)) }
    sb.splitTrack = false
    sb.setPadding(ctx.dp(13), 0, ctx.dp(13), 0)
}

class SettingsActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val s = prefs(); val fg = setupWindow(true)
        val soft = alphaOf(fg, 0x22)
        val inv = if (Color.red(fg) > 128) Color.BLACK else Color.WHITE
        val palette = listOf(0L, 0xFFFFFFFF, 0xFF000000, 0xFF9E9E9E, 0xFFFF5252, 0xFFFF9800, 0xFFFFD54F,
            0xFF69F0AE, 0xFF40C4FF, 0xFFB388FF, 0xFFFF80AB).map { it.toInt() }

        fun rb(r: Int, c: Int) = GradientDrawable().apply { cornerRadius = dp(r).toFloat(); setColor(c) }
        fun lp(bottom: Int = 10) = LP(-1, -2).apply { bottomMargin = dp(bottom) }
        fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
        fun section(t: String) = TextView(this).apply {
            text = t; setTextColor(fg); alpha = .65f; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .06f; setPadding(dp(6), dp(26), 0, dp(10))
        }
        fun label(t: String) = TextView(this).apply { text = t; setTextColor(fg); textSize = 16f }

        fun sw(text: String, key: String, def: Boolean, reload: Boolean = false): View = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(16), dp(14))
            background = rb(22, soft); layoutParams = lp()
            addView(label(text), LP(0, -2, 1f))
            val tg = Toggle(context, s.getBoolean(key, def), fg) { s.edit().putBoolean(key, it).apply(); if (reload) recreate() }
            addView(tg, LP(dp(62), dp(36)))
            setOnClickListener { tg.performClick() }
        }

        fun seek(text: String, key: String, lo: Int, hi: Int, def: Int, unit: String): View = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(12))
            background = rb(22, soft); layoutParams = lp()
            val v = s.getInt(key, def)
            val t = label("$text: $v$unit")
            val sb = SeekBar(context).apply {
                max = hi - lo; progress = v - lo; styleSeek(this, fg)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, p: Int, u: Boolean) { t.text = "$text: ${p + lo}$unit" }
                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) { s.edit().putInt(key, bar!!.progress + lo).apply() }
                })
            }
            addView(t); addView(sb, LP(-1, dp(40)).apply { topMargin = dp(6) })
        }

        fun chips(text: String, key: String, opts: List<String>, def: Int): View = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(16))
            background = rb(22, soft); layoutParams = lp()
            addView(label(text))
            val row = LinearLayout(context); val views = ArrayList<TextView>()
            fun paint() {
                val cur = s.getInt(key, def)
                views.forEachIndexed { i, v ->
                    v.setTextColor(if (i == cur) inv else fg)
                    v.background = rb(22, if (i == cur) fg else soft)
                }
            }
            opts.forEachIndexed { i, o ->
                val tv = TextView(context).apply {
                    this.text = o; textSize = 14f; gravity = Gravity.CENTER; setPadding(dp(18), dp(12), dp(18), dp(12))
                    setOnClickListener { s.edit().putInt(key, i).apply(); paint() }; pressAnim()
                }
                views.add(tv); row.addView(tv, LP(-2, -2).apply { rightMargin = dp(8) })
            }
            paint()
            addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(row) }, LP(-1, -2).apply { topMargin = dp(12) })
        }

        fun swatches(text: String, key: String): View = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(16))
            background = rb(22, soft); layoutParams = lp()
            addView(label(text))
            val row = LinearLayout(context); val views = ArrayList<View>()
            fun paint() {
                val cur = s.getInt(key, 0)
                views.forEachIndexed { i, v ->
                    val c = palette[i]
                    v.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        if (c == 0) { orientation = GradientDrawable.Orientation.LEFT_RIGHT; colors = intArrayOf(Color.WHITE, Color.BLACK) } else setColor(c)
                        setStroke(dp(if (c == cur) 3 else 1), if (c == cur) fg else Color.GRAY)
                    }
                }
            }
            palette.forEachIndexed { i, c ->
                val v = View(context); views.add(v)
                v.setOnClickListener { s.edit().putInt(key, c).apply(); paint() }; v.pressAnim()
                row.addView(v, LP(dp(46), dp(46)).apply { rightMargin = dp(12) })
            }
            paint()
            addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(row) }, LP(-1, -2).apply { topMargin = dp(12) })
        }

        fun btn(t: String, onClick: () -> Unit) = TextView(this).apply {
            text = t; setTextColor(inv); textSize = 16f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(20), dp(18), dp(20), dp(18)); background = rb(28, fg); layoutParams = lp()
            setOnClickListener { onClick() }; pressAnim()
        }

        fun picker(): View {
            val cur = s.getString("wall", null) ?: if (s.getBoolean("black", false)) "black" else "system"
            return HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false; background = rb(22, soft); layoutParams = lp()
                setPadding(dp(14), dp(14), dp(14), dp(12))
                addView(LinearLayout(context).apply {
                    Wall.opts.forEach { o ->
                        addView(LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                            addView(FrameLayout(context).apply {
                                setPadding(dp(3), dp(3), dp(3), dp(3))
                                background = GradientDrawable().apply {
                                    cornerRadius = dp(16).toFloat()
                                    setStroke(dp(2), if (o.id == cur) fg else Color.TRANSPARENT)
                                }
                                addView(ImageView(context).apply {
                                    setImageBitmap(Wall.thumb(o.id)); scaleType = ImageView.ScaleType.CENTER_CROP
                                    outlineProvider = object : ViewOutlineProvider() {
                                        override fun getOutline(v: View, out: Outline) { out.setRoundRect(0, 0, v.width, v.height, dp(13).toFloat()) }
                                    }
                                    clipToOutline = true
                                }, FrameLayout.LayoutParams(dp(68), dp(120)))
                            })
                            addView(TextView(context).apply {
                                text = o.name; setTextColor(fg); textSize = 12f; gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0)
                            })
                            setOnClickListener { s.edit().putString("wall", o.id).apply(); recreate() }
                            pressAnim()
                            layoutParams = LP(-2, -2).apply { rightMargin = dp(10) }
                        })
                    }
                })
            }
        }

        fun applyWall(flag: Int, ok: String) {
            val wall = s.getString("wall", null) ?: "system"
            if (wall == "system") { toast("Hãy chọn một hình nền có sẵn ở trên trước"); return }
            Thread {
                try {
                    val bmp = Wall.full(this, wall)
                    WallpaperManager.getInstance(this).setBitmap(bmp, null, true, flag)
                    runOnUiThread { toast(ok) }
                } catch (e: Exception) { runOnUiThread { toast("Không đặt được: ${e.message}") } }
            }.start()
        }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(28), dp(18), dp(40)) }
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(Ic(context, 2, fg).apply { setOnClickListener { finish() }; pressAnim() }, LP(dp(48), dp(48)))
            addView(TextView(context).apply { text = "Cài đặt"; setTextColor(fg); textSize = 24f; setPadding(dp(8), 0, 0, 0) })
        })

        col.addView(section("GIAO DIỆN"))
        col.addView(sw("Chế độ sáng", "light", false, true))
        col.addView(picker())
        col.addView(seek("Độ mờ nền", "dim", 30, 100, 65, "%"))
        col.addView(btn("Đặt làm hình nền màn hình chính") { applyWall(WallpaperManager.FLAG_SYSTEM, "Đã đặt hình nền màn hình chính") })
        col.addView(btn("Đặt làm hình nền khóa màn hình") { applyWall(WallpaperManager.FLAG_LOCK, "Đã đặt hình nền khóa màn hình") })

        col.addView(section("MÀN HÌNH CHÍNH"))
        col.addView(sw("Tự động chia thư mục", "folders", false))
        col.addView(sw("Hiện mục Hay dùng", "usedsec", true))
        col.addView(btn("Xóa lịch sử Hay dùng") {
            val ed = s.edit(); s.all.keys.filter { it.startsWith("u_") }.forEach { ed.remove(it) }; ed.apply()
            toast("Đã xóa lịch sử")
        })
        col.addView(seek("Số cột", "cols", 3, 6, 4, ""))
        col.addView(sw("Hiện tên ứng dụng", "labels", true))
        col.addView(sw("Hiệu ứng chuyển động", "anim", true))

        col.addView(section("BIỂU TƯỢNG"))
        col.addView(chips("Hình dạng icon", "ishape", listOf("Gốc", "Tròn", "Vuông bo", "Squircle"), 0))
        col.addView(seek("Độ bo góc (Vuông bo)", "icorner", 0, 50, 28, "%"))
        col.addView(seek("Cỡ biểu tượng", "icon", 36, 80, 56, "dp"))

        col.addView(section("THÔNG BÁO"))
        col.addView(sw("Dấu chấm thông báo", "dots", false))
        col.addView(btn("Cấp / quản lý quyền đọc thông báo") {
            try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (e: Exception) { toast("Máy không mở được trang này") }
        })

        col.addView(section("SAO LƯU"))
        col.addView(btn("Sao lưu cài đặt ra file") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, "minilauncher-backup.json"), 101)
        })
        col.addView(btn("Khôi phục cài đặt từ file") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 102)
        })

        col.addView(section("ĐỒNG HỒ"))
        col.addView(sw("Hiện đồng hồ", "clock", true))
        col.addView(sw("Hiện giờ", "ch", true))
        col.addView(sw("Hiện phút", "cm", true))
        col.addView(sw("Hiện giây", "cs", true))
        col.addView(sw("Giây nhỏ hơn", "csmall", true))
        col.addView(sw("Định dạng 24 giờ", "c24", true))
        col.addView(sw("Hiện ngày", "cdate", true))
        col.addView(swatches("Màu đồng hồ", "ccol"))
        col.addView(swatches("Màu ngày", "dcol"))
        col.addView(seek("Cỡ đồng hồ", "csize", 30, 96, 54, "sp"))
        col.addView(seek("Cỡ ngày", "dsize", 10, 26, 15, "sp"))
        col.addView(chips("Kiểu chữ", "cfont", listOf("Mảnh", "Nhẹ", "Thường", "Đậm", "Mono"), 1))
        col.addView(chips("Căn lề", "calign", listOf("Trái", "Giữa", "Phải"), 0))
        col.addView(chips("Kiểu ngày", "cdf", listOf("Thứ, ngày tháng", "Ngắn", "Đầy đủ", "dd/MM/yyyy"), 0))

        col.addView(section("KHÁC"))
        col.addView(btn("Đặt làm màn hình chính mặc định") {
            try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (e: Exception) {}
        })
        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (res != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        val s = prefs()
        try {
            if (req == 101) {
                val o = JSONObject()
                for ((k, v) in s.all) {
                    val e = JSONObject()
                    when (v) {
                        is Boolean -> { e.put("t", "b"); e.put("v", v) }
                        is Int -> { e.put("t", "i"); e.put("v", v) }
                        is Long -> { e.put("t", "l"); e.put("v", v) }
                        is Float -> { e.put("t", "f"); e.put("v", v.toDouble()) }
                        is String -> { e.put("t", "s"); e.put("v", v) }
                        is Set<*> -> { e.put("t", "ss"); e.put("v", JSONArray(v.map { it.toString() })) }
                        else -> continue
                    }
                    o.put(k, e)
                }
                contentResolver.openOutputStream(uri)?.use { it.write(o.toString().toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, "Đã sao lưu", Toast.LENGTH_SHORT).show()
            } else if (req == 102) {
                val txt = contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                val o = JSONObject(txt); val ed = s.edit().clear()
                o.keys().forEach { k ->
                    val e = o.getJSONObject(k)
                    when (e.getString("t")) {
                        "b" -> ed.putBoolean(k, e.getBoolean("v"))
                        "i" -> ed.putInt(k, e.getInt("v"))
                        "l" -> ed.putLong(k, e.getLong("v"))
                        "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                        "s" -> ed.putString(k, e.getString("v"))
                        "ss" -> { val ar = e.getJSONArray("v"); ed.putStringSet(k, (0 until ar.length()).map { ar.getString(it) }.toSet()) }
                    }
                }
                ed.apply(); Toast.makeText(this, "Đã khôi phục", Toast.LENGTH_SHORT).show(); recreate()
            }
        } catch (e: Exception) { Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_LONG).show() }
    }
}
