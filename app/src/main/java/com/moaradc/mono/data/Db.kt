package com.moaradc.mono.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 轻量 SQLite：书签 / 历史 / 播放记录 / 稍后再看 / 下载。
 * 所有方法为同步调用，需在工作线程执行（U.runBg）。
 */
object Db {
    private const val NAME = "mono.db"
    private const val VERSION = 1
    private lateinit var helper: Helper

    fun init(ctx: Context) {
        helper = Helper(ctx.applicationContext)
    }

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx, NAME, null, VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE bookmarks(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, url TEXT NOT NULL UNIQUE, icon BLOB, created INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, url TEXT NOT NULL, time INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX idx_history_time ON history(time DESC)")
            db.execSQL("CREATE TABLE records(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, title TEXT NOT NULL, url TEXT NOT NULL UNIQUE, position REAL NOT NULL DEFAULT 0, duration REAL NOT NULL DEFAULT 0, time INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE watch_later(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, title TEXT NOT NULL, url TEXT NOT NULL UNIQUE, time INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE downloads(id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL, filename TEXT NOT NULL, mime TEXT, size INTEGER NOT NULL DEFAULT 0, downloaded INTEGER NOT NULL DEFAULT 0, status INTEGER NOT NULL DEFAULT 0, dest TEXT, time INTEGER NOT NULL, err TEXT)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // v1
        }
    }

    private fun w(): SQLiteDatabase = helper.writableDatabase
    private fun r(): SQLiteDatabase = helper.readableDatabase

    // ---------- 历史 ----------
    fun addHistory(title: String, url: String, time: Long = System.currentTimeMillis()) {
        try {
            w().execSQL(
                "INSERT INTO history(title,url,time) SELECT ?,?,? WHERE NOT EXISTS(SELECT 1 FROM history WHERE url=? AND time > ?)",
                arrayOf(title, url, time, url, time - 4000)
            )
            w().execSQL("UPDATE history SET title=?, time=? WHERE url=?", arrayOf(title, time, url))
        } catch (e: Exception) {
        }
    }

