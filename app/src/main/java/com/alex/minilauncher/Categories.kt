package com.alex.minilauncher

import android.content.pm.ApplicationInfo
import android.os.Build

const val F_SOC = "Mạng xã hội"
const val F_GAME = "Trò chơi"
const val F_MEDIA = "Đa phương tiện"
const val F_WEB = "Trình duyệt"
const val F_WORK = "Làm việc & AI"
const val F_DEV = "Lập trình"
const val F_HEALTH = "Sức khỏe & Thể thao"
const val F_MONEY = "Mua sắm & Tài chính"
const val F_GO = "Du lịch & Bản đồ"
const val F_TOOL = "Công cụ"
const val F_OTHER = "Khác"

val FOLDERS = listOf(F_SOC, F_GAME, F_MEDIA, F_WEB, F_WORK, F_DEV, F_HEALTH, F_MONEY, F_GO, F_TOOL, F_OTHER)

/**
 * Phân loại offline. Thứ tự ưu tiên: từ khóa theo nhóm -> danh mục Android -> app hệ thống.
 * s(...) = tìm chuỗi con trong tên + package (chỉ dùng từ khóa dài, đặc trưng).
 * w(...) = khớp nguyên từ trong tên app (dùng cho từ ngắn để tránh khớp nhầm).
 */
