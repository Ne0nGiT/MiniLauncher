package com.alex.minilauncher

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.AlarmClock
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.text.Normalizer

class AppItem(val label: String, val pkg: String, val icon: Drawable) {
    fun dr(): Drawable = icon.constantState?.newDrawable() ?: icon
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
fun Context.prefs() = getSharedPreferences("p", 0)
fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace('đ', 'd')
fun letterOf(s: String): Char {
    val c = norm(s).firstOrNull()?.uppercaseChar()
    return if (c != null && c in 'A'..'Z') c else '#'
}

class Calc(val s: String) {
    var i = 0
    fun eval(): Double? = try { val v = expr(); if (i == s.length) v else null } catch (e: Exception) { null }
    private fun peek(): Char = if (i < s.length) s[i] else '\u0000'
    private fun expr(): Double {
        var v = term()
        while (peek() == '+' || peek() == '-') { val o = s[i++]; val r = term(); v = if (o == '+') v + r else v - r }
        return v
    }
    private fun term(): Double {
        var v = pow()
        while (peek() == '*' || peek() == '/' || peek() == '%') {
            val o = s[i++]; val r = pow()
            v = when (o) { '*' -> v * r; '/' -> v / r; else -> v % r }
        }
        return v
    }
    private fun pow(): Double { val b = unary(); return if (peek() == '^') { i++; Math.pow(b, pow()) } else b }
    private fun unary(): Double = when (peek()) { '-' -> { i++; -unary() }; '+' -> { i++; unary() }; else -> atom() }
    private fun atom(): Double {
        if (peek() == '(') { i++; val v = expr(); if (peek() != ')') throw Exception(); i++; return v }
        val st = i
        while (peek().isDigit() || peek() == '.') i++
        if (st == i) throw Exception()
        return s.substring(st, i).toDouble()
    }
}

fun calc(q: String): String? {
    val s = q.replace(" ", "").replace(',', '.').replace('x', '*').replace('×', '*').replace(':', '/')
    if (s.length < 3 || s.none { it in "+-*/^%" } || s.none { it.isDigit() }) return null
    val v = Calc(s).eval() ?: return null
    if (v.isNaN() || v.isInfinite()) return null
    return if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString()
    else String.format(java.util.Locale.US, "%.8f", v).trimEnd('0').trimEnd('.')
}

/** Nền theo cài đặt (plain = màn Cài đặt: nền riêng, không lộ hình nền hệ thống). Trả về màu chữ. */
fun Activity.setupWindow(plain: Boolean = false): Int {
    val p = prefs()
    val lightPref = p.getBoolean("light", false)
    val wall = p.getString("wall", null) ?: if (p.getBoolean("black", false)) "black" else "system"
    val light: Boolean
    if (plain) {
        light = lightPref
        window.setBackgroundDrawable(if (lightPref) ColorDrawable(0xFFF2F2F2.toInt()) else Wall.window(this, "black", 100))
    } else if (wall == "system") {
        light = lightPref
        val base = if (light) 255 else 0
        window.setBackgroundDrawable(ColorDrawable(Color.argb(p.getInt("dim", 65) * 255 / 100, base, base, base)))
    } else {
        light = Wall.isLight(wall)
        window.setBackgroundDrawable(Wall.window(this, wall, p.getInt("dim", 65)))
    }
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
    var f = 0
    if (light) {
        f = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (Build.VERSION.SDK_INT >= 26) f = f or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }
    window.decorView.systemUiVisibility = f
    return if (light) Color.BLACK else Color.WHITE
}

fun View.pressAnim() {
    if (!context.prefs().getBoolean("anim", true)) return
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(.9f).scaleY(.9f).setDuration(90).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(OvershootInterpolator()).start()
        }
        false
    }
}

fun Context.pill(t: String, fg: Int, onClick: () -> Unit) = TextView(this).apply {
    text = t; setTextColor(fg); textSize = 15f; gravity = Gravity.CENTER
    setPadding(dp(14), dp(14), dp(14), dp(14))
    background = GradientDrawable().apply {
        cornerRadius = dp(24).toFloat(); setColor(Color.argb(0x26, Color.red(fg), Color.green(fg), Color.blue(fg)))
    }
    setOnClickListener { onClick() }
    pressAnim()
}

