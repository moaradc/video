package com.moaradc.mono

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebView.HitTestResult
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.moaradc.mono.data.Bookmark
import com.moaradc.mono.data.Db
import com.moaradc.mono.data.HistoryItem
import com.moaradc.mono.downloads.DownloadEngine
import com.moaradc.mono.player.PlayerActivity
import com.moaradc.mono.ui.BookmarksActivity
import com.moaradc.mono.ui.DownloadsActivity
import com.moaradc.mono.ui.HistoryActivity
import com.moaradc.mono.ui.RecordsActivity
import com.moaradc.mono.ui.SettingsActivity
import com.moaradc.mono.ui.WatchLaterActivity
import com.moaradc.mono.util.Prefs
import com.moaradc.mono.util.U
import com.moaradc.mono.web.BrowserHost
import com.moaradc.mono.web.MonoJs
import com.moaradc.mono.web.Tab
import com.moaradc.mono.web.TabManager
import com.moaradc.mono.web.VideoTheater
import com.moaradc.mono.web.WebFactory
import android.content.res.Configuration

class MainActivity : AppCompatActivity(), BrowserHost {

    lateinit var root: FrameLayout
    lateinit var homeContainer: FrameLayout
    lateinit var browserArea: LinearLayout
    lateinit var webContainer: FrameLayout
    lateinit var addressArea: FrameLayout
    lateinit var addressText: TextView
    lateinit var addressEdit: EditText
    lateinit var btnRefresh: ImageView
    lateinit var progressLine: View
    lateinit var findBar: View
    lateinit var findInput: EditText
    lateinit var findCount: TextView
    lateinit var btnBack: ImageView
    lateinit var btnForward: ImageView
    lateinit var btnHome: ImageView
    lateinit var tabBadge: TextView
    lateinit var overlayRoot: FrameLayout
    lateinit var scrim: View

    var tabs: TabManager = TabManager(this)
    lateinit var theater: VideoTheater
    var homeUi: HomeUi? = null

