package com.moaradc.mono.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.moaradc.mono.MainActivity
import com.moaradc.mono.R
import com.moaradc.mono.data.Db
import com.moaradc.mono.util.U

/** 网页历史 */
class HistoryActivity : BaseActivity() {

    override fun pageTitle() = "历史记录"

    override fun actionLabel() = "清空"

    override fun onAction() {
        AlertDialog.Builder(this)
            .setTitle("清空历史记录？")
            .setPositiveButton("清空") { _, _ ->
                U.runBg { Db.clearHistory() }
                U.runMain { rebuild() }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun rebuild() {
        U.runBg {
            val list = Db.history()
            U.runMain { render(list) }
        }
    }

    private fun render(list: List<com.moaradc.mono.data.HistoryItem>) {
        contentList.removeAllViews()
        if (list.isEmpty()) {
            emptyHint("暂无浏览历史")
            return
        }
        var lastDay = -1L
        list.forEach { h ->
            val day = h.time / 86400_000
            if (day != lastDay) {
                lastDay = day
                addHeader(dayLabel(h.time))
            }
            addRow(
                h.title.ifBlank { U.hostOf(h.url) },
                U.hostOf(h.url),
                icon = R.drawable.ic_globe,
                endText = null,
                click = { openUrl(h.url) },
                longClick = {
                    AlertDialog.Builder(this)
                        .setItems(arrayOf("删除这条记录", "复制链接")) { d, w ->
                            if (w == 0) {
                                U.runBg { Db.deleteHistory(h.id) }
                                U.runMain { rebuild() }
                            } else {
                                copyLink(h.url)
                            }
                            d.dismiss()
                        }
                        .show()
                    true
                }
            )
        }
    }

    private fun dayLabel(t: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = t
        val today = java.util.Calendar.getInstance()
        return when {
            cal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
                    cal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR) -> "今天"
            else -> "${cal.get(java.util.Calendar.MONTH) + 1}月${cal.get(java.util.Calendar.DAY_OF_MONTH)}日"
        }
    }

    private fun openUrl(url: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun copyLink(url: String) {
        val cm = getSystemService(android.content.ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText("url", url))
        U.toast(this, "已复制链接")
    }
}
