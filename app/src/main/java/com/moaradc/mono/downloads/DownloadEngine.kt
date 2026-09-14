package com.moaradc.mono.downloads

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.moaradc.mono.data.Db
import com.moaradc.mono.data.Dl
import com.moaradc.mono.data.DownloadRow
import com.moaradc.mono.util.U
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * 轻量下载引擎：HttpURLConnection + Range 断点续传 + MediaStore/公共目录落地。
 * 下载到应用私有 .part 文件，完成后写入公共下载目录（Download/Mono）。
 */
object DownloadEngine {

    private val pool = Executors.newFixedThreadPool(2)
    private val active = ConcurrentHashMap<Long, Boolean>()
    val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(l: () -> Unit) {
        listeners.add(l)
        l()
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    private fun notifyChanged() {
        U.runMain { listeners.forEach { it() } }
    }

    fun enqueue(ctx: Context, url: String, filename: String, mime: String?): Long {
        val safe = sanitize(filename.ifBlank { "file" })
        val id = Db.insertDownload(url, safe, mime)
        val appCtx = ctx.applicationContext
        submit(appCtx, id, url, safe)
        return id
    }

    fun resume(ctx: Context, id: Long) {
        U.runBg {
            val row = Db.downloadById(id) ?: return@runBg
            Db.updateDownload(id) { put("status", Dl.QUEUED); put("err", null as String?) }
            notifyChanged()
            submit(ctx.applicationContext, id, row.url, row.filename)
        }
    }

    fun pause(id: Long) {
        active[id] = false
    }

    fun cancel(ctx: Context, id: Long) {
        active[id] = false
        U.runBg {
            val row = Db.downloadById(id)
            Db.deleteDownload(id)
            row?.let { partFile(ctx, it.filename).delete() }
            notifyChanged()
        }
    }

    private fun submit(ctx: Context, id: Long, url: String, filename: String) {
        pool.execute {
            run(ctx, id, url, filename)
        }
    }

    private fun run(ctx: Context, id: Long, url: String, filename: String) {
        active[id] = true
        Db.updateDownload(id) { put("status", Dl.RUNNING); put("err", null as String?) }
        notifyChanged()
        val part = partFile(ctx, filename)
        var downloaded = part.length()
        var total = 0L
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            if (downloaded > 0) conn.setRequestProperty("Range", "bytes=$downloaded-")
            val code = conn.responseCode
            if (code == 416) {
                // 已完整
                finalize(ctx, id, filename, part, Db.downloadById(id)?.mime)
                active[id] = false
                return
            }
            if (code / 100 != 2) throw IOException("HTTP $code")
            val cl = conn.contentLengthLong
            total = if (cl > 0) cl + (if (code == 206) downloaded else 0) else 0L
            if (code == 200 && downloaded > 0) {
                // 服务器不支持断点：重头下
                part.delete(); downloaded = 0
            }
            Db.updateDownload(id) {
                if (total > 0) put("size", total)
                put("downloaded", downloaded)
            }
            val input: InputStream = conn.inputStream
            val out: OutputStream = FileOutputStream(part, downloaded > 0 && code == 206)
            val buf = ByteArray(16384)
            var lastNotify = 0L
            try {
                while (active[id] == true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    downloaded += n
                    val now = System.currentTimeMillis()
                    if (now - lastNotify > 350) {
                        lastNotify = now
                        Db.updateDownload(id) {
                            put("downloaded", downloaded)
                            if (total > 0) put("size", total)
                        }
                        notifyChanged()
                    }
                }
                out.flush()
            } finally {
                try { out.close() } catch (e: Exception) {}
                try { input.close() } catch (e: Exception) {}
                conn.disconnect()
            }
            if (active[id] != true) {
                Db.updateDownload(id) { put("status", Dl.PAUSED); put("downloaded", downloaded) }
                notifyChanged()
                active[id] = false
                return
            }
            if (total <= 0) {
                total = downloaded
                Db.updateDownload(id) { put("size", total) }
            }
            finalize(ctx, id, filename, part, Db.downloadById(id)?.mime)
            active[id] = false
        } catch (e: Exception) {
            active[id] = false
            Db.updateDownload(id) { put("status", Dl.FAILED); put("err", e.message ?: "失败") }
            notifyChanged()
        }
    }

