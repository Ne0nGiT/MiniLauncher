package com.alex.minilauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.text.Normalizer

class AppItem(val label: String, val pkg: String, val icon: Drawable)

val DEF_BG = 0xFF101418.toInt()
val DEF_ACC = 0xFF4FC3F7.toInt()

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
fun Context.prefs() = getSharedPreferences("p", 0)
fun isLight(c: Int) = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) > 150
fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace('đ', 'd')

fun Activity.applyBars(bg: Int) {
    window.statusBarColor = bg
    window.navigationBarColor = bg
    var f = 0
    if (isLight(bg)) {
        f = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (Build.VERSION.SDK_INT >= 26) f = f or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }
    window.decorView.systemUiVisibility = f
}

fun Context.pill(t: String, accent: Int, fg: Int, onClick: () -> Unit) = TextView(this).apply {
    text = t; setTextColor(fg); textSize = 15f; gravity = Gravity.CENTER
    setPadding(dp(14), dp(14), dp(14), dp(14))
    background = GradientDrawable().apply {
        cornerRadius = dp(24).toFloat(); setColor((accent and 0xFFFFFF) or 0x33000000)
    }
    setOnClickListener { onClick() }
}

class AppAdapter(val ctx: Context, var items: List<AppItem>) : BaseAdapter() {
    override fun getCount() = items.size
    override fun getItem(p: Int) = items[p]
    override fun getItemId(p: Int) = p.toLong()
    override fun getView(p: Int, v: View?, parent: ViewGroup): View {
        val s = ctx.prefs()
        val fg = if (isLight(s.getInt("bg", DEF_BG))) Color.BLACK else Color.WHITE
        val size = ctx.dp(s.getInt("icon", 56))
        val a = items[p]
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, ctx.dp(6), 0, ctx.dp(6))
            addView(ImageView(ctx).apply {
                setImageDrawable(a.icon); layoutParams = LinearLayout.LayoutParams(size, size)
            })
            if (s.getBoolean("labels", true)) addView(TextView(ctx).apply {
                text = a.label; setTextColor(fg); textSize = 11f; maxLines = 1
                ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
                setPadding(ctx.dp(2), ctx.dp(4), ctx.dp(2), 0)
            })
        }
    }
}

class MainActivity : Activity() {
    var apps = listOf<AppItem>()
    lateinit var overlay: View
    lateinit var search: EditText
    lateinit var favAd: AppAdapter
    lateinit var allAd: AppAdapter
    lateinit var empty: TextView

    override fun onResume() { super.onResume(); setContentView(build()); load() }
    override fun onNewIntent(intent: Intent?) { super.onNewIntent(intent); if (::overlay.isInitialized) closeDrawer() }
    override fun onBackPressed() { if (::overlay.isInitialized && overlay.visibility == View.VISIBLE) closeDrawer() }

    fun favs() = prefs().getString("favs", "")!!.split(",").filter { it.isNotEmpty() }
    fun favApps() = favs().mapNotNull { p -> apps.find { it.pkg == p } }

    fun load() = Thread {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = packageManager.queryIntentActivities(i, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { AppItem(it.loadLabel(packageManager).toString(), it.activityInfo.packageName, it.loadIcon(packageManager)) }
            .distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
        runOnUiThread { apps = list; refresh() }
    }.start()

    fun refresh() {
        favAd.items = favApps(); favAd.notifyDataSetChanged()
        allAd.items = apps; allAd.notifyDataSetChanged()
        empty.visibility = if (favAd.items.isEmpty()) View.VISIBLE else View.GONE
    }

    fun launch(a: AppItem) { packageManager.getLaunchIntentForPackage(a.pkg)?.let { startActivity(it) } }

    fun menu(a: AppItem) {
        val isFav = favs().contains(a.pkg)
        AlertDialog.Builder(this).setTitle(a.label).setItems(
            arrayOf(if (isFav) "Bỏ khỏi Yêu thích" else "Thêm vào Yêu thích", "Thông tin ứng dụng", "Gỡ cài đặt")
        ) { _, w ->
            when (w) {
                0 -> {
                    val f = favs().toMutableList()
                    if (!f.remove(a.pkg)) f.add(a.pkg)
                    prefs().edit().putString("favs", f.joinToString(",")).apply(); refresh()
                }
                1 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
                2 -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")))
            }
        }.show()
    }

    fun grid(cols: Int, ad: AppAdapter) = GridView(this).apply {
        numColumns = cols; adapter = ad; selector = ColorDrawable(0)
        verticalSpacing = dp(8); isVerticalScrollBarEnabled = false
        setOnItemClickListener { _, _, p, _ -> launch(ad.items[p]) }
        setOnItemLongClickListener { _, _, p, _ -> menu(ad.items[p]); true }
    }

    fun mlp() = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), 0, dp(4), 0) }

    fun openDrawer(focus: Boolean) {
        overlay.visibility = View.VISIBLE
        search.setText("")
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (focus) search.post { search.requestFocus(); imm.showSoftInput(search, 0) }
        else { search.clearFocus(); imm.hideSoftInputFromWindow(search.windowToken, 0) }
    }

    fun closeDrawer() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(search.windowToken, 0)
        overlay.visibility = View.GONE
    }

    fun build(): View {
        val bg = prefs().getInt("bg", DEF_BG); val ac = prefs().getInt("acc", DEF_ACC)
        val fg = if (isLight(bg)) Color.BLACK else Color.WHITE
        val cols = prefs().getInt("cols", 4)
        applyBars(bg)
        favAd = AppAdapter(this, favApps()); allAd = AppAdapter(this, apps)
        empty = TextView(this).apply {
            text = "Chưa có app yêu thích.\nMở Kho ứng dụng rồi nhấn giữ một app để thêm."
            setTextColor(fg); alpha = 0.6f; setPadding(dp(4), 0, 0, dp(8))
            visibility = if (apps.isEmpty() || favs().isEmpty()) View.VISIBLE else View.GONE
        }
        val home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(24), dp(16), dp(16))
            addView(TextView(context).apply {
                text = "★ Yêu thích"; setTextColor(ac); textSize = 16f
                typeface = Typeface.DEFAULT_BOLD; setPadding(dp(4), 0, 0, dp(12))
            })
            addView(empty)
            addView(grid(cols, favAd), LinearLayout.LayoutParams(-1, 0, 1f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0)
                addView(pill("Kho ứng dụng", ac, fg) { openDrawer(false) }, mlp())
                addView(pill("Tìm kiếm", ac, fg) { openDrawer(true) }, mlp())
                addView(pill("Cài đặt", ac, fg) { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }, mlp())
            })
        }
        search = EditText(this).apply {
            hint = "Tìm ứng dụng..."; setTextColor(fg)
            setHintTextColor(Color.argb(130, Color.red(fg), Color.green(fg), Color.blue(fg)))
            setSingleLine(); imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor((ac and 0xFFFFFF) or 0x33000000) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val q = norm(s.toString().trim())
                    allAd.items = if (q.isEmpty()) apps else apps.filter { norm(it.label).contains(q) }
                    allAd.notifyDataSetChanged()
                }
            })
            setOnEditorActionListener { _, _, _ -> allAd.items.firstOrNull()?.let { launch(it) }; true }
        }
        overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); isClickable = true
            setPadding(dp(16), dp(20), dp(16), dp(16)); visibility = View.GONE
            addView(search, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            addView(grid(cols, allAd), LinearLayout.LayoutParams(-1, 0, 1f))
        }
        return FrameLayout(this).apply { setBackgroundColor(bg); addView(home); addView(overlay) }
    }
}
