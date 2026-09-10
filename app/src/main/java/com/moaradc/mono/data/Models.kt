package com.moaradc.mono.data

/** 数据模型：全部为轻量数据类 */
data class Bookmark(
    val id: Long,
    val title: String,
    val url: String,
    val icon: ByteArray?,
    val created: Long
)

data class HistoryItem(
    val id: Long,
    val title: String,
    val url: String,
    val time: Long
)

data class PlayRecord(
    val id: Long,
    val kind: String, // "web" / "local"
    val title: String,
    val url: String,
    val position: Double, // 秒
    val duration: Double, // 秒
    val time: Long
)

data class WatchLaterItem(
    val id: Long,
    val kind: String,
    val title: String,
    val url: String,
    val time: Long
)

data class DownloadRow(
    val id: Long,
    val url: String,
    val filename: String,
    val mime: String,
    val size: Long,
    val downloaded: Long,
    val status: Int, // 0 排队 1 下载中 2 已暂停 3 完成 4 失败
    val dest: String,
    val time: Long,
    val err: String?
)

object Dl {
    const val QUEUED = 0
    const val RUNNING = 1
    const val PAUSED = 2
    const val DONE = 3
    const val FAILED = 4
}