    private var menuSheet: LinearLayout? = null
    private var sheetDim: View? = null
    private var tabsOverlay: ViewGroup? = null
    private var tabsOverlayDim: View? = null
    private var appliedUiRev = -1

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
            }
            PlayerActivity.start(this, uri.toString(), uri.lastPathSegment ?: "本地视频", 0L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        theater = VideoTheater(this)
        homeUi = HomeUi(this)
        wireBottomBar()
        wireAddressBar()
        wireFindBar()
        tabs.newTab()
        handleIntent(intent)
        appliedUiRev = Prefs.uiRev
        applyDim()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(VideoTheater.EXTRA_RESTORE, false)) {
            theater.restoreInApp()
        }
        val data = intent.data
        if (intent.action == Intent.ACTION_VIEW && data != null) {
            loadUrl(data.toString())
        }
    }

    private fun bindViews() {
        root = findViewById(R.id.root)
        homeContainer = findViewById(R.id.homeContainer)
        browserArea = findViewById(R.id.browserArea)
        webContainer = findViewById(R.id.webContainer)
        addressArea = findViewById(R.id.addressArea)
        addressText = findViewById(R.id.addressText)
        addressEdit = findViewById(R.id.addressEdit)
        btnRefresh = findViewById(R.id.btnRefresh)
        progressLine = findViewById(R.id.progressLine)
        findBar = findViewById(R.id.findBar)
        findInput = findViewById(R.id.findInput)
        findCount = findViewById(R.id.findCount)
        btnBack = findViewById(R.id.btnBack)
        btnForward = findViewById(R.id.btnForward)
        btnHome = findViewById(R.id.btnHome)
        tabBadge = findViewById(R.id.tabBadge)
        overlayRoot = findViewById(R.id.overlayRoot)
        scrim = findViewById(R.id.scrim)
    }

    // ---------------------------------------------------------------- 导航/地址栏

    private fun wireBottomBar() {
        btnBack.setOnClickListener {
            if (closeOverlays()) return@setOnClickListener
            val wv = tabs.currentWebView()
            if (wv != null && wv.canGoBack()) wv.goBack()
            else if (tabs.count > 1) tabs.current?.let { t -> if (t.isHome) tabs.close(t) }
        }
        btnForward.setOnClickListener {
            val wv = tabs.currentWebView()
            if (wv != null && wv.canGoForward()) wv.goForward()
        }
        btnHome.setOnClickListener {
            val cur = tabs.current
            if (cur == null || !cur.isHome) tabs.newTab()
        }
        findViewById<View>(R.id.btnTabs).setOnClickListener { showTabsOverlay() }
        findViewById<View>(R.id.btnMenu).setOnClickListener { showMenuSheet() }
    }

    private fun wireAddressBar() {
        addressText.setOnClickListener { enterAddressEdit() }
        addressEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submitAddress()
                true
            } else false
        }
        btnRefresh.setOnClickListener {
            val t = tabs.current
            if (t != null && !t.isHome) {
                if (t.loading) t.webView?.stopLoading() else t.webView?.reload()
            }
        }
    }

    private fun enterAddressEdit() {
        val t = tabs.current
        addressText.visibility = View.GONE
        addressEdit.visibility = View.VISIBLE
        addressEdit.setText(if (t != null && !t.isHome) t.url else "")
        addressEdit.setSelection(addressEdit.text.length)
        addressEdit.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(addressEdit, 0)
    }

    fun submitAddress() {
        val raw = addressEdit.text.toString().trim()
        exitAddressEdit()
        if (raw.isNotEmpty()) {
            val url = if (U.isLikelyUrl(raw)) U.normalizeUrl(raw) else U.buildSearch(raw)
            loadUrl(url)
        }
    }

    private fun exitAddressEdit() {
        addressEdit.visibility = View.GONE
        addressText.visibility = View.VISIBLE
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(addressEdit.windowToken, 0)
    }

    fun loadUrl(url: String) {
        val t = tabs.current ?: tabs.newTab()
        if (t.isHome) tabs.loadIn(t, url) else t.webView?.loadUrl(url)
    }

    // ---------------------------------------------------------------- BrowserHost

    override fun createWebView(tab: Tab): WebView = WebFactory.create(this, tab)

    override fun onTabChanged(tab: Tab?) {
        updateUi()
        if (tab == null || tab.isHome) homeUi?.refresh()
    }

    override fun onTabClosed(tab: Tab) {
        theater.onHostTabClosed(tab)
        updateUi()
    }

    override fun attachWebView(wv: WebView) {
        webContainer.removeAllViews()
        webContainer.addView(wv, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    override fun detachWebView(wv: WebView) {
        if (wv.parent === webContainer) webContainer.removeView(wv)
    }

    override fun destroyWebView(wv: WebView) {
        detachWebView(wv)
        wv.stopLoading()
        wv.destroy()
    }

    // ---------------------------------------------------------------- WebView 回调

    fun onTabStarted(tab: Tab) {
        tab.loading = true
        if (tabs.current === tab) updateUi()
    }

    fun onTabFinished(tab: Tab) {
        tab.loading = false
        if (saveHistoryAllowed() && !tab.isHome) {
            U.runBg { Db.updateHistoryTitle(tab.url, tab.title) }
        }
        if (tabs.current === tab) updateUi()
    }

    fun onTabError(tab: Tab, url: String) {
        tab.loading = false
        U.toast(this, "无法打开 ${U.hostOf(url)}")
        if (tabs.current === tab) updateUi()
    }

    fun onProgress(tab: Tab, p: Int) {
        if (tabs.current === tab) {
            progressLine.scaleX = (p / 100f).coerceIn(0.02f, 1f)
            if (p >= 100) progressLine.postDelayed({ progressLine.scaleX = 0f }, 400)
        }
    }

    fun onTabUi(tab: Tab) {
        if (tabs.current === tab) updateUi()
    }

    fun recordVisit(tab: Tab, url: String) {
        if (!saveHistoryAllowed()) return
        if (!url.startsWith("http")) return
        val title = tab.title.ifBlank { U.hostOf(url) }
        U.runBg { Db.addHistory(title, url) }
    }

    private fun saveHistoryAllowed(): Boolean = Prefs.saveHistory

    fun onDownloadRequested(url: String, contentDisposition: String?, mime: String?) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            U.toast(this, "此链接暂不支持直接下载")
            return
        }
        val name = U.guessFilename(url, contentDisposition, mime)
        DownloadEngine.enqueue(this, url, name, mime ?: "")
        U.toast(this, "开始下载")
    }

    fun onWebViewLongClick(wv: WebView) {
        val r = wv.hitTestResult
        val url = r.extra ?: return
        val isImage = r.type == HitTestResult.IMAGE_TYPE || r.type == HitTestResult.SRC_IMAGE_ANCHOR_TYPE
        val items = if (isImage) arrayOf("新标签页打开图片", "下载图片", "复制链接") else arrayOf("新标签页打开", "下载链接", "复制链接", "加入稍后再看")
        AlertDialog.Builder(this)
            .setTitle(U.hostOf(url))
            .setItems(items) { _, which ->
                when (items[which]) {
                    "新标签页打开图片", "新标签页打开" -> tabs.newTab(url)
                    "下载图片", "下载链接" -> onDownloadRequested(url, null, null)
                    "复制链接" -> copyLink(url)
                    "加入稍后再看" -> U.runBg {
                        Db.addWatchLater("web", U.hostOf(url), url)
                        U.toast(this, "已加入稍后再看")
                    }
                }
            }
            .show()
    }

    private fun copyLink(url: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("url", url))
        U.toast(this, "链接已复制")
    }

    // ---------------------------------------------------------------- UI 刷新

    fun updateUi() {
        val t = tabs.current
        val home = t == null || t.isHome
        browserArea.visibility = if (home) View.GONE else View.VISIBLE
        homeContainer.visibility = if (home) View.VISIBLE else View.GONE
        addressText.text = when {
            t == null -> ""
            t.isHome -> getString(R.string.search_hint)
            else -> t.title.ifBlank { U.hostOf(t.url) }
        }
        val wv = if (home) null else t?.webView
        btnBack.isEnabled = wv?.canGoBack() == true
        btnForward.isEnabled = wv?.canGoForward() == true
        btnBack.alpha = if (btnBack.isEnabled) 1f else 0.3f
        btnForward.alpha = if (btnForward.isEnabled) 1f else 0.3f
        if (tabs.count > 1) {
            tabBadge.visibility = View.VISIBLE
            tabBadge.text = if (tabs.count > 99) "…" else tabs.count.toString()
        } else tabBadge.visibility = View.GONE
    }

    // ---------------------------------------------------------------- 菜单弹层

    @SuppressLint("RtlHardcoded")
    private fun showMenuSheet() {
        if (menuSheet != null) return
        val dim = View(this).apply {
            setBackgroundColor(0x66000000)
            setOnClickListener { hideMenuSheet() }
        }
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(R.drawable.bg_menu_sheet, theme)
            val pad = U.dp(this@MainActivity, 6f)
            setPadding(pad, U.dp(this@MainActivity, 14f), pad, U.dp(this@MainActivity, 14f))
        }
        val t = tabs.current
        val hasPage = t != null && !t.isHome
        val rows = mutableListOf<Pair<Int, String>>()
        rows.add(R.drawable.ic_plus to "新标签页")
        if (hasPage) rows.add(R.drawable.ic_search to "查找网页内容")
        if (hasPage) rows.add(R.drawable.ic_bookmark to if (bookmarkedFlag) "取消收藏" else "收藏本页")
        if (hasPage) rows.add(R.drawable.ic_watch_later to "加入稍后再看")
        rows.add(R.drawable.ic_download to "下载管理")
        rows.add(R.drawable.ic_clock to "历史记录")
        rows.add(R.drawable.ic_play_circle to "播放记录")
        rows.add(R.drawable.ic_bookmark to "书签管理")
        if (hasPage) rows.add(R.drawable.ic_pip to "小窗播放当前视频")
        rows.add(R.drawable.ic_globe to if (Prefs.desktopUa) "桌面版网站 ✓" else "桌面版网站")
        rows.add(R.drawable.ic_moon to if (isDarkNow()) "夜间模式 ✓" else "夜间模式")
        rows.add(R.drawable.ic_settings to "设置")

        for ((icon, label) in rows) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val h = U.dp(this@MainActivity, 48f)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
                isClickable = true
                setOnClickListener { hideMenuSheet(); onMenuAction(label) }
            }
            val iv = ImageView(this).apply {
                setImageResource(icon)
                imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.ink))
                val s = U.dp(this@MainActivity, 20f)
                layoutParams = LinearLayout.LayoutParams(s, s)
            }
            val tv = TextView(this).apply {
                text = label
                textSize = 14f
                setTextColor(getColor(R.color.ink))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginStart = U.dp(this@MainActivity, 16f)
                layoutParams = lp
            }
            row.addView(iv)
            row.addView(tv)
            sheet.addView(row)
        }
        overlayRoot.addView(dim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        )
        overlayRoot.addView(sheet, lp)
        sheet.translationY = sheet.height.toFloat() + 300
        sheet.post { sheet.animate().translationY(0f).setDuration(180).start() }
        sheetDim = dim
        menuSheet = sheet
    }

    private fun hideMenuSheet() {
        menuSheet?.animate()?.translationY((menuSheet?.height ?: 0) + 200f)?.setDuration(160)?.withEndAction {
            overlayRoot.removeView(menuSheet)
            sheetDim?.let { overlayRoot.removeView(it) }
            menuSheet = null
            sheetDim = null
        }?.start() ?: run {
            sheetDim?.let { overlayRoot.removeView(it) }
            menuSheet = null
            sheetDim = null
        }
    }

    private val bookmarkedFlag: Boolean
        get() {
            val t = tabs.current ?: return false
            return bookmarkedUrls.contains(t.url)
        }

    private val bookmarkedUrls = mutableSetOf<String>()

    fun refreshBookmarkFlag() {
        U.runBg {
            bookmarkedUrls.clear()
            Db.bookmarks().forEach { bookmarkedUrls.add(it.url) }
            U.runMain { }
        }
    }

    /** HomeUi 刷新后的书签集合同步 */
    fun syncBookmarks(urls: Set<String>) {
        bookmarkedUrls.clear()
        bookmarkedUrls.addAll(urls)
    }

    fun editBookmarkDialog(b: Bookmark) {
        val pad = U.dp(this, 20f)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val eTitle = EditText(this).apply {
            hint = "名称"; setText(b.title)
        }
        val eUrl = EditText(this).apply {
            hint = "网址"; setText(b.url)
        }
        col.addView(eTitle)
        col.addView(eUrl)
        AlertDialog.Builder(this)
            .setTitle("编辑快捷方式")
            .setView(col)
            .setPositiveButton("保存") { _, _ ->
                val t = eTitle.text.toString().trim()
                val u = eUrl.text.toString().trim()
                if (u.isNotEmpty()) {
                    U.runBg {
                        Db.updateBookmark(b.id, t.ifBlank { U.hostOf(u) }, U.normalizeUrl(u))
                        U.runMain { homeUi?.refresh() }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun onMenuAction(label: String) {
        val t = tabs.current
        when (label) {
            "新标签页" -> tabs.newTab()
            "查找网页内容" -> showFindBar()
            "收藏本页", "取消收藏" -> toggleBookmark(t)
            "加入稍后再看" -> t?.let {
                U.runBg {
                    Db.addWatchLater("web", it.title.ifBlank { U.hostOf(it.url) }, it.url)
                    U.runMain { U.toast(this, "已加入稍后再看") }
                }
            }
            "下载管理" -> startActivity(Intent(this, DownloadsActivity::class.java))
            "历史记录" -> startActivity(Intent(this, HistoryActivity::class.java))
            "播放记录" -> startActivity(Intent(this, RecordsActivity::class.java))
            "书签管理" -> startActivity(Intent(this, BookmarksActivity::class.java))
            "小窗播放当前视频" -> popVideoNow()
            "桌面版网站", "桌面版网站 ✓" -> {
                Prefs.desktopUa = !Prefs.desktopUa
                Prefs.bumpUi()
                WebFactory.applyUaAll(tabs.allWebViews())
                tabs.currentWebView()?.reload()
                U.toast(this, if (Prefs.desktopUa) "已切换桌面版 UA" else "已恢复移动版 UA")
            }
            "夜间模式", "夜间模式 ✓" -> {
                Prefs.theme = if (Prefs.theme == 2) 1 else 2
                Prefs.bumpUi()
                App.applyTheme()
                recreate()
            }
            "设置" -> startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun popVideoNow() {
        val wv = tabs.currentWebView() ?: return
        wv.evaluateJavascript("(window.__monoPop && __monoPop())", null)
    }

    private fun toggleBookmark(t: Tab?) {
        if (t == null || t.isHome) return
        val url = t.url
        U.runBg {
            if (Db.isBookmarked(url)) {
                Db.deleteBookmarkByUrl(url)
                U.runMain {
                    bookmarkedUrls.remove(url)
                    U.toast(this, "已取消收藏")
                    homeUi?.refresh()
                }
            } else {
                val bytes = t.favicon?.let { f ->
                    try {
                        java.io.ByteArrayOutputStream().use { o ->
                            f.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, o)
                            o.toByteArray()
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                Db.addBookmark(t.title.ifBlank { U.hostOf(url) }, url, bytes)
                U.runMain {
                    bookmarkedUrls.add(url)
                    U.toast(this, "已收藏")
                    homeUi?.refresh()
                }
            }
        }
    }

    // ---------------------------------------------------------------- 标签页覆盖层

    private fun showTabsOverlay() {
        if (tabsOverlay != null) return
        val dim = View(this).apply {
            setBackgroundColor(getColor(R.color.bg))
            setOnClickListener { hideTabsOverlay() }
        }
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // 头部
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = U.dp(this@MainActivity, 16f)
            setPadding(pad, U.dp(this@MainActivity, 12f), pad, U.dp(this@MainActivity, 12f))
        }
        val title = TextView(this).apply {
            text = "标签页 · ${tabs.count}"
            textSize = 16f
            setTextColor(getColor(R.color.ink))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val newTab = TextView(this).apply {
            text = "新建"
            textSize = 13f
            setPadding(U.dp(this@MainActivity, 10f), U.dp(this@MainActivity, 6f), U.dp(this@MainActivity, 10f), U.dp(this@MainActivity, 6f))
            setTextColor(getColor(R.color.ink))
            background = resources.getDrawable(R.drawable.bg_search_pill, theme)
            setOnClickListener { hideTabsOverlay(); tabs.newTab() }
        }
        val closeAll = TextView(this).apply {
            text = "全部关闭"
            textSize = 13f
            setPadding(U.dp(this@MainActivity, 10f), U.dp(this@MainActivity, 6f), U.dp(this@MainActivity, 10f), U.dp(this@MainActivity, 6f))
            setTextColor(getColor(R.color.ink))
            setOnClickListener { hideTabsOverlay(); tabs.closeAll(); tabs.newTab() }
        }
        header.addView(title)
        header.addView(newTab)
        (newTab.layoutParams as LinearLayout.LayoutParams).marginEnd = U.dp(this, 8f)
        header.addView(closeAll)
        panel.addView(header)

        val list = android.widget.RecyclerView(this).apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MainActivity)
            adapter = TabsAdapter()
        }
        panel.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        View(this).apply { setBackgroundColor(getColor(R.color.line)) }.also {
            panel.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        }
        overlayRoot.addView(dim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        overlayRoot.addView(panel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        tabsOverlayDim = dim
        tabsOverlay = panel
    }

    private fun hideTabsOverlay() {
        tabsOverlay?.let { overlayRoot.removeView(it) }
        tabsOverlayDim?.let { overlayRoot.removeView(it) }
        tabsOverlay = null
        tabsOverlayDim = null
    }

    inner class TabsAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<TabsAdapter.VH>() {
        val items = tabs.tabs.toList()

        inner class VH(val row: LinearLayout) : androidx.recyclerview.widget.RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val pad = U.dp(this@MainActivity, 16f)
                setPadding(pad, U.dp(this@MainActivity, 10f), pad, U.dp(this@MainActivity, 10f))
                isClickable = true
            }
            val mono = TextView(this@MainActivity).apply {
                textSize = 15f
                setTextColor(getColor(R.color.ink))
                gravity = Gravity.CENTER
                background = U.monogramDrawable(this@MainActivity, "#", isDarkNow())
                val s = U.dp(this@MainActivity, 34f)
                layoutParams = LinearLayout.LayoutParams(s, s)
            }
            val col = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                (layoutParams as LinearLayout.LayoutParams).marginStart = U.dp(this@MainActivity, 12f)
            }
            val t1 = TextView(this@MainActivity).apply {
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(getColor(R.color.ink))
            }
            val t2 = TextView(this@MainActivity).apply {
                textSize = 11f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(getColor(R.color.sub))
            }
            col.addView(t1)
            col.addView(t2)
            val close = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_close)
                imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.sub))
                val s = U.dp(this@MainActivity, 20f)
                layoutParams = LinearLayout.LayoutParams(s, s)
                setOnClickListener {
                    val tab = items.getOrNull(bindingAdapterPosition) ?: return@setOnClickListener
                    tabs.close(tab)
                    hideTabsOverlay()
                    if (tabs.count > 0) showTabsOverlay()
                }
            }
            row.addView(mono)
            row.addView(col)
            row.addView(close)
            row.setOnClickListener {
                val tab = items.getOrNull(bindingAdapterPosition) ?: return@setOnClickListener
                tabs.switch(tab)
                hideTabsOverlay()
            }
            row.tag = listOf(mono, t1, t2) as List<Any>
            return VH(row)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val tab = items[position]
            val views = holder.row.tag as List<*>
            val mono = views[0] as TextView
            val t1 = views[1] as TextView
            val t2 = views[2] as TextView
            mono.text = U.monogram(tab.title)
            mono.background = U.monogramDrawable(this@MainActivity, "#", isDarkNow())
            t1.text = tab.title
            t2.text = if (tab.isHome) "起始页" else U.hostOf(tab.url)
            holder.row.setBackgroundColor(
                if (tab === tabs.current) getColor(R.color.card) else getColor(R.color.bg)
            )
        }

        override fun getItemCount(): Int = items.size
    }

    // ---------------------------------------------------------------- 页内查找

    private fun wireFindBar() {
        findInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doFind(findInput.text.toString())
                true
            } else false
        }
        findViewById<View>(R.id.findNext).setOnClickListener { tabs.currentWebView()?.findNext(true) }
        findViewById<View>(R.id.findPrev).setOnClickListener { tabs.currentWebView()?.findNext(false) }
        findViewById<View>(R.id.findClose).setOnClickListener { closeFindBar() }
    }

    private fun showFindBar() {
        findBar.visibility = View.VISIBLE
        findInput.setText("")
        findCount.text = ""
        findInput.requestFocus()
    }

    private fun doFind(q: String) {
        if (q.isBlank()) return
        val wv = tabs.currentWebView() ?: return
        wv.setFindListener { active, total, _ ->
            findCount.text = if (total > 0) "$active/$total" else "0"
        }
        wv.findAllAsync(q)
    }

    private fun closeFindBar() {
        findBar.visibility = View.GONE
        tabs.currentWebView()?.clearMatches()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(findInput.windowToken, 0)
    }

    // ---------------------------------------------------------------- 悬浮窗权限

    fun ensureOverlayPermission(cb: () -> Unit) {
        if (android.provider.Settings.canDrawOverlays(this)) {
            cb()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("需要悬浮窗权限")
            .setMessage("小窗播放器需要在其他应用上层显示。\n请在系统设置中允许「显示在其他应用上层」。")
            .setPositiveButton("去授权") { _, _ ->
                startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------------------------------------------------------------- 主题 / 压暗

    private fun isDarkNow(): Boolean {
        val mode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    fun applyDim() {
        val dark = isDarkNow()
        val on = dark && Prefs.dimOn
        scrim.visibility = if (on) View.VISIBLE else View.GONE
        scrim.alpha = (Prefs.dimLevel / 100f).coerceIn(0.05f, 0.75f)
    }

    override fun onResume() {
        super.onResume()
        homeUi?.refresh()
        refreshBookmarkFlag()
        if (appliedUiRev != Prefs.uiRev) {
            appliedUiRev = Prefs.uiRev
            applyDim()
            recreate()
        } else {
            applyDim()
        }
    }

    override fun onBackPressed() {
        when {
            addressEdit.visibility == View.VISIBLE -> exitAddressEdit()
            menuSheet != null -> hideMenuSheet()
            tabsOverlay != null -> hideTabsOverlay()
            findBar.visibility == View.VISIBLE -> closeFindBar()
            theater.mode == VideoTheater.Mode.INAPP -> theater.exitFromUser()
            else -> {
                val wv = tabs.currentWebView()
                if (wv != null && wv.canGoBack()) wv.goBack()
                else if (tabs.count > 1) tabs.current?.let { tabs.close(it) }
                else moveTaskToBack(true)
            }
        }
    }

    private fun closeOverlays(): Boolean {
        if (menuSheet != null) { hideMenuSheet(); return true }
        if (tabsOverlay != null) { hideTabsOverlay(); return true }
        if (findBar.visibility == View.VISIBLE) { closeFindBar(); return true }
        return false
    }

    // ---------------------------------------------------------------- 首页回调

    fun pickLocalVideo() {
        pickFile.launch(arrayOf("video/*"))
    }

    fun openWatchLater() = startActivity(Intent(this, WatchLaterActivity::class.java))
    fun openRecords() = startActivity(Intent(this, RecordsActivity::class.java))
    fun openDownloads() = startActivity(Intent(this, DownloadsActivity::class.java))
    fun openBookmarks() = startActivity(Intent(this, BookmarksActivity::class.java))
    fun openHistory() = startActivity(Intent(this, HistoryActivity::class.java))
    fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    fun addBookmarkDialog() {
        val pad = U.dp(this, 20f)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val eTitle = EditText(this).apply { hint = "名称" }
        val eUrl = EditText(this).apply { hint = "网址" }
        col.addView(eTitle)
        col.addView(eUrl)
        AlertDialog.Builder(this)
            .setTitle("添加快捷方式")
            .setView(col)
            .setPositiveButton("添加") { _, _ ->
                val t = eTitle.text.toString().trim()
                val u = eUrl.text.toString().trim()
                if (u.isNotEmpty()) {
                    U.runBg {
                        Db.addBookmark(t.ifBlank { U.hostOf(u) }, U.normalizeUrl(u), null)
                        U.runMain { homeUi?.refresh() }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
