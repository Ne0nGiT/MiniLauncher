package com.alex.minilauncher

import android.content.pm.ApplicationInfo
import android.os.Build

val FOLDERS = listOf(
    "Mạng xã hội", "Trò chơi", "Đa phương tiện", "Trình duyệt", "Làm việc & AI",
    "Mua sắm & Tài chính", "Du lịch & Bản đồ", "Công cụ", "Khác"
)

/** Phân loại offline: từ khóa tên app/package, rồi danh mục Android, rồi app hệ thống. */
fun classify(pkg: String, label: String, ai: ApplicationInfo): String {
    val lbl = norm(label)
    val all = pkg.lowercase() + " " + lbl
    val words = lbl.split(Regex("[^a-z0-9]+")).toSet()
    fun s(vararg x: String) = x.any { all.contains(it) }
    fun w(vararg x: String) = x.any { it in words }

    if (s("facebook", "messenger", "zalo", "instagram", "tiktok", "discord", "telegram", "twitter", "whatsapp",
            "snapchat", "reddit", "viber", "pinterest", "linkedin", "naver.line", "wechat", "skype", "threads") || w("x", "line")) return FOLDERS[0]
    if (s("game", "roblox", "minecraft", "mojang", "supercell", "garena", "innersloth", "among us", "chess",
            "pubg", "genshin", "mihoyo", "freefire", "clash", "brawl", "blast")) return FOLDERS[1]
    if (s("youtube", "netflix", "spotify", "music", "zingmp3", "video", "vlc", "camera", "gallery", "photo",
            "capcut", "snapseed", "lightroom", "picsart", "podcast", "soundcloud", "nhaccuatui") || w("anh", "nhac", "radio", "tv")) return FOLDERS[2]
    if (s("chrome", "brave", "firefox", "opera", "browser", "duckduckgo", "com.microsoft.emmx") || w("edge")) return FOLDERS[3]
    if (s("docs", "sheets", "slides", "onenote", "notion", "classroom", "duolingo", "claude", "chatgpt", "openai",
            "gemini", "anthropic", "outlook", "gmail", "calendar", "com.microsoft.office", "zoom") ||
        w("ai", "word", "excel", "powerpoint", "meet", "drive", "keep", "notes", "mail", "teams", "lich")) return FOLDERS[4]
    if (s("shopee", "lazada", "tiki", "momo", "zalopay", "vietcombank", "vcb", "mbbank", "techcombank", "tpbank",
            "bidv", "vietinbank", "paypal", "wallet", "bank", "sendo", "grabpay") || w("pay", "vi")) return FOLDERS[5]
    if (s("maps", "grab", "xanhsm", "gojek", "booking", "airbnb", "traveloka", "uber")) return FOLDERS[6]
    if (Build.VERSION.SDK_INT >= 26) {
        when (ai.category) {
            ApplicationInfo.CATEGORY_GAME -> return FOLDERS[1]
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> return FOLDERS[2]
            ApplicationInfo.CATEGORY_SOCIAL -> return FOLDERS[0]
            ApplicationInfo.CATEGORY_NEWS -> return FOLDERS[3]
            ApplicationInfo.CATEGORY_MAPS -> return FOLDERS[6]
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> return FOLDERS[4]
            else -> {}
        }
    }
    if (s("authenticator", "termux", "pydroid", "explorer", "apkpure", "vpn", "keyboard", "scanner", "translate", "cleaner") ||
        (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return FOLDERS[7]
    return FOLDERS[8]
}
