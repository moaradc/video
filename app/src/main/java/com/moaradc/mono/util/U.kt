package com.moaradc.mono.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import java.net.URLDecoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** 通用小工具 */
object U {
    val main: Handler = Handler(Looper.getMainLooper())
    private val bg: ExecutorService = Executors.newSingleThreadExecutor()

    fun runBg(r: Runnable) {
        bg.execute(r)
    }

    fun runMain(r: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run() else main.post(r)
    }

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun toast(ctx: Context, msg: String) {
        runMain { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
    }

    /** 秒 → mm:ss / h:mm:ss */
    fun fmtTime(sec: Double): String {
        if (sec.isNaN() || sec < 0) return "00:00"
        val s = sec.toLong()
        val h = s / 3600
        val m = (s % 3600) / 60
        val r = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, r) else String.format("%02d:%02d", m, r)
    }

    /** 毫秒 → mm:ss / h:mm:ss */
    fun fmtMs(ms: Long): String = fmtTime(ms / 1000.0)

    fun fmtBytes(b: Long): String {
        if (b < 0) return "未知"
        if (b < 1024) return "${b}B"
        if (b < 1024 * 1024) return String.format("%.1fKB", b / 1024.0)
        if (b < 1024L * 1024 * 1024) return String.format("%.1fMB", b / 1048576.0)
        return String.format("%.2fGB", b / 1073741824.0)
    }

    fun hostOf(url: String): String {
        return try {
            val u = Uri.parse(url)
            u.host ?: url
        } catch (e: Exception) {
            url
        }
    }

    /** 域名首字母（等宽风格字标） */
    fun monogram(s: String): String {
        val h = hostOf(s)
        val c = h.trimStart('.').firstOrNull { it.isLetterOrDigit() } ?: 'W'
        return c.uppercaseChar().toString()
    }

    fun isLikelyUrl(input: String): Boolean {
        val t = input.trim()
        if (t.isEmpty()) return false
        if (t.startsWith("http://") || t.startsWith("https://") || t.startsWith("file://") || t.startsWith("about:")) return true
        if (t.contains(" ")) return false
        if (!t.contains(".")) return false
        val host = t.substringBefore('/')
        return host.substringAfterLast('.').length in 2..10 && host.none { it == ' ' }
    }

    fun normalizeUrl(input: String): String {
        val t = input.trim()
        return if (t.startsWith("http") || t.startsWith("file") || t.startsWith("about")) t else "https://$t"
    }

    /** 搜索引擎列表 */
    val ENGINES = listOf(
        "必应" to "https://cn.bing.com/search?q=",
        "百度" to "https://www.baidu.com/s?wd=",
        "谷歌" to "https://www.google.com/search?q=",
        "DuckDuckGo" to "https://duckduckgo.com/?q="
    )

    fun buildSearch(query: String): String {
        val (name, base) = ENGINES[Prefs.engine.coerceIn(0, ENGINES.size - 1)]
        return base + Uri.encode(query)
    }

    fun guessFilename(url: String, disposition: String?, mime: String?): String {
        var name: String? = null
        if (disposition != null) {
            val m = Regex("filename\\*?=\"?(?:UTF-8'')?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(disposition)
            if (m != null) name = m.groupValues[1]
        }
        if (name.isNullOrBlank()) {
            name = try {
                Uri.parse(url).lastPathSegment
            } catch (e: Exception) {
                null
            }
        }
        if (name.isNullOrBlank()) name = "download"
        var n = name
        try {
            n = URLDecoder.decode(name, "UTF-8")
        } catch (e: Exception) {
        }
        n = n.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
        if (!n.contains('.') && mime != null) {
            n += when (Regex("/(\\w+)").find(mime)?.groupValues?.get(1)) {
                "mp4", "mpeg4" -> ".mp4"
                "webm" -> ".webm"
                "mp3", "mpeg" -> ".mp3"
                "m4a" -> ".m4a"
                "aac" -> ".aac"
                "ogg" -> ".ogg"
                "pdf" -> ".pdf"
                "zip" -> ".zip"
                "png" -> ".png"
                "jpeg" -> ".jpg"
                "gif" -> ".gif"
                else -> ""
            }
        }
        return n
    }

    fun isVideoKind(mime: String?, name: String): Boolean {
        if (mime != null && mime.startsWith("video/")) return true
        val lower = name.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mkv") ||
                lower.endsWith(".avi") || lower.endsWith(".mov") || lower.endsWith(".3gp") ||
                lower.endsWith(".ts") || lower.endsWith(".flv") || lower.endsWith(".m4v")
    }

    /** 黑白圆形字标（首页快捷方式 / 列表头像） */
    fun monogramDrawable(ctx: Context, letter: String, dark: Boolean): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setStroke(U.dp(ctx, 1f), if (dark) Color.WHITE else Color.BLACK)
        d.setColor(Color.TRANSPARENT)
        return d
    }

    @SuppressLint("ResourceType")
    fun bold(text: CharSequence): CharSequence = text // 占位：统一在调用处使用 Typeface
}