    /** 落地到公共目录，删除 .part，标记完成 */
    private fun finalize(ctx: Context, id: Long, filename: String, part: File, mime: String?) {
        val finalMime = mime ?: guessMime(filename)
        val dest = place(ctx, filename, finalMime)
        Db.updateDownload(id) {
            put("status", Dl.DONE)
            put("dest", dest.first)
            put("downloaded", part.length())
            put("size", part.length())
        }
        if (dest.second) part.delete()
        else part.renameTo(dest.third ?: part)
        notifyChanged()
        U.toast(ctx, "下载完成：$filename")
    }

    /** 返回 (dest描述, 是否mediaStore(已复制需删part), 最终文件) */
    private fun place(ctx: Context, filename: String, mime: String): Triple<String, Boolean, File?> {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Mono")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return fallback(ctx, filename, mime)
                ctx.contentResolver.openOutputStream(uri)?.use { o ->
                    partFile(ctx, filename).inputStream().use { it.copyTo(o) }
                }
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                ctx.contentResolver.update(uri, done, null, null)
                return Triple(uri.toString(), true, null)
            } catch (e: Exception) {
                return fallback(ctx, filename, mime)
            }
        }
        return fallback(ctx, filename, mime)
    }

    private fun fallback(ctx: Context, filename: String, mime: String): Triple<String, Boolean, File?> {
        // ≤ Android 9：公共下载目录（需权限），否则退回应用目录
        val pub = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Mono")
        return try {
            pub.mkdirs()
            val target = uniqueFile(pub, filename)
            partFile(ctx, filename).copyTo(target, true)
            Triple(target.absolutePath, false, null)
        } catch (e: Exception) {
            val priv = File(ctx.getExternalFilesDir("Downloaded") ?: ctx.filesDir, "Mono")
            priv.mkdirs()
            val target = uniqueFile(priv, filename)
            Triple(target.absolutePath, false, null)
        }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (f.exists()) {
            f = File(dir, "$base ($i)$ext")
            i++
        }
        return f
    }

    private fun partFile(ctx: Context, filename: String): File {
        val dir = File(ctx.getExternalFilesDir("Downloaded") ?: ctx.filesDir, "parts")
        dir.mkdirs()
        return File(dir, "$filename.part")
    }

    private fun sanitize(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).ifBlank { "file" }
    }

    private fun guessMime(name: String): String {
        val l = name.lowercase()
        return when {
            l.endsWith(".mp4") || l.endsWith(".m4v") -> "video/mp4"
            l.endsWith(".webm") -> "video/webm"
            l.endsWith(".mkv") -> "video/x-matroska"
            l.endsWith(".avi") -> "video/x-msvideo"
            l.endsWith(".mov") -> "video/quicktime"
            l.endsWith(".3gp") -> "video/3gpp"
            l.endsWith(".mp3") -> "audio/mpeg"
            l.endsWith(".m4a") -> "audio/mp4"
            l.endsWith(".aac") -> "audio/aac"
            l.endsWith(".flac") -> "audio/flac"
            l.endsWith(".jpg") || l.endsWith(".jpeg") -> "image/jpeg"
            l.endsWith(".png") -> "image/png"
            l.endsWith(".gif") -> "image/gif"
            l.endsWith(".webp") -> "image/webp"
            l.endsWith(".zip") -> "application/zip"
            l.endsWith(".apk") -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    /** 打开已完成的文件 */
    fun openFile(ctx: Context, row: DownloadRow) {
        try {
            val uri: Uri = if (row.dest.startsWith("content:")) {
                Uri.parse(row.dest)
            } else {
                FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", File(row.dest))
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, row.mime.ifBlank { guessMime(row.filename) })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            // 尝试用应用内播放器（视频）
            try {
                com.moaradc.mono.player.PlayerActivity.start(ctx, row.dest, row.filename, 0L)
            } catch (e2: Exception) {
                U.toast(ctx, "无法打开此文件")
            }
        }
    }

    /** 若为视频则用应用内播放器播放 */
    fun playIfVideo(ctx: Context, row: DownloadRow): Boolean {
        val uri = if (row.dest.startsWith("content:")) row.dest else {
            try {
                FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", File(row.dest)).toString()
            } catch (e: Exception) {
                row.dest
            }
        }
        if (U.isVideoKind(row.mime, row.filename)) {
            com.moaradc.mono.player.PlayerActivity.start(ctx, uri, row.filename, 0L)
            return true
        }
        return false
    }
}
