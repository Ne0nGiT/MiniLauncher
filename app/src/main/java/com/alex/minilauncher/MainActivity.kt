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
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.AlarmClock
import android.provider.Settings
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.RelativeSizeSpan
import android.view.DragEvent
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppItem(val label: String, val pkg: String, val icon: Drawable, val cat: String = "Khác") {
    fun dr(): Drawable = icon.constantState?.newDrawable() ?: icon
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
fun Context.prefs() = getSharedPreferences("p", 0)
fun alphaOf(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
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
    else String.format(Locale.US, "%.8f", v).trimEnd('0').trimEnd('.')
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
    var gen = 0
    val handler = Handler(Looper.getMainLooper())
    val letters = ('A'..'Z').toList() + '#'
    val letterRow = HashMap<Char, View>()
    val pending = ArrayList<Pair<View, Int>>()
    val dots = HashMap<String, ArrayList<View>>()
    val iconCache = HashMap<String, Bitmap>()
    var dragMoved = false
    var dragHover = false
    lateinit var useTitle: TextView
    lateinit var useBox: LinearLayout
    lateinit var scroll: ScrollView
    lateinit var search: EditText
    lateinit var favTitle: TextView
    lateinit var favBox: LinearLayout
    lateinit var allTitle: TextView
    lateinit var allBox: LinearLayout
    lateinit var index: LinearLayout
    lateinit var bubble: TextView
    lateinit var clockBox: LinearLayout
    lateinit var clockTv: TextView
    lateinit var dateTv: TextView
    lateinit var loading: ProgressBar
    lateinit var folderLayer: FrameLayout
    lateinit var folderCard: LinearLayout
    lateinit var folderTitle: TextView
    lateinit var folderBox: LinearLayout
    lateinit var folderScroll: ScrollView

    val tickR = object : Runnable {
        override fun run() { updateClock(); handler.postDelayed(this, 1000 - System.currentTimeMillis() % 1000) }
    }

    override fun onResume() {
        super.onResume(); setContentView(build()); load()
        handler.removeCallbacks(tickR); handler.post(tickR)
        Notifs.onChange = { updateDots() }
    }
    override fun onPause() { super.onPause(); handler.removeCallbacks(tickR); Notifs.onChange = null }
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (::folderLayer.isInitialized) folderLayer.visibility = View.GONE
        reset()
    }
    override fun onBackPressed() {
        if (::folderLayer.isInitialized && folderLayer.visibility == View.VISIBLE) closeFolder() else reset()
    }

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
    fun results(): List<AppItem> {
        val q = query()
        return if (q.isEmpty()) { val h = hidden(); apps.filter { it.pkg !in h } } else apps.filter { norm(it.label).contains(q) }
    }
    fun mapPref(k: String): MutableMap<String, String> {
        val m = LinkedHashMap<String, String>()
        prefs().getString(k, "")!!.split("\n").forEach { val i = it.indexOf('='); if (i > 0) m[it.substring(0, i)] = it.substring(i + 1) }
        return m
    }
    fun saveMap(k: String, m: Map<String, String>) {
        prefs().edit().putString(k, m.entries.joinToString("\n") { it.key + "=" + it.value }).apply()
    }
    fun customFolders() = prefs().getString("cfolders", "")!!.split("\n").filter { it.isNotEmpty() }
    fun fname(id: String) = mapPref("fren")[id] ?: id

    fun groups(list: List<AppItem>): List<Pair<String, List<AppItem>>> {
        val cm = mapPref("catmap"); val order = FOLDERS + customFolders()
        fun cat(a: AppItem): String { val c = cm[a.pkg]; return if (c != null && c in order) c else a.cat }
        return order.mapNotNull { f -> list.filter { cat(it) == f }.let { if (it.isEmpty()) null else Pair(f, it) } }
    }

    fun usedApps(n: Int): List<AppItem> {
        val all = prefs().all; val fav = favs(); val h = hidden()
        return apps.filter { it.pkg !in h && it.pkg !in fav }
            .map { Pair(it, (all["u_" + it.pkg] as? Int) ?: 0) }.filter { it.second > 0 }
            .sortedByDescending { it.second }.take(n).map { it.first }
    }

    fun moveFav(src: String, dst: String) {
        val f = favs().toMutableList(); val to = f.indexOf(dst)
        if (to < 0 || !f.remove(src)) return
        f.add(to, src); setList("favs", f)
    }

    /** Icon theo hình dạng người dùng chọn (có cache bitmap). */
    fun icon(a: AppItem, size: Int): Drawable {
        val p = prefs(); val shape = p.getInt("ishape", 0)
        if (shape == 0) return a.dr()
        val pct = p.getInt("icorner", 28)
        val key = a.pkg + "|" + shape + "|" + pct + "|" + size
        val bmp = iconCache.getOrPut(key) { if (iconCache.size > 900) iconCache.clear(); renderIcon(a.dr(), size, shape, pct) }
        return BitmapDrawable(resources, bmp)
    }

    fun dotView(pkg: String): View {
        val v = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(fg)
                setStroke(dp(2), if (Color.red(fg) > 128) Color.BLACK else Color.WHITE)
            }
            visibility = if ((Notifs.counts[pkg] ?: 0) > 0) View.VISIBLE else View.GONE
        }
        dots.getOrPut(pkg) { ArrayList() }.add(v)
        return v
    }

    fun updateDots() {
        val on = prefs().getBoolean("dots", false)
        for ((k, l) in dots) {
            val vis = if (on && (Notifs.counts[k] ?: 0) > 0) View.VISIBLE else View.GONE
            l.forEach { it.visibility = vis }
        }
    }

    fun load() = Thread {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = packageManager.queryIntentActivities(i, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                val lb = it.loadLabel(packageManager).toString()
                AppItem(lb, it.activityInfo.packageName, it.loadIcon(packageManager),
                    classify(it.activityInfo.packageName, lb, it.activityInfo.applicationInfo))
            }
            .distinctBy { it.pkg }
            .sortedWith(compareBy<AppItem>({ letterOf(it.label) == '#' }, { norm(it.label) }))
        runOnUiThread {
            if (list.map { it.pkg } != apps.map { it.pkg }) { apps = list; refresh(true) }
            else if (::loading.isInitialized) loading.visibility = View.GONE
        }
    }.start()

    // ---------- đồng hồ ----------
    fun fmt(pat: String, d: Date) = SimpleDateFormat(pat, Locale.getDefault()).format(d)

    fun updateClock() {
        if (!::clockTv.isInitialized) return
        val p = prefs(); val now = Date()
        val h24 = p.getBoolean("c24", true); val showH = p.getBoolean("ch", true)
        val parts = ArrayList<String>()
        if (showH) parts.add(fmt(if (h24) "HH" else "hh", now))
        if (p.getBoolean("cm", true)) parts.add(fmt("mm", now))
        val sb = SpannableStringBuilder(parts.joinToString(":"))
        if (p.getBoolean("cs", true)) {
            val st = sb.length
            sb.append(if (st == 0) fmt("ss", now) else ":" + fmt("ss", now))
            if (st > 0 && p.getBoolean("csmall", true)) sb.setSpan(RelativeSizeSpan(0.45f), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (!h24 && showH && sb.isNotEmpty()) {
            val st = sb.length
            sb.append(" " + fmt("a", now)); sb.setSpan(RelativeSizeSpan(0.4f), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        clockTv.text = sb
        clockTv.visibility = if (sb.isEmpty()) View.GONE else View.VISIBLE
        val pats = arrayOf("EEEE, d MMMM", "EEE, d/M", "EEEE, d MMMM yyyy", "dd/MM/yyyy")
        dateTv.text = fmt(pats[p.getInt("cdf", 0).coerceIn(0, 3)], now)
    }

    // ---------- hành động ----------
    fun launch(a: AppItem, v: View?) {
        val i = packageManager.getLaunchIntentForPackage(a.pkg) ?: return
        prefs().edit().putInt("u_" + a.pkg, prefs().getInt("u_" + a.pkg, 0) + 1).apply()
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
        background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(alphaOf(fg, 0x26)) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        setOnClickListener { onClick() }; pressAnim()
    }

    fun menu(a: AppItem) {
        val sc = shortcutsOf(a.pkg)
        val isFav = favs().contains(a.pkg); val isHid = hidden().contains(a.pkg)
        val acts = ArrayList<Pair<String, () -> Unit>>()
        fun add(t: String, f: () -> Unit) { acts.add(Pair(t, f)) }
        sc.forEach { si ->
            add("↗ " + (si.shortLabel ?: si.id)) {
                try { (getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).startShortcut(si, null, null) } catch (e: Exception) {}
            }
        }
        add(if (isFav) "Bỏ khỏi Yêu thích" else "Thêm vào Yêu thích") {
            val f = favs().toMutableList(); if (!f.remove(a.pkg)) f.add(a.pkg); setList("favs", f)
        }
        add(if (isHid) "Bỏ ẩn" else "Ẩn ứng dụng") {
            val h = hidden().toMutableList()
            if (!h.remove(a.pkg)) { h.add(a.pkg); prefs().edit().putString("favs", favs().filter { it != a.pkg }.joinToString(",")).apply() }
            setList("hidden", h)
        }
        if (prefs().getBoolean("folders", false)) add("Chuyển thư mục…") { chooseFolder(a) }
        add("Thông tin ứng dụng") { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}"))) }
        add("Gỡ cài đặt") { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}"))) }
        AlertDialog.Builder(this).setTitle(a.label).setItems(acts.map { it.first }.toTypedArray()) { _, w -> acts[w].second() }.show()
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

    // ---------- quản lý thư mục ----------
    fun toastMsg(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    fun askName(title: String, initial: String, ok: (String) -> Unit) {
        val et = EditText(this).apply { setText(initial); setSingleLine(); setPadding(dp(24), dp(16), dp(24), dp(16)) }
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ -> ok(et.text.toString().trim()) }.setNegativeButton("Hủy", null).show()
    }

    fun nameTaken(n: String, except: String? = null): Boolean =
        n in FOLDERS && n != except || (FOLDERS + customFolders()).any { it != except && fname(it).equals(n, true) }

    fun setCat(pkg: String, id: String?) {
        val m = mapPref("catmap"); if (id == null) m.remove(pkg) else m[pkg] = id
        saveMap("catmap", m); folderLayer.visibility = View.GONE; refresh(false)
    }

    fun chooseFolder(a: AppItem) {
        val ids = FOLDERS + customFolders()
        val names = ids.map { fname(it) } + listOf("＋ Thư mục mới…", "↺ Về tự động")
        AlertDialog.Builder(this).setTitle("Chuyển vào thư mục").setItems(names.toTypedArray()) { _, w ->
            if (w < ids.size) setCat(a.pkg, ids[w])
            else if (w == ids.size) askName("Tên thư mục mới", "") { n ->
                if (n.isEmpty() || nameTaken(n)) toastMsg("Tên trống hoặc đã tồn tại")
                else { prefs().edit().putString("cfolders", (customFolders() + n).joinToString("\n")).apply(); setCat(a.pkg, n) }
            }
            else setCat(a.pkg, null)
        }.show()
    }

    fun renameFolder(id: String, n: String) {
        if (n.isEmpty() || nameTaken(n, id)) { toastMsg("Tên trống hoặc đã tồn tại"); return }
        if (id in FOLDERS) { val m = mapPref("fren"); if (n == id) m.remove(id) else m[id] = n; saveMap("fren", m) }
        else {
            prefs().edit().putString("cfolders", customFolders().map { if (it == id) n else it }.joinToString("\n")).apply()
            val cm = mapPref("catmap"); for (k in cm.keys.toList()) if (cm[k] == id) cm[k] = n; saveMap("catmap", cm)
        }
        refresh(false)
    }

    fun deleteFolder(id: String) {
        prefs().edit().putString("cfolders", customFolders().filter { it != id }.joinToString("\n")).apply()
        val cm = mapPref("catmap"); cm.entries.removeAll { it.value == id }; saveMap("catmap", cm); refresh(false)
    }

    fun folderMenu(id: String) {
        val custom = id !in FOLDERS
        val renamed = mapPref("fren").containsKey(id)
        val opts = ArrayList<String>(); opts.add("Đổi tên")
        if (custom) opts.add("Xóa thư mục") else if (renamed) opts.add("Khôi phục tên gốc")
        AlertDialog.Builder(this).setTitle(fname(id)).setItems(opts.toTypedArray()) { _, w ->
            when (w) {
                0 -> askName("Đổi tên thư mục", fname(id)) { n -> renameFolder(id, n) }
                1 -> if (custom) deleteFolder(id) else { val m = mapPref("fren"); m.remove(id); saveMap("fren", m); refresh(false) }
                else -> {}
            }
        }.show()
    }

    // ---------- thư mục ----------
    fun openFolder(name: String, items: List<AppItem>) {
        val cols = prefs().getInt("cols", 4)
        folderTitle.text = fname(name)
        fill(folderBox, items, cols, false, false, 0)
        val cellH = dp(prefs().getInt("icon", 56)) + dp(52)
        val rows = (items.size + cols - 1) / cols
        folderScroll.layoutParams = LinearLayout.LayoutParams(-1, minOf(rows * cellH, (resources.displayMetrics.heightPixels * 0.55f).toInt()))
        folderLayer.visibility = View.VISIBLE
        folderLayer.alpha = 0f; folderLayer.animate().alpha(1f).setDuration(180).start()
        folderCard.scaleX = .85f; folderCard.scaleY = .85f
        folderCard.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(1.1f)).start()
    }

    fun closeFolder() {
        folderLayer.animate().alpha(0f).setDuration(140).withEndAction { folderLayer.visibility = View.GONE }.start()
    }

    // ---------- ô app / thư mục ----------
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

    fun label(t: String, lines: Int) = TextView(this).apply {
        text = t; setTextColor(fg); textSize = 11.5f; maxLines = lines
        ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
        setPadding(dp(2), dp(4), dp(2), 0)
    }

    fun intro(v: View, idx: Int, anim: Boolean) {
        if (anim && idx < 32) { v.alpha = 0f; v.translationY = dp(40).toFloat(); pending.add(Pair(v, idx)) }
    }

    fun cell(a: AppItem, idx: Int, anim: Boolean, fav: Boolean = false): View {
        val s = prefs(); val size = dp(s.getInt("icon", 56))
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
            val box = FrameLayout(context)
            box.addView(ImageView(context).apply { setImageDrawable(icon(a, size)) }, FrameLayout.LayoutParams(size, size))
            if (s.getBoolean("dots", false))
                box.addView(dotView(a.pkg), FrameLayout.LayoutParams(dp(12), dp(12), Gravity.TOP or Gravity.END))
            addView(box, LinearLayout.LayoutParams(size, size))
            if (s.getBoolean("labels", true)) addView(label(a.label, 1))
            setOnClickListener { launch(a, it) }
            if (fav) {
                setOnLongClickListener { v ->
                    dragMoved = false; dragHover = false
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    v.startDragAndDrop(ClipData.newPlainText("fav", a.pkg), View.DragShadowBuilder(v), a.pkg, 0)
                    true
                }
                setOnDragListener { v, e ->
                    when (e.action) {
                        DragEvent.ACTION_DRAG_STARTED -> e.localState is String
                        DragEvent.ACTION_DRAG_ENTERED -> {
                            if (e.localState != a.pkg) { dragHover = true; v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).start() }
                            true
                        }
                        DragEvent.ACTION_DRAG_EXITED -> { v.animate().scaleX(1f).scaleY(1f).setDuration(120).start(); true }
                        DragEvent.ACTION_DROP -> {
                            v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                            val src = e.localState as? String
                            if (src != null && src != a.pkg) { dragMoved = true; moveFav(src, a.pkg) }
                            true
                        }
                        DragEvent.ACTION_DRAG_ENDED -> {
                            v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                            if (e.localState == a.pkg && !dragMoved && !dragHover) menu(a)
                            true
                        }
                        else -> true
                    }
                }
            } else setOnLongClickListener { menu(a); true }
            pressAnim(); intro(this, idx, anim)
        }
    }

    fun folderCell(id: String, items: List<AppItem>, idx: Int, anim: Boolean): View {
        val s = prefs(); val size = dp(s.getInt("icon", 56))
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
            val tile = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dp(5), dp(5), dp(5), dp(5))
                background = GradientDrawable().apply { cornerRadius = size * 0.28f; setColor(alphaOf(fg, 0x30)) }
                val m = (size - dp(10)) / 2
                for (r in 0..1) addView(LinearLayout(context).apply {
                    for (c in 0..1) {
                        val a = items.getOrNull(r * 2 + c)
                        addView(ImageView(context).apply { if (a != null) setImageDrawable(icon(a, m)) }, LinearLayout.LayoutParams(m, m))
                    }
                })
            }
            addView(tile, LinearLayout.LayoutParams(size, size))
            if (s.getBoolean("labels", true)) addView(label(fname(id), 2))
            setOnClickListener { openFolder(id, items) }
            setOnLongClickListener { folderMenu(id); true }
            pressAnim(); intro(this, idx, anim)
        }
    }

    fun rowOf(n: Int, r: Int, cols: Int, cellAt: (Int) -> View): LinearLayout {
        val row = LinearLayout(this)
        for (c in 0 until cols) {
            val k = r * cols + c
            if (k < n) row.addView(cellAt(k), LinearLayout.LayoutParams(0, -2, 1f))
            else row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        }
        return row
    }

    fun appRow(items: List<AppItem>, r: Int, cols: Int, anim: Boolean, track: Boolean, base: Int, fav: Boolean = false): View {
        val row = rowOf(items.size, r, cols) { k -> cell(items[k], k + base, anim, fav) }
        if (track) for (k in r * cols until minOf(items.size, (r + 1) * cols)) {
            val ch = letterOf(items[k].label)
            if (!letterRow.containsKey(ch)) letterRow[ch] = row
        }
        return row
    }

    fun fill(box: LinearLayout, items: List<AppItem>, cols: Int, anim: Boolean, track: Boolean, base: Int, fav: Boolean = false) {
        box.removeAllViews(); if (track) letterRow.clear()
        for (r in 0 until (items.size + cols - 1) / cols) box.addView(appRow(items, r, cols, anim, track, base, fav))
    }

    /** Dựng 7 hàng đầu ngay, phần còn lại chia nhỏ từng đợt để giao diện (và vòng loading) không bị đứng. */
    fun fillChunked(box: LinearLayout, items: List<AppItem>, cols: Int, anim: Boolean, track: Boolean, base: Int, g: Int, done: () -> Unit) {
        box.removeAllViews(); if (track) letterRow.clear()
        val total = (items.size + cols - 1) / cols
        val first = minOf(total, 7)
        for (r in 0 until first) box.addView(appRow(items, r, cols, anim, track, base))
        if (first >= total) { done(); return }
        var next = first
        val step = object : Runnable {
            override fun run() {
                if (g != gen) return
                val end = minOf(total, next + 5)
                for (r in next until end) box.addView(appRow(items, r, cols, false, track, base))
                next = end
                if (next >= total) done() else handler.postDelayed(this, 12)
            }
        }
        handler.postDelayed(step, 12)
    }

    fun refresh(anim: Boolean) {
        val g = ++gen
        val p = prefs()
        val searching = query().isNotEmpty()
        val folderMode = !searching && p.getBoolean("folders", false)
        val cols = p.getInt("cols", 4)
        val vis = if (searching) View.GONE else View.VISIBLE
        favTitle.visibility = vis; favBox.visibility = vis; allTitle.visibility = vis
        index.visibility = if (searching || folderMode) View.GONE else View.VISIBLE
        clockBox.visibility = if (searching || !p.getBoolean("clock", true)) View.GONE else View.VISIBLE
        allTitle.text = if (folderMode) "Thư mục" else "Tất cả ứng dụng"
        val favList = if (searching) emptyList() else favApps()
        val useList = if (searching || !p.getBoolean("usedsec", true)) emptyList() else usedApps(cols)
        val useVis = if (useList.isEmpty()) View.GONE else View.VISIBLE
        useTitle.visibility = useVis; useBox.visibility = useVis
        val base = minOf(favList.size + useList.size, 8)
        if (!searching) {
            if (favList.isEmpty()) {
                favBox.removeAllViews()
                favBox.addView(TextView(this).apply {
                    text = "Nhấn giữ một app để thêm vào đây"; setTextColor(fg); alpha = .5f
                    textSize = 13f; setPadding(dp(8), 0, 0, dp(4))
                })
            } else fill(favBox, favList, cols, anim, false, 0, true)
        }
        if (useList.isNotEmpty()) fill(useBox, useList, cols, anim, false, favList.size)
        val list = results()
        if (folderMode) {
            allBox.removeAllViews()
            val gr = groups(list)
            for (r in 0 until (gr.size + cols - 1) / cols)
                allBox.addView(rowOf(gr.size, r, cols) { k -> folderCell(gr[k].first, gr[k].second, k + base, anim) })
        } else {
            fillChunked(allBox, list, cols, anim && !searching, !searching, base, g) {
                if (!searching) for (i in letters.indices) index.getChildAt(i).alpha = if (letterRow.containsKey(letters[i])) 1f else .3f
            }
        }
        loading.visibility = View.GONE
        runPending()
        if (searching) {
            val q = search.text.toString().trim()
            allBox.addView(row("Tìm “$q” trên Google") { webSearch(q) }, 0)
            calc(q)?.let { r -> allBox.addView(row("= $r") { copy(r) }, 0) }
        }
    }

    // ---------- dựng giao diện ----------
    fun build(): View {
        fg = setupWindow()
        dots.clear()
        val p = prefs()
        val soft = alphaOf(fg, 0x26)
        fun sec(t: String) = TextView(this).apply {
            text = t; setTextColor(fg); textSize = 15f; setPadding(dp(8), dp(16), 0, dp(10))
        }
        favTitle = sec("Yêu thích"); useTitle = sec("Hay dùng"); allTitle = sec("Tất cả ứng dụng")
        favBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        allBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        useBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val fonts = arrayOf("sans-serif-thin", "sans-serif-light", "sans-serif", "sans-serif-black", "monospace")
        val fi = p.getInt("cfont", 1).coerceIn(0, 4)
        val ccol = p.getInt("ccol", 0); val dcol = p.getInt("dcol", 0)
        clockTv = TextView(this).apply {
            setTextColor(if (ccol == 0) fg else ccol); textSize = p.getInt("csize", 54).toFloat()
            typeface = Typeface.create(fonts[fi], Typeface.NORMAL); includeFontPadding = false
        }
        dateTv = TextView(this).apply {
            setTextColor(if (dcol == 0) fg else dcol); alpha = if (dcol == 0) .7f else 1f
            textSize = p.getInt("dsize", 15).toFloat(); typeface = if (fi == 4) Typeface.MONOSPACE else Typeface.DEFAULT
            setPadding(0, dp(4), 0, 0)
            visibility = if (p.getBoolean("cdate", true)) View.VISIBLE else View.GONE
        }
        clockBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(20), dp(8), dp(4))
            gravity = when (p.getInt("calign", 0)) { 1 -> Gravity.CENTER_HORIZONTAL; 2 -> Gravity.END; else -> Gravity.START }
            addView(clockTv); addView(dateTv)
            setOnClickListener { try { startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS)) } catch (e: Exception) {} }
            pressAnim()
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(26), dp(24))
            addView(clockBox); addView(favTitle); addView(favBox); addView(useTitle); addView(useBox); addView(allTitle); addView(allBox)
        }
        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(content) }

        search = EditText(this).apply {
            hint = "Tìm kiếm"; setTextColor(fg); textSize = 16f
            setHintTextColor(alphaOf(fg, 140))
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
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(alphaOf(fg, 0x40)) }
        }
        loading = ProgressBar(this).apply {
            indeterminateTintList = ColorStateList.valueOf(fg)
            visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        }

        val cardBg = if (Color.red(fg) > 128) 0xF2161618.toInt() else 0xF2F4F4F4.toInt()
        folderTitle = TextView(this).apply {
            setTextColor(fg); textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setPadding(dp(6), 0, 0, dp(10))
        }
        folderBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        folderScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(folderBox) }
        folderCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(18), dp(16), dp(12)); isClickable = true
            background = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(cardBg) }
            addView(folderTitle); addView(folderScroll, LinearLayout.LayoutParams(-1, -2))
        }
        folderLayer = FrameLayout(this).apply {
            visibility = View.GONE; setBackgroundColor(0xB3000000.toInt()); isClickable = true
            setOnClickListener { closeFolder() }
            addView(folderCard, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply { setMargins(dp(22), dp(22), dp(22), dp(22)) })
        }

        val root = FrameLayout(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(bar); addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            })
            addView(index, FrameLayout.LayoutParams(dp(26), -2, Gravity.END or Gravity.CENTER_VERTICAL))
            addView(bubble, FrameLayout.LayoutParams(dp(80), dp(80), Gravity.CENTER))
            addView(loading, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))
            addView(folderLayer, FrameLayout.LayoutParams(-1, -1))
        }
        updateClock()
        if (apps.isNotEmpty()) refresh(anim())
        return root
    }
}