    fun history(limit: Int = 500): List<HistoryItem> {
        val out = mutableListOf<HistoryItem>()
        try {
            r().rawQuery("SELECT id,title,url,time FROM history ORDER BY time DESC LIMIT ?", arrayOf("$limit")).use { c ->
                while (c.moveToNext()) out.add(HistoryItem(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun deleteHistory(id: Long) {
        try { w().delete("history", "id=?", arrayOf("$id")) } catch (e: Exception) {}
    }

    fun clearHistory() {
        try { w().delete("history", null, null) } catch (e: Exception) {}
    }

    // ---------- 书签 ----------
    fun addBookmark(title: String, url: String, icon: ByteArray?): Boolean {
        return try {
            w().insertWithOnConflict("bookmarks", null, ContentValues().apply {
                put("title", title); put("url", url); put("icon", icon); put("created", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_IGNORE) > 0
        } catch (e: Exception) {
            false
        }
    }

    fun isBookmarked(url: String): Boolean {
        return try {
            r().rawQuery("SELECT 1 FROM bookmarks WHERE url=?", arrayOf(url)).use { it.moveToFirst() }
        } catch (e: Exception) {
            false
        }
    }

    fun deleteBookmarkByUrl(url: String) {
        try { w().delete("bookmarks", "url=?", arrayOf(url)) } catch (e: Exception) {}
    }

    fun updateHistoryTitle(url: String, title: String) {
        if (title.isBlank() || url.isBlank()) return
        try {
            w().execSQL("UPDATE history SET title=? WHERE url=? AND title=''", arrayOf(title, url))
        } catch (e: Exception) {
        }
    }

    fun bookmarks(): List<Bookmark> {
        val out = mutableListOf<Bookmark>()
        try {
            r().rawQuery("SELECT id,title,url,icon,created FROM bookmarks ORDER BY created ASC", null).use { c ->
                while (c.moveToNext()) out.add(Bookmark(c.getLong(0), c.getString(1), c.getString(2), c.getBlob(3), c.getLong(4)))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun updateBookmark(id: Long, title: String, url: String) {
        try { w().execSQL("UPDATE bookmarks SET title=?,url=? WHERE id=?", arrayOf(title, url, id)) } catch (e: Exception) {}
    }

    fun deleteBookmark(id: Long) {
        try { w().delete("bookmarks", "id=?", arrayOf("$id")) } catch (e: Exception) {}
    }

    // ---------- 播放记录 ----------
    fun upsertRecord(kind: String, title: String, url: String, position: Double, duration: Double) {
        try {
            w().execSQL(
                "INSERT INTO records(kind,title,url,position,duration,time) VALUES(?,?,?,?,?,?) ON CONFLICT(url) DO UPDATE SET position=excluded.position, duration=excluded.duration, time=excluded.time, title=excluded.title",
                arrayOf(kind, title, url, position, duration, System.currentTimeMillis())
            )
        } catch (e: Exception) {
        }
    }

    fun getRecord(url: String): PlayRecord? {
        return try {
            r().rawQuery("SELECT id,kind,title,url,position,duration,time FROM records WHERE url=?", arrayOf(url)).use { c ->
                if (c.moveToFirst()) PlayRecord(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getDouble(4), c.getDouble(5), c.getLong(6)) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun records(limit: Int = 300): List<PlayRecord> {
        val out = mutableListOf<PlayRecord>()
        try {
            r().rawQuery("SELECT id,kind,title,url,position,duration,time FROM records ORDER BY time DESC LIMIT ?", arrayOf("$limit")).use { c ->
                while (c.moveToNext()) out.add(PlayRecord(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getDouble(4), c.getDouble(5), c.getLong(6)))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun deleteRecord(id: Long) {
        try { w().delete("records", "id=?", arrayOf("$id")) } catch (e: Exception) {}
    }

    fun clearRecords() {
        try { w().delete("records", null, null) } catch (e: Exception) {}
    }

    // ---------- 稍后再看 ----------
    fun addWatchLater(kind: String, title: String, url: String): Boolean {
        return try {
            w().insertWithOnConflict("watch_later", null, ContentValues().apply {
                put("kind", kind); put("title", title); put("url", url); put("time", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_IGNORE) > 0
        } catch (e: Exception) {
            false
        }
    }

    fun watchLater(): List<WatchLaterItem> {
        val out = mutableListOf<WatchLaterItem>()
        try {
            r().rawQuery("SELECT id,kind,title,url,time FROM watch_later ORDER BY time DESC", null).use { c ->
                while (c.moveToNext()) out.add(WatchLaterItem(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4)))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun deleteWatchLater(id: Long) {
        try { w().delete("watch_later", "id=?", arrayOf("$id")) } catch (e: Exception) {}
    }

    fun clearWatchLater() {
        try { w().delete("watch_later", null, null) } catch (e: Exception) {}
    }

    // ---------- 下载 ----------
    fun insertDownload(url: String, filename: String, mime: String?): Long {
        return try {
            w().insert("downloads", null, ContentValues().apply {
                put("url", url); put("filename", filename); put("mime", mime)
                put("time", System.currentTimeMillis())
            }) ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    fun updateDownload(id: Long, cv: ContentValues.() -> Unit) {
        try { w().update("downloads", ContentValues().apply(cv), "id=?", arrayOf("$id")) } catch (e: Exception) {}
    }

    fun downloads(): List<DownloadRow> {
        val out = mutableListOf<DownloadRow>()
        try {
            r().rawQuery("SELECT id,url,filename,mime,size,downloaded,status,dest,time,err FROM downloads ORDER BY time DESC", null).use { c ->
                while (c.moveToNext()) out.add(
                    DownloadRow(c.getLong(0), c.getString(1), c.getString(2), c.getString(3) ?: "", c.getLong(4), c.getLong(5), c.getInt(6), c.getString(7) ?: "", c.getLong(8), c.getString(9))
                )
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun downloadById(id: Long): DownloadRow? {
        return downloads().firstOrNull { it.id == id }
    }

    fun deleteDownload(id: Long) {
        try { w().delete("downloads", "id=?", arrayOf("$id")) } catch (e: Exception) {}
    }
}