/** Icon vẽ tay: 0 = tìm kiếm, 1 = ba chấm, 2 = quay lại */
class Ic(ctx: Context, val kind: Int, val color: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val s = minOf(w, h)
        p.color = color; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = s * 0.09f
        when (kind) {
            0 -> {
                p.style = Paint.Style.STROKE
                canvas.drawCircle(w * 0.44f, h * 0.44f, s * 0.26f, p)
                canvas.drawLine(w * 0.63f, h * 0.63f, w * 0.84f, h * 0.84f, p)
            }
            1 -> {
                p.style = Paint.Style.FILL
                for (i in 0..2) canvas.drawCircle(w / 2, h * (0.25f + 0.25f * i), s * 0.075f, p)
            }
            else -> {
                p.style = Paint.Style.STROKE
                canvas.drawLine(w * 0.75f, h * 0.5f, w * 0.28f, h * 0.5f, p)
                canvas.drawLine(w * 0.28f, h * 0.5f, w * 0.5f, h * 0.28f, p)
                canvas.drawLine(w * 0.28f, h * 0.5f, w * 0.5f, h * 0.72f, p)
            }
        }
    }
}

class MainActivity : Activity() {
    var apps = listOf<AppItem>()
    var fg = Color.WHITE
    val letters = ('A'..'Z').toList() + '#'
    val letterRow = HashMap<Char, View>()
    lateinit var scroll: ScrollView
    lateinit var search: EditText
    lateinit var favTitle: TextView
    lateinit var favBox: LinearLayout
    lateinit var allTitle: TextView
    lateinit var allBox: LinearLayout
    lateinit var index: LinearLayout
    lateinit var bubble: TextView
    lateinit var clockBox: LinearLayout

    override fun onResume() { super.onResume(); setContentView(build()); load() }
    override fun onNewIntent(intent: Intent?) { super.onNewIntent(intent); reset() }
    override fun onBackPressed() { reset() }

    fun reset() {
        if (!::search.isInitialized) return
        search.setText(""); search.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(search.windowToken, 0)
        scroll.smoothScrollTo(0, 0)
    }

    fun anim() = prefs().getBoolean("anim", true)
    fun favs() = prefs().getString("favs", "")!!.split(",").filter { it.isNotEmpty() }
    fun hidden() = prefs().getString("hidden", "")!!.split(",").filter { it.isNotEmpty() }
    fun favApps(): List<AppItem> { val h = hidden(); return favs().mapNotNull { p -> apps.find { it.pkg == p && it.pkg !in h } } }
    fun setList(k: String, l: List<String>) { prefs().edit().putString(k, l.joinToString(",")).apply(); refresh(false) }
    fun query() = norm(search.text.toString().trim())
    fun results(): List<AppItem> { val q = query(); return if (q.isEmpty()) { val h = hidden(); apps.filter { it.pkg !in h } } else apps.filter { norm(it.label).contains(q) } }

    fun load() = Thread {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = packageManager.queryIntentActivities(i, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { AppItem(it.loadLabel(packageManager).toString(), it.activityInfo.packageName, it.loadIcon(packageManager)) }
            .distinctBy { it.pkg }
            .sortedWith(compareBy<AppItem>({ letterOf(it.label) == '#' }, { norm(it.label) }))
        runOnUiThread { if (list.map { it.pkg } != apps.map { it.pkg }) { apps = list; refresh(true) } }
    }.start()

    fun launch(a: AppItem, v: View?) {
        val i = packageManager.getLaunchIntentForPackage(a.pkg) ?: return
        if (v != null && anim()) startActivity(i, ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.width, v.height).toBundle())
        else startActivity(i)
    }

    fun shortcutsOf(pkg: String): List<ShortcutInfo> {
        if (Build.VERSION.SDK_INT < 25) return emptyList()
        return try {
            val la = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            if (!la.hasShortcutHostPermission()) return emptyList()
            val q = LauncherApps.ShortcutQuery().setPackage(pkg)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
            (la.getShortcuts(q, Process.myUserHandle()) ?: emptyList()).filter { it.isEnabled }.take(4)
        } catch (e: Exception) { emptyList() }
    }

