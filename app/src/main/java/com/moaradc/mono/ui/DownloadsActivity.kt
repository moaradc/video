package com.moaradc.mono.ui

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.moaradc.mono.R
import com.moaradc.mono.data.Dl
import com.moaradc.mono.data.DownloadRow
import com.moaradc.mono.downloads.DownloadEngine
import com.moaradc.mono.util.U

/** 下载管理：进度 / 暂停续传 / 删除 / 打开 / 应用内播放 */
class DownloadsActivity : BaseActivity() {

    private val listener: () -> Unit = { rebuild() }
    private var alive = true

    override fun pageTitle() = "下载管理"

    override fun actionLabel() = "新建"

    override fun onAction() {
        newDownloadDialog()
    }

    override fun onResume() {
        super.onResume()
        alive = true
        DownloadEngine.listeners.add(listener)
    }

    override fun onPause() {
        alive = false
        DownloadEngine.listeners.remove(listener)
        super.onPause()
    }

    override fun rebuild() {
        U.runBg {
            val list = com.moaradc.mono.data.Db.downloads()
            U.runMain { if (alive) render(list) }
        }
    }

    private fun render(list: List<DownloadRow>) {
        contentList.removeAllViews()
        if (list.isEmpty()) {
            emptyHint("暂无下载任务\n浏览时长按链接即可下载")
            return
        }
        list.forEach { row -> addDownloadRow(row) }
    }

    private fun addDownloadRow(row: DownloadRow) {
        val pad = U.dp(this, 20f)
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(pad, U.dp(this, 12f), pad, U.dp(this, 12f))

        // 第一行：文件名 + 状态
        val line1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val name = TextView(this).apply {
            text = row.filename
            textSize = 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setTextColor(getColor(R.color.ink))
        }
        line1.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
        val status = TextView(this).apply {
            text = statusText(row)
            textSize = 11f
            setTextColor(getColor(R.color.sub))
        }
        line1.addView(status, LinearLayout.LayoutParams(-2, -2).apply { marginStart = U.dp(this@DownloadsActivity, 8f) })
        card.addView(line1)

        // 第二行：来源 + 大小
        val line2 = TextView(this).apply {
            text = "${U.hostOf(row.url)} · ${U.fmtBytes(row.downloaded)}" +
                    if (row.size > 0) " / ${U.fmtBytes(row.size)}" else ""
            textSize = 11f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setTextColor(getColor(R.color.sub))
        }
        card.addView(line2, LinearLayout.LayoutParams(-1, -2).apply { topMargin = U.dp(this@DownloadsActivity, 3f) })

        // 进度线
        val track = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.line)) }
        val fill = View(this).apply { setBackgroundColor(getColor(R.color.ink)) }
        fill.layoutParams = FrameLayout.LayoutParams(
            if (row.size > 0) (screenPx() * (row.downloaded.toFloat() / row.size)).toInt().coerceAtMost(screenPx()) else 0,
            U.dp(this, 2f)
        )
        track.addView(fill)
        card.addView(track, LinearLayout.LayoutParams(-1, U.dp(this, 2f)).apply { topMargin = U.dp(this@DownloadsActivity, 8f) })

        if (row.status == Dl.FAILED && !row.err.isNullOrBlank()) {
            val err = TextView(this).apply {
                text = row.err
                textSize = 10f
                maxLines = 1
                setTextColor(getColor(R.color.sub))
            }
            card.addView(err, LinearLayout.LayoutParams(-1, -2).apply { topMargin = U.dp(this@DownloadsActivity, 3f) })
        }

        // 操作按钮
        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        when (row.status) {
            Dl.RUNNING, Dl.QUEUED -> btns.addView(smallBtn("暂停") { DownloadEngine.pause(row.id) })
            Dl.PAUSED, Dl.FAILED -> btns.addView(smallBtn("继续") { DownloadEngine.resume(this, row.id) })
            Dl.DONE -> {
                btns.addView(smallBtn("播放") {
                    if (!DownloadEngine.playIfVideo(this, row)) DownloadEngine.openFile(this, row)
                })
                btns.addView(smallBtn("打开") { DownloadEngine.openFile(this, row) })
            }
        }
        btns.addView(smallBtn("删除") { DownloadEngine.cancel(this, row.id); rebuild() })
        card.addView(btns, LinearLayout.LayoutParams(-1, -2).apply { topMargin = U.dp(this@DownloadsActivity, 6f) })

        // 完成的行点击播放/打开
        if (row.status == Dl.DONE) {
            card.isClickable = true
            card.background = rippleBg()
            card.setOnClickListener {
                if (!DownloadEngine.playIfVideo(this, row)) DownloadEngine.openFile(this, row)
            }
        }
        contentList.addView(card, LinearLayout.LayoutParams(-1, -2))
        addDivider()
    }

    private fun screenPx(): Int = resources.displayMetrics.widthPixels

    private fun smallBtn(label: String, onClick: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.textSize = 12f
        tv.setPadding(U.dp(this, 12f), U.dp(this, 5f), U.dp(this, 12f), U.dp(this, 5f))
        tv.setTextColor(getColor(R.color.ink))
        tv.background = rippleBg()
        tv.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.marginStart = U.dp(this, 6f)
        tv.layoutParams = lp
        return tv
    }

    private fun rippleBg(): android.graphics.drawable.Drawable {
        val d = android.graphics.drawable.GradientDrawable()
        d.setColor(getColor(R.color.card))
        d.cornerRadius = U.dp(this, 14f).toFloat()
        d.setStroke(1, getColor(R.color.line))
        return android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(getColor(R.color.line)), d, null
        )
    }

    private fun statusText(row: DownloadRow): String = when (row.status) {
        Dl.QUEUED -> "排队中"
        Dl.RUNNING -> if (row.size > 0) "${(row.downloaded * 100 / row.size).coerceAtMost(100)}%" else "下载中"
        Dl.PAUSED -> "已暂停"
        Dl.DONE -> "已完成"
        Dl.FAILED -> "失败"
        else -> ""
    }

    private fun newDownloadDialog() {
        val pad = U.dp(this, 20f)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val eUrl = EditText(this).apply { hint = "下载链接（http/https 直链）" }
        col.addView(eUrl)
        AlertDialog.Builder(this)
            .setTitle("新建下载")
            .setView(col)
            .setPositiveButton("下载") { _, _ ->
                val u = eUrl.text.toString().trim()
                if (u.isNotEmpty()) {
                    val name = U.guessFilename(u, null, null)
                    DownloadEngine.enqueue(this, u, name, null)
                    U.toast(this, "开始下载")
                    rebuild()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
