package com.moaradc.mono.ui

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import com.moaradc.mono.MainActivity
import com.moaradc.mono.R
import com.moaradc.mono.data.Db
import com.moaradc.mono.data.WatchLaterItem
import com.moaradc.mono.player.PlayerActivity
import com.moaradc.mono.util.U

/** 稍后再看 */
class WatchLaterActivity : BaseActivity() {

    override fun pageTitle() = "稍后再看"

    override fun actionLabel() = "清空"

    override fun onAction() {
        AlertDialog.Builder(this)
            .setTitle("清空稍后再看？")
            .setPositiveButton("清空") { _, _ ->
                U.runBg { Db.clearWatchLater() }
                U.runMain { rebuild() }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun rebuild() {
        U.runBg {
            val list = Db.watchLater()
            U.runMain { render(list) }
        }
    }

    private fun render(list: List<WatchLaterItem>) {
        contentList.removeAllViews()
        if (list.isEmpty()) {
            emptyHint("暂无稍后再看的视频")
            return
        }
        list.forEachIndexed { i, item ->
            val kindLabel = if (item.kind == "local") "本地视频" else "网页视频"
            addRow(
                item.title,
                "$kindLabel · ${timeAgoLabel(item.time)}",
                if (item.kind == "local") R.drawable.ic_video else R.drawable.ic_globe,
                click = { play(item) },
                longClick = {
                    AlertDialog.Builder(this)
                        .setTitle(item.title)
                        .setItems(arrayOf("移除", "立即播放")) { _, which ->
                            if (which == 0) {
                                U.runBg { Db.deleteWatchLater(item.id) }
                                U.runMain { rebuild() }
                            } else play(item)
                        }
                        .show()
                    true
                }
            )
            if (i != list.size - 1) addDivider()
        }
    }

    private fun play(item: WatchLaterItem) {
        if (item.kind == "local") {
            PlayerActivity.start(this, item.url, item.title, 0L)
        } else {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .setData(Uri.parse(item.url))
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }
}