fun classify(pkg: String, label: String, ai: ApplicationInfo): String {
    val lbl = norm(label)
    val all = pkg.lowercase() + " " + lbl
    val words = lbl.split(Regex("[^a-z0-9]+")).toSet()
    fun s(vararg x: String) = x.any { all.contains(it) }
    fun w(vararg x: String) = x.any { it in words }

    // ---- Mạng xã hội ----
    if (s("facebook", "messenger", "zalo", "instagram", "tiktok", "discord", "telegram", "twitter", "whatsapp",
            "snapchat", "reddit", "viber", "pinterest", "linkedin", "naver.line", "wechat", "skype", "threads",
            "bluesky", "mastodon", "tumblr", "kakao", "tinder", "bumble", "locket", "bereal", "gapo", "mocha") ||
        w("x", "line", "signal")) return F_SOC

    // ---- Trò chơi (đã gồm game phổ biến ở Việt Nam) ----
    if (s("game", "roblox", "minecraft", "mojang", "supercell", "garena", "innersloth", "among us", "chess",
            "com.tencent.ig", "tmgp", "pubg", "genshin", "mihoyo", "hoyoverse", "honkai", "star rail", "freefire",
            "free fire", "clash", "brawl", "blast", "lien quan", "kgvn", "toc chien", "wild rift", "wildrift",
            "crossfire", "call of duty", "callofduty", "codm", "mobile legends", "mobilelegends", "moonton",
            "delta force", "deltaforce", "zingspeed", "ngoi sao thoi trang", "dau truong chan ly", "teamfight",
            "fc online", "fconline", "fc mobile", "arena breakout", "gunny", "candy crush", "pokemon", "whiteout",
            "royal match", "fortnite", "subway surfers", "stumble", "wuthering", "zenless", "honor of kings",
            "gossip harbor", "ngoc rong", "vo lam", "kiem the", "8 ball", "ludo", "sudoku", "solitaire", "wordle",
            "puzzle", "among", "tft") || w("2048", "tft", "lol")) return F_GAME

    // ---- Lập trình ----
    if (s("acode", "termux", "pydroid", "spck", "github", "sketchware", "dcoder", "quoda", "replit",
            "qpython", "code editor", "vscode", "visual studio", "jetbrains", "codespace", "codex", "claude code",
            "androidide", "android studio", "juicessh", "termius", "apk editor", "apk analyzer", "postman",
            "hoppscotch", "developer", "devtools", "terminal", "shizuku") || w("git", "ide", "aide", "ssh", "adb", "sdk", "python", "java", "kotlin", "bash")) return F_DEV

    // ---- Sức khỏe & Thể thao ----
    if (s("fitpro", "fitness", "fitbit", "strava", "myfitnesspal", "shealth", "samsung health", "health", "garmin",
            "zepp", "amazfit", "mi fitness", "xiaomi wear", "oura", "whoop", "polar flow", "runtastic", "nike run",
            "nike training", "calm", "headspace", "meditat", "yoga", "workout", "gym", "pedometer", "step counter",
            "buoc chan", "walkfit", "fatsecret", "cronometer", "lifesum", "noom", "calorie", "diet", "suc khoe",
            "chay bo", "tap luyen", "7 minute", "sleep") || w("fit", "run")) return F_HEALTH

    // ---- Đa phương tiện (ảnh, video, nhạc, phim, truyện) ----
    if (s("youtube", "netflix", "spotify", "music", "zingmp3", "zing mp3", "nhaccuatui", "video", "vlc", "camera",
            "gallery", "photo", "capcut", "snapseed", "lightroom", "picsart", "podcast", "soundcloud", "recorder",
            "ghi am", "fptplay", "fpt play", "vieon", "tv360", "vtvgo", "vtv go", "mytv", "viettel tv", "galaxy play",
            "fimplus", "cliptv", "clip tv", "keeng", "zing tv", "primevideo", "prime video", "disney", "iqiyi",
            "wetv", "bilibili", "twitch", "anime", "manga", "dramabox", "netshort", "freereels", "dramawave",
            "storyreel", "vibeshort", "blinkdrama", "shortmax", "goodshort", "pinedrama", "cashflicks", "dramaspot",
            "reelshort", "puretuber", "pure tuber", "kindle", "wattpad", "audible", "webtoon", "baeble") ||
        w("anh", "nhac", "radio", "tv", "pops", "sach", "truyen", "hbo")) return F_MEDIA

    // ---- Trình duyệt & tin tức ----
    if (s("chrome", "brave", "firefox", "com.opera", "browser", "duckduckgo", "com.microsoft.emmx", "googlequicksearchbox",
            "sbrowser", "vivaldi", "vnexpress", "baomoi", "bao moi", "znews", "zing news", "tuoitre", "dantri",
            "thanhnien", "kenh14", "vietnamplus", "cafef", "google news", "flipboard", "newsbreak", "tin tuc") ||
        w("edge", "internet") || lbl.trim() == "google") return F_WEB

    // ---- Làm việc, học tập & AI ----
    if (s("docs", "sheets", "slides", "onenote", "notion", "classroom", "duolingo", "claude", "chatgpt", "openai",
            "gemini", "anthropic", "outlook", "gmail", "calendar", "com.microsoft.office", "zoom", "onedrive",
            "skydrive", "copilot", "perplexity", "deepseek", "grok", "notebooklm", "midjourney", "canva", "evernote",
            "todoist", "trello", "slack", "ghi chu", "keep", "dropbox", "camscanner", "acrobat", "quizlet",
            "photomath", "elsa", "coursera", "udemy", "ielts", "toeic", "wps") ||
        w("ai", "word", "excel", "powerpoint", "meet", "drive", "notes", "mail", "teams", "lich", "pdf", "lark", "cake", "mochi")) return F_WORK

    // ---- Mua sắm, giao hàng & tài chính (ví, ngân hàng VN) ----
    if (s("shopee", "lazada", "tiki", "momo", "zalopay", "viettelpay", "viettel money", "vnpay", "vietcombank", "vcb",
            "mbbank", "mb bank", "mbmobile", "techcombank", "tpbank", "tpb.mb", "bidv", "vietinbank", "vpbank", "sacombank",
            "myvib", "hdbank", "seabank", "lpbank", "lienviet", "agribank", "nam a bank", "namabank", "eximbank", "bac a bank",
            "bvbank", "pvcombank", "vietabank", "vikki", "paypal", "wallet", "bank", "ngan hang", "digibank",
            "smartbanking", "sendo", "grabpay", "befood", "foody", "bach hoa xanh", "thegioididong", "dienmayxanh",
            "fpt shop", "cellphones", "hasaki", "ahamove", "giaohangnhanh", "ghtk", "viettel post", "vnpost", "ebay",
            "aliexpress", "temu", "shein", "chotot", "carousell", "binance", "okx", "bybit", "coinbase", "baoviet",
            "prudential", "manulife", "dai-ichi", "bao hiem") ||
        w("pay", "vi", "now", "acb", "msb", "ocb", "shb", "tcb", "vib", "timo", "stock", "stocks", "bhx", "tgdd")) return F_MONEY

    // ---- Du lịch, di chuyển & bản đồ ----
    if (s("maps", "grab", "xanhsm", "xanh sm", "gojek", "booking", "airbnb", "traveloka", "agoda", "trip.com",
            "tripadvisor", "klook", "vexere", "vietjet", "vietnam airlines", "bamboo airways", "skyscanner", "waze",
            "vietmap", "lalamove", "mytour", "ivivu", "xyz.be.", "mai linh", "vinasun", "fastgo") ||
        w("be", "bus", "taxi", "hotel", "flight", "bolt", "uber")) return F_GO

    // ---- Công cụ (đặt trước danh mục Android vì Android gộp "Tools" vào "Productivity") ----
    if (s("authenticator", "explorer", "apkpure", "apkmirror", "aptoide", "uptodown", "fdroid", "f-droid", "vpn", "keyboard",
            "gboard", "swiftkey", "scanner", "translate", "cleaner", "clicker", "cpuid", "cpu-z", "cpu_z", "localsend",
            "com.android.vending", "cua hang", "calculator", "may tinh", "dong ho", "thoi tiet", "weather", "flashlight",
            "compass", "cai dat", "settings", "antivirus", "an toan", "backup", "sao luu", "vneid", "vssid", "etax",
            "dich vu cong", "speedtest", "barcode", "battery", "launcher", "wallpaper", "hinh nen", "icon pack",
            "digital wellbeing", "family link", "zarchiver", "winrar", "mixplorer", "benchmark", "antutu", "geekbench",
            "device info", "tin nhan", "danh ba", "dialer", "contacts", "tasker", "automate") ||
        w("file", "files", "nfc", "clock", "wifi", "bluetooth", "manager", "tep", "qr", "cpu", "gpu", "ram", "sms", "sim")) return F_TOOL

    if (Build.VERSION.SDK_INT >= 26) {
        when (ai.category) {
            ApplicationInfo.CATEGORY_GAME -> return F_GAME
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> return F_MEDIA
            ApplicationInfo.CATEGORY_SOCIAL -> return F_SOC
            ApplicationInfo.CATEGORY_NEWS -> return F_WEB
            ApplicationInfo.CATEGORY_MAPS -> return F_GO
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> return F_TOOL
            else -> {}
        }
    }
    if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return F_TOOL
    return F_OTHER
}
