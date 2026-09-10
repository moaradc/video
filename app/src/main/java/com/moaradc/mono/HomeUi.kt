package com.moaradc.mono

import android.annotation.SuppressLint
import android.graphics.Color
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.moaradc.mono.data.Bookmark
import com.moaradc.mono.data.Db
import com.moaradc.mono.data.PlayRecord
import com.moaradc.mono.data.WatchLaterItem
import com.moaradc.mono.player.PlayerActivity
import com.moaradc.mono.util.Prefs
import com.moaradc.mono.util.U
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 搜索引擎列表 */
val ENGINES = listOf(
    "必应" to "https://cn.bing.com/search?q=",
    "百度" to "https://www.baidu.com/s?wd=",
    "谷歌" to "https://www.google.com/search?q=",
    "DuckDuckGo" to "https://duckduckgo.com/?q="
)

/**
 * 起始页：黑白极简（参考 UC/Via 的经典布局），
 * 由代码构建，避免过多 XML。
 */
@SuppressLint("SetTextI18n")
class HomeUi(private val activity: MainActivity) {

    private val ctx: MainActivity = activity
    private val pad get() = U.dp(ctx, 20f)

    private lateinit var column: LinearLayout
    private lateinit var engineLabel: TextView
    private lateinit var searchInput: EditText
    private lateinit var linksGrid: GridLayout
    private lateinit var localRow: LinearLayout
    private lateinit var localSection: LinearLayout
    private lateinit var watchSection: LinearLayout
    private lateinit var watchRows: LinearLayout
    private lateinit var recentSection: LinearLayout
    private lateinit var recentRows: LinearLayout

    init {
        build()
        refresh()
    }

    // ---------------------------------------------------------------- 构建