    fun webSearch(q: String) {
        if (q.isEmpty()) return
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))) } catch (e: Exception) {}
    }

    fun copy(t: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("kq", t))
        Toast.makeText(this, "Đã sao chép", Toast.LENGTH_SHORT).show()
    }

    fun row(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t; setTextColor(fg); textSize = 16f; setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.argb(0x26, Color.red(fg), Color.green(fg), Color.blue(fg))) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        setOnClickListener { onClick() }; pressAnim()
    }

    fun menu(a: AppItem) {
        val sc = shortcutsOf(a.pkg)
        val isFav = favs().contains(a.pkg); val isHid = hidden().contains(a.pkg)
        val names = sc.map { "↗ " + (it.shortLabel ?: it.id) } + listOf(
            if (isFav) "Bỏ khỏi Yêu thích" else "Thêm vào Yêu thích",
            if (isHid) "Bỏ ẩn" else "Ẩn ứng dụng", "Thông tin ứng dụng", "Gỡ cài đặt")
        AlertDialog.Builder(this).setTitle(a.label).setItems(names.toTypedArray()) { _, w ->
            if (w < sc.size) {
                try { (getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).startShortcut(sc[w], null, null) } catch (e: Exception) {}
            } else when (w - sc.size) {
                0 -> { val f = favs().toMutableList(); if (!f.remove(a.pkg)) f.add(a.pkg); setList("favs", f) }
                1 -> {
                    val h = hidden().toMutableList()
                    if (!h.remove(a.pkg)) { h.add(a.pkg); prefs().edit().putString("favs", favs().filter { it != a.pkg }.joinToString(",")).apply() }
                    setList("hidden", h)
                }
                2 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
                3 -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")))
                else -> {}
            }
        }.show()
    }

    fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "Cài đặt"); menu.add(0, 2, 1, "Đặt làm mặc định")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    2 -> try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (e: Exception) {}
                }
                true
            }
            show()
        }
    }

    val pending = ArrayList<Pair<View, Int>>()

    /** Chạy animation SAU khi layout xong, để không bị nuốt mất do khung hình đầu nặng. */
    fun runPending() {
        if (pending.isEmpty()) return
        val list = ArrayList(pending); pending.clear()
        scroll.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                scroll.viewTreeObserver.removeOnPreDrawListener(this)
                list.forEach { (v, i) ->
                    v.animate().alpha(1f).translationY(0f).setStartDelay(minOf(i, 20) * 22L)
                        .setDuration(380).setInterpolator(DecelerateInterpolator(1.6f)).start()
                }
                return true
            }
        })
    }

    fun cell(a: AppItem, idx: Int, anim: Boolean): View {
        val s = prefs(); val size = dp(s.getInt("icon", 56))
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
            addView(ImageView(context).apply { setImageDrawable(a.dr()) }, LinearLayout.LayoutParams(size, size))
            if (s.getBoolean("labels", true)) addView(TextView(context).apply {
                text = a.label; setTextColor(fg); textSize = 11.5f; maxLines = 1
                ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
                setPadding(dp(2), dp(4), dp(2), 0)
            })
            setOnClickListener { launch(a, it) }
            setOnLongClickListener { menu(a); true }
            pressAnim()
            if (anim && idx < 32) { alpha = 0f; translationY = dp(40).toFloat(); pending.add(Pair(this, idx)) }
        }
    }

    fun fill(box: LinearLayout, items: List<AppItem>, cols: Int, anim: Boolean, track: Boolean, base: Int) {
        box.removeAllViews()
        if (track) letterRow.clear()
        var i = 0
        while (i < items.size) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until cols) {
                val k = i + c
                if (k < items.size) {
                    row.addView(cell(items[k], k + base, anim), LinearLayout.LayoutParams(0, -2, 1f))
                    val ch = letterOf(items[k].label)
                    if (track && !letterRow.containsKey(ch)) letterRow[ch] = row
                } else row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
            box.addView(row); i += cols
        }
    }

    fun refresh(anim: Boolean) {
        val searching = query().isNotEmpty()
        val cols = prefs().getInt("cols", 4)
        val vis = if (searching) View.GONE else View.VISIBLE
        favTitle.visibility = vis; favBox.visibility = vis; allTitle.visibility = vis; index.visibility = vis
        clockBox.visibility = if (searching || !prefs().getBoolean("clock", true)) View.GONE else View.VISIBLE
        var base = 0
        if (!searching) {
            val f = favApps(); base = minOf(f.size, 8)
            if (f.isEmpty()) {
                favBox.removeAllViews()
                favBox.addView(TextView(this).apply {
                    text = "Nhấn giữ một app để thêm vào đây"; setTextColor(fg); alpha = .5f
                    textSize = 13f; setPadding(dp(8), 0, 0, dp(4))
                })
            } else fill(favBox, f, cols, anim, false, 0)
        }
        fill(allBox, results(), cols, anim && !searching, !searching, base)
        if (searching) {
            val q = search.text.toString().trim()
            allBox.addView(row("Tìm “$q” trên Google") { webSearch(q) }, 0)
            calc(q)?.let { r -> allBox.addView(row("= $r") { copy(r) }, 0) }
        }
        if (!searching) for (i in letters.indices) index.getChildAt(i).alpha = if (letterRow.containsKey(letters[i])) 1f else .3f
        runPending()
    }

    fun build(): View {
        fg = setupWindow()
        val soft = Color.argb(0x26, Color.red(fg), Color.green(fg), Color.blue(fg))
        fun sec(t: String) = TextView(this).apply {
            text = t; setTextColor(fg); textSize = 15f; setPadding(dp(8), dp(16), 0, dp(10))
        }
        favTitle = sec("Yêu thích"); allTitle = sec("Tất cả ứng dụng")
        favBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        allBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clockBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(20), 0, dp(4))
            addView(TextClock(context).apply {
                format24Hour = "HH:mm"; format12Hour = "HH:mm"; setTextColor(fg); textSize = 54f
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            })
            addView(TextClock(context).apply {
                format24Hour = "EEEE, d MMMM"; format12Hour = "EEEE, d MMMM"; setTextColor(fg); alpha = .7f; textSize = 15f
            })
            setOnClickListener { try { startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS)) } catch (e: Exception) {} }
            pressAnim()
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(26), dp(24))
            addView(clockBox); addView(favTitle); addView(favBox); addView(allTitle); addView(allBox)
        }
        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(content) }

        search = EditText(this).apply {
            hint = "Tìm kiếm"; setTextColor(fg); textSize = 16f
            setHintTextColor(Color.argb(140, Color.red(fg), Color.green(fg), Color.blue(fg)))
            background = null; setSingleLine(); imeOptions = EditorInfo.IME_ACTION_SEARCH
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { refresh(false); scroll.scrollTo(0, 0) }
            })
            setOnEditorActionListener { _, _, _ ->
                val r = results().firstOrNull()
                if (r != null) launch(r, this) else webSearch(text.toString().trim())
                true
            }
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(8), dp(4))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(12), 0)
                background = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(soft) }
                addView(Ic(context, 0, fg), LinearLayout.LayoutParams(dp(22), dp(22)))
                addView(search, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(10) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Ic(context, 1, fg).apply { setOnClickListener { showMenu(it) }; pressAnim() },
                LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(4) })
        }

        index = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            letters.forEach { ch ->
                addView(TextView(context).apply {
                    text = ch.toString(); setTextColor(fg); textSize = 10f; gravity = Gravity.CENTER; alpha = .3f
                }, LinearLayout.LayoutParams(dp(26), dp(17)))
            }
            setOnTouchListener { v, e ->
                if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                    bubble.animate().alpha(0f).setDuration(160).start()
                } else {
                    val pos = ((e.y / v.height) * letters.size).toInt().coerceIn(0, letters.size - 1)
                    var j = pos
                    while (j < letters.size && !letterRow.containsKey(letters[j])) j++
                    if (j >= letters.size) { j = pos; while (j >= 0 && !letterRow.containsKey(letters[j])) j-- }
                    if (j in letters.indices) {
                        scroll.scrollTo(0, letterRow[letters[j]]!!.top + allBox.top)
                        bubble.text = letters[j].toString()
                        bubble.animate().alpha(1f).setDuration(80).start()
                    }
                }
                true
            }
        }
        bubble = TextView(this).apply {
            textSize = 34f; setTextColor(fg); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; alpha = 0f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.argb(0x40, Color.red(fg), Color.green(fg), Color.blue(fg)))
            }
        }
        val root = FrameLayout(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(bar); addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            })
            addView(index, FrameLayout.LayoutParams(dp(26), -2, Gravity.END or Gravity.CENTER_VERTICAL))
            addView(bubble, FrameLayout.LayoutParams(dp(80), dp(80), Gravity.CENTER))
        }
        if (apps.isNotEmpty()) refresh(anim())
        return root
    }
}
