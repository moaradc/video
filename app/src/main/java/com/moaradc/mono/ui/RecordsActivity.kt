package com.moaradc.mono.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.moaradc.mono.MainActivity
import com.moaradc.mono.R
import com.moaradc.mono.data.Db
import com.moaradc.mono.data.PlayRecord
import com.moaradc.mono.player.PlayerActivity
import com.moaradc.mono.util.U

/** 播放记录（断点续播历史） */
class RecordsActivity : BaseActivity() {

    override fun pageTitle() = "播放记录"

    override fun actionLabel() = "清空"

    override fun onAction() {
        AlertDialog.Builder(this)
            .setTitle("清空播放记录？")
            .setPositiveButton("清空") { _, _ ->
                U.runBg { Db.clearRecords() }
                U.runMain { rebuild() }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun rebuild() {
        U.runBg {
            val list = Db.records()
            U.runMain { render(list) }
        }
    }

    private fun render(list: List<PlayRecord>) {
        contentList.removeAllViews()
        if (list.isEmpty()) {
            emptyHint("暂无播放记录")
            return
        }
        list.forEachIndexed { i, item ->
            val kindLabel = if (item.kind == "local") "本地视频" else "网页视频"
            val posLabel = if (item.duration > 0) {
                "看到 ${U.fmtTime(item.position)} / ${U.fmtTime(item.duration)}"
            } else kindLabel
            addRow(
                item.title,
                "$kindLabel · $posLabel · ${timeAgoLabel(item.time)}",
                if (item.kind == "local") R.drawable.ic_video else R.drawable.ic_globe,
                click = { play(item) },
                longClick = {
                    AlertDialog.Builder(this)
                        .setTitle(item.title)
                        .setItems(arrayOf("删除记录", "继续播放")) { _, which ->
                            if (which == 0) {
                                U.runBg { Db.deleteRecord(item.id) }
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

    private fun play(item: PlayRecord) {
        if (item.kind == "local") {
            PlayerActivity.start(this, item.url, item.title, item.position.toLong())
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