    private fun build() {
        val scroll = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, U.dp(ctx, 44f), pad, U.dp(ctx, 28f))
        }
        scroll.addView(column)
        activity.homeContainer.addView(scroll)

        // ---- 品牌字标
        val word = TextView(ctx).apply {
            text = "MONO"
            textSize = 32f
            letterSpacing = 0.35f
            gravity = Gravity.CENTER
            setTextColor(ctx.getColor(R.color.ink))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        column.addView(word)
        val tag = TextView(ctx).apply {
            text = "黑白之间 · 播放与浏览"
            textSize = 11f
            letterSpacing = 0.25f
            gravity = Gravity.CENTER
            setTextColor(ctx.getColor(R.color.sub))
            layoutParams = linParams { topMargin = U.dp(ctx, 8f) }
        }
        column.addView(tag)

        // ---- 搜索胶囊
        val pill = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ctx.getDrawable(R.drawable.bg_search_pill)
            setPadding(U.dp(ctx, 16f), 0, U.dp(ctx, 16f), 0)
            layoutParams = linParams {
                topMargin = U.dp(ctx, 30f)
                height = U.dp(ctx, 48f)
            }
        }
        engineLabel = TextView(ctx).apply {
            text = ENGINES[Prefs.engine].first
            textSize = 13f
            setTextColor(ctx.getColor(R.color.ink))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val engineRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { chooseEngine() }
        }
        engineRow.addView(engineLabel)
        val caret = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_chevron_down)
            imageTintList = android.content.res.ColorStateList.valueOf(ctx.getColor(R.color.sub))
            layoutParams = LinearLayout.LayoutParams(U.dp(ctx, 14f), U.dp(ctx, 14f))
        }
        engineRow.addView(caret)
        pill.addView(engineRow)
        val divider = View(ctx).apply {
            setBackgroundColor(ctx.getColor(R.color.line))
            layoutParams = LinearLayout.LayoutParams(1, U.dp(ctx, 18f)).apply {
                marginStart = U.dp(ctx, 12f); marginEnd = U.dp(ctx, 12f)
            }
        }
        pill.addView(divider)
        searchInput = EditText(ctx).apply {
            hint = ctx.getString(R.string.search_hint)
            setHintTextColor(ctx.getColor(R.color.sub))
            setTextColor(ctx.getColor(R.color.ink))
            background = null
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    submitSearch()
                    true
                } else false
            }
        }
        pill.addView(searchInput)
        column.addView(pill)

        // ---- 四大快捷入口
        val quick = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = linParams { topMargin = U.dp(ctx, 26f) }
        }
        quickAction(quick, R.drawable.ic_watch_later, "稍后再看") { activity.openWatchLater() }
        quickAction(quick, R.drawable.ic_play_circle, "播放记录") { activity.openRecords() }
        quickAction(quick, R.drawable.ic_download, "下载管理") { activity.openDownloads() }
        quickAction(quick, R.drawable.ic_bookmark, "书签") { activity.openBookmarks() }
        column.addView(quick)

        // ---- 本地视频
        localSection = section("本地视频")
        column.addView(localSection)
        val openBtn = TextView(ctx).apply {
            text = "＋ 打开文件"
            textSize = 12f
            setTextColor(ctx.getColor(R.color.ink))
            background = ctx.getDrawable(R.drawable.bg_small_pill)
            setPadding(U.dp(ctx, 14f), U.dp(ctx, 7f), U.dp(ctx, 14f), U.dp(ctx, 7f))
            setOnClickListener { activity.pickLocalVideo() }
        }
        (localSection.getChildAt(0) as? LinearLayout)?.addView(
            openBtn,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        )
        val hscroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = linParams { topMargin = U.dp(ctx, 12f) }
        }
        localRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        hscroll.addView(localRow)
        localSection.addView(hscroll)

        // ---- 快捷方式（书签网格）
        val linksSection = section("快捷方式")
        column.addView(linksSection)
        linksGrid = GridLayout(ctx).apply {
            columnCount = 4
            layoutParams = linParams { topMargin = U.dp(ctx, 10f) }
        }
        linksSection.addView(linksGrid)

        // ---- 稍后再看
        watchSection = section("稍后再看")
        column.addView(watchSection)
        val moreWatch = TextView(ctx).apply {
            text = "全部 ›"
            textSize = 12f
            setTextColor(ctx.getColor(R.color.sub))
            setOnClickListener { activity.openWatchLater() }
        }
        (watchSection.getChildAt(0) as? LinearLayout)?.addView(
            moreWatch,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        )
        watchRows = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        watchSection.addView(watchRows)

        // ---- 最近播放
        recentSection = section("最近播放")
        column.addView(recentSection)
        val moreRecent = TextView(ctx).apply {
            text = "全部 ›"
            textSize = 12f
            setTextColor(ctx.getColor(R.color.sub))
            setOnClickListener { activity.openRecords() }
        }
        (recentSection.getChildAt(0) as? LinearLayout)?.addView(
            moreRecent,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        )
        recentRows = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        recentSection.addView(recentRows)

        // ---- 页脚
        val footer = TextView(ctx).apply {
            text = "MONO v1.0.0 · 极简播放与浏览"
            textSize = 10f
            letterSpacing = 0.15f
            gravity = Gravity.CENTER
            setTextColor(ctx.getColor(R.color.sub))
            layoutParams = linParams { topMargin = U.dp(ctx, 30f) }
        }
        column.addView(footer)
    }

    private fun linParams(config: LinearLayout.LayoutParams.() -> Unit): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.config()
        return lp
    }

    private fun section(title: String): LinearLayout {
        val sec = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = linParams { topMargin = U.dp(ctx, 30f) }
        }
        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val label = TextView(ctx).apply {
            text = title
            textSize = 12f
            letterSpacing = 0.2f
            setTextColor(ctx.getColor(R.color.ink))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        head.addView(label)
        sec.addView(head)
        val line = View(ctx).apply {
            setBackgroundColor(ctx.getColor(R.color.line))
            layoutParams = linParams { topMargin = U.dp(ctx, 10f); height = 1 }
        }
        sec.addView(line)
        return sec
    }

    private fun quickAction(parent: LinearLayout, icon: Int, label: String, click: () -> Unit) {
        val cell = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val circle = ImageView(ctx).apply {
            setImageResource(icon)
            imageTintList = android.content.res.ColorStateList.valueOf(ctx.getColor(R.color.ink))
            background = ctx.getDrawable(R.drawable.bg_circle_line)
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(U.dp(ctx, 46f), U.dp(ctx, 46f)).apply {
                gravity = Gravity.CENTER
            }
        }
        cell.addView(circle)
        val text = TextView(ctx).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(ctx.getColor(R.color.sub))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = U.dp(ctx, 6f)
            }
        }
        cell.addView(text)
        parent.addView(cell)
    }

    // ---------------------------------------------------------------- 数据

    fun refresh() {
        U.runBg {
            val links = Db.bookmarks()
            val watch = Db.watchLater().take(3)
            val records = Db.records(200)
            val locals = records.filter { it.kind == "local" }.distinctBy { it.url }.take(4)
            val recents = records.take(4)
            val bookmarked = links.map { it.url }.toSet()
            U.runMain {
                rebuildLinks(links)
                rebuildLocals(locals)
                rebuildWatch(watch)
                rebuildRecent(recents)
                activity.syncBookmarks(bookmarked)
            }
        }
    }

    private fun rebuildLinks(list: List<Bookmark>) {
        linksGrid.removeAllViews()
        list.forEach { b ->
            val cell = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setOnClickListener { activity.loadUrl(b.url) }
                setOnLongClickListener { editLink(b); true }
                layoutParams = GridLayout.LayoutParams().apply {
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    width = 0
                    topMargin = U.dp(ctx, 10f)
                    bottomMargin = U.dp(ctx, 10f)
                }
            }
            val mono = TextView(ctx).apply {
                text = U.monogram(b.title.ifBlank { U.hostOf(b.url) })
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(ctx.getColor(R.color.ink))
                background = ctx.getDrawable(R.drawable.bg_circle_line)
                layoutParams = LinearLayout.LayoutParams(U.dp(ctx, 46f), U.dp(ctx, 46f)).apply {
                    gravity = Gravity.CENTER
                }
            }
            cell.addView(mono)
            val label = TextView(ctx).apply {
                text = b.title.ifBlank { U.hostOf(b.url) }
                textSize = 10f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setTextColor(ctx.getColor(R.color.sub))
                layoutParams = LinearLayout.LayoutParams(
                    U.dp(ctx, 76f), ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = U.dp(ctx, 6f) }
            }
            cell.addView(label)
            linksGrid.addView(cell)
        }
        // 添加按钮
        val addCell = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setOnClickListener { activity.addBookmarkDialog() }
            layoutParams = GridLayout.LayoutParams().apply {
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                width = 0
                topMargin = U.dp(ctx, 10f)
                bottomMargin = U.dp(ctx, 10f)
            }
        }
        val addMono = TextView(ctx).apply {
            text = "＋"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(ctx.getColor(R.color.sub))
            background = ctx.getDrawable(R.drawable.bg_circle_line)
            layoutParams = LinearLayout.LayoutParams(U.dp(ctx, 46f), U.dp(ctx, 46f)).apply {
                gravity = Gravity.CENTER
            }
        }
        addCell.addView(addMono)
        val addLabel = TextView(ctx).apply {
            text = "添加"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(ctx.getColor(R.color.sub))
            layoutParams = LinearLayout.LayoutParams(
                U.dp(ctx, 76f), ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = U.dp(ctx, 6f) }
        }
        addCell.addView(addLabel)
        linksGrid.addView(addCell)
    }

    private fun editLink(b: Bookmark) {
        val opts = arrayOf("编辑", "删除")
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(b.title)
            .setItems(opts) { _, which ->
                if (which == 1) {
                    U.runBg {
                        Db.deleteBookmark(b.id)
                        U.runMain { refresh() }
                    }
                } else {
                    activity.editBookmarkDialog(b)
                }
            }
            .show()
    }

    private fun rebuildLocals(list: List<PlayRecord>) {
        localRow.removeAllViews()
        localSection.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        list.forEach { r ->
            val tile = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = ctx.getDrawable(R.drawable.bg_card)
                setPadding(U.dp(ctx, 10f), U.dp(ctx, 10f), U.dp(ctx, 10f), U.dp(ctx, 10f))
                setOnClickListener {
                    PlayerActivity.start(ctx, r.url, r.title, (r.position * 1000).toLong())
                }
                layoutParams = LinearLayout.LayoutParams(U.dp(ctx, 96f), U.dp(ctx, 56f)).apply {
                    marginEnd = U.dp(ctx, 10f)
                }
            }
            val glyph = ImageView(ctx).apply {
                setImageResource(R.drawable.ic_video)
                imageTintList = android.content.res.ColorStateList.valueOf(ctx.getColor(R.color.sub))
                layoutParams = LinearLayout.LayoutParams(U.dp(ctx, 20f), U.dp(ctx, 20f))
            }
            tile.addView(glyph)
            val name = TextView(ctx).apply {
                text = r.title
                textSize = 9f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
                gravity = Gravity.CENTER
                setTextColor(ctx.getColor(R.color.sub))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            tile.addView(name)
            localRow.addView(tile)
        }
    }

    private fun rebuildWatch(list: List<WatchLaterItem>) {
        watchRows.removeAllViews()
        watchSection.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        list.forEach { w ->
            watchRows.addView(recordRow(
                R.drawable.ic_watch_later, w.title,
                "${timeAgo(w.time)} · ${if (w.kind == "local") "本地" else U.hostOf(w.url)}"
            ) {
                if (w.kind == "local") PlayerActivity.start(ctx, w.url, w.title, 0)
                else activity.loadUrl(w.url)
            })
        }
    }

    private fun rebuildRecent(list: List<PlayRecord>) {
        recentRows.removeAllViews()
        recentSection.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        list.forEach { r ->
            val pct = if (r.duration > 0) (r.position / r.duration * 100).toInt() else 0
            recentRows.addView(recordRow(
                R.drawable.ic_play_circle, r.title,
                "看到 ${pct}% · ${timeAgo(r.time)}"
            ) {
                if (r.kind == "local") PlayerActivity.start(ctx, r.url, r.title, (r.position * 1000).toLong())
                else activity.loadUrl(r.url)
            })
        }
    }

    private fun recordRow(icon: Int, title: String, sub: String, click: () -> Unit): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { click() }
            setPadding(0, U.dp(ctx, 12f), 0, U.dp(ctx, 12f))
        }
        val iv = ImageView(ctx).apply {
            setImageResource(icon)
            imageTintList = android.content.res.ColorStateList.valueOf(ctx.getColor(R.color.sub))
            layoutParams = LinearLayout.LayoutParams(U.dp(ctx, 22f), U.dp(ctx, 22f))
        }
        row.addView(iv)
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = U.dp(ctx, 14f)
            }
        }
        val t = TextView(ctx).apply {
            text = title
            textSize = 14f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(ctx.getColor(R.color.ink))
        }
        col.addView(t)
        val s = TextView(ctx).apply {
            text = sub
            textSize = 11f
            setTextColor(ctx.getColor(R.color.sub))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = U.dp(ctx, 3f)
            }
        }
        col.addView(s)
        row.addView(col)
        val arrow = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_chevron_right)
            imageTintList = android.content.res.ColorStateList.valueOf(ctx.getColor(R.color.sub))
            layoutParams = LinearLayout.LayoutParams(U.dp(ctx, 16f), U.dp(ctx, 16f)).apply {
                marginStart = U.dp(ctx, 10f)
            }
        }
        row.addView(arrow)
        return row
    }

    private fun chooseEngine() {
        val names = ENGINES.map { it.first }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("搜索引擎")
            .setSingleChoiceItems(names, Prefs.engine) { d, which ->
                Prefs.engine = which
                engineLabel.text = ENGINES[which].first
                d.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun submitSearch() {
        val q = searchInput.text.toString().trim()
        if (q.isEmpty()) return
        val url = if (U.isLikelyUrl(q)) U.normalizeUrl(q) else U.buildSearch(q)
        searchInput.setText("")
        activity.loadUrl(url)
    }

    private fun timeAgo(t: Long): String {
        val diff = System.currentTimeMillis() - t
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3600_000 -> "${diff / 60_000}分钟前"
            diff < 86400_000 -> "${diff / 3600_000}小时前"
            diff < 172800_000 -> "昨天"
            else -> SimpleDateFormat("M月d日", Locale.CHINA).format(Date(t))
        }
    }
}
