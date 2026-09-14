package com.moaradc.mono.web

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.moaradc.mono.MainActivity
import com.moaradc.mono.R
import com.moaradc.mono.data.Db
import com.moaradc.mono.player.FloatBus
import com.moaradc.mono.player.FloatPlayerService
import com.moaradc.mono.util.Prefs
import com.moaradc.mono.util.U

/** 网页全屏视频交给小窗前的交接数据 */
class WebHandoff(
    val view: View,
    val callback: WebChromeClient.CustomViewCallback?,
    val tab: Tab?,
    val theater: VideoTheater?
)

/**
 * 视频剧场：接管网页全屏视频（onShowCustomView），
 * 以 Mono 自身的播控 UI 替代站点原生/自研播放器，并可弹出到全局小窗。
 */
class VideoTheater(private val activity: MainActivity) {

    enum class Mode { NONE, INAPP, FLOAT }

    var mode = Mode.NONE
        private set
    private var customView: View? = null
    private var callback: WebChromeClient.CustomViewCallback? = null
    private var hostTab: Tab? = null
    private var isVideoFullscreen = true
    private var state: VideoState? = null

    // —— 应用内全屏 UI ——
    private var overlay: FrameLayout? = null
    private var contentFrame: FrameLayout? = null
    private var controls: FrameLayout? = null
    private var progressFill: View? = null
    private var progressTrack: FrameLayout? = null
    private var timeText: TextView? = null
    private var playIcon: ImageView? = null
    private var pipButton: ImageView? = null

    private val handler = Handler(Looper.getMainLooper())
    private var polling = false
    private val pollTask = object : Runnable {
        override fun run() {
            if (!polling) return
            val wv = hostTab?.webView
            if (wv == null) { stopPolling(); return }
            wv.evaluateJavascript(MonoJs.STATE) { res ->
                val st = VideoState.parse(res) ?: return@evaluateJavascript
                state = st
                renderState(st)
            }
            handler.postDelayed(this, 500)
        }
    }

    /** WebView 要求进入网页全屏 */
    fun show(view: View, cb: WebChromeClient.CustomViewCallback, tab: Tab) {
        if (mode != Mode.NONE) {
            // 旧会话先收尾
            exitFromUser()
        }
        customView = view
        callback = cb
        hostTab = tab
        mode = Mode.INAPP
        tab.webView?.evaluateJavascript(MonoJs.IS_VIDEO_FS) { r ->
            isVideoFullscreen = (r != "0")
            if (Prefs.autoFloat && isVideoFullscreen) {
                handler.postDelayed({ if (mode == Mode.INAPP) popToFloat() }, 400)
            }
        }
        buildInAppOverlay()
        startPolling()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildInAppOverlay() {
        val view = customView ?: return
        removeFromParent(view)

        val ov = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }

        val cf = FrameLayout(activity)
        cf.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER)
        cf.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
        ov.addView(cf)

        // —— 控制层 ——
        val ctl = FrameLayout(activity)

        // 顶部：关闭 + 小窗
        val top = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        top.layoutParams = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END)
        top.setPadding(U.dp(activity, 8f), U.dp(activity, 12f), U.dp(activity, 8f), 0)
        val close = roundIconBtn(R.drawable.ic_close) { exitFromUser() }
        val pip = roundIconBtn(R.drawable.ic_pip) { popToFloat() }
        pipButton = pip
        top.addView(close)
        top.addView(pip)
        ctl.addView(top)

        // 底部：进度线 + 时间 + 播放/暂停
        val bottom = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        bottom.layoutParams = FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM)
        bottom.setPadding(U.dp(activity, 16f), 0, U.dp(activity, 16f), U.dp(activity, 14f))

        val play = ImageView(activity)
        play.setImageResource(R.drawable.ic_pause)
        play.setColorFilter(Color.WHITE)
        play.layoutParams = LinearLayout.LayoutParams(U.dp(activity, 30f), U.dp(activity, 30f))
        play.setOnClickListener { togglePlay() }
        playIcon = play

        val time = TextView(activity).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
        }
        val line = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        line.addView(play)
        line.addView(time)
        (time.layoutParams as LinearLayout.LayoutParams).marginStart = U.dp(activity, 12f)
        bottom.addView(line)

        val track = FrameLayout(activity).apply { setBackgroundColor(0x33FFFFFF) }
        track.layoutParams = LinearLayout.LayoutParams(MATCH, U.dp(activity, 2f)).apply {
            topMargin = U.dp(activity, 10f)
        }
        val fill = View(activity).apply { setBackgroundColor(Color.WHITE) }
        fill.layoutParams = FrameLayout.LayoutParams(0, MATCH)
        track.addView(fill)
        track.setOnTouchListener { v, ev ->
            if (ev.action == android.view.MotionEvent.ACTION_UP || ev.action == android.view.MotionEvent.ACTION_DOWN) {
                val st = state
                if (st != null && st.durSec > 0 && v.width > 0) {
                    val f = (ev.x / v.width).coerceIn(0f, 1f)
                    seekTo(st.durSec * f)
                }
            }
            true
        }
        bottom.addView(track)
        progressTrack = track
        progressFill = fill
        timeText = time
        ctl.addView(bottom)

        // 点按切换控制层显隐
        cf.setOnClickListener { toggleControls() }
        ov.addView(ctl)
        controls = ctl
        scheduleHideControls()

        activity.overlayRoot.addView(
            ov,
            FrameLayout.LayoutParams(MATCH, MATCH)
        )
        overlay = ov
        contentFrame = cf
    }

    private fun roundIconBtn(icon: Int, onClick: () -> Unit): ImageView {
        val iv = ImageView(activity)
        iv.setImageResource(icon)
        iv.setColorFilter(Color.WHITE)
        val pad = U.dp(activity, 8f)
        iv.setPadding(pad, pad, pad, pad)
        iv.setBackgroundResource(R.drawable.bg_float_btn)
        iv.layoutParams = LinearLayout.LayoutParams(U.dp(activity, 40f), U.dp(activity, 40f)).apply {
            marginStart = U.dp(activity, 8f)
        }
        iv.setOnClickListener { onClick() }
        return iv
    }

    private fun toggleControls() {
        val c = controls ?: return
        c.visibility = if (c.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
        if (c.visibility == View.VISIBLE) scheduleHideControls()
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControls)
        handler.postDelayed(hideControls, 3200)
    }

    private val hideControls = Runnable {
        controls?.visibility = View.INVISIBLE
    }

    private fun togglePlay() {
        hostTab?.webView?.evaluateJavascript(MonoJs.TOGGLE, null)
    }

    private fun seekTo(sec: Double) {
        hostTab?.webView?.evaluateJavascript(MonoJs.seekJs(sec), null)
    }

    /** 弹出到全局小窗 */
    fun popToFloat() {
        if (mode != Mode.INAPP) return
        val view = customView ?: return
        val tab = hostTab
        activity.ensureOverlayPermission {
            mode = Mode.FLOAT
            removeFromParent(view)
            overlay?.let { activity.overlayRoot.removeView(it) }
            overlay = null
            contentFrame = null
            controls = null
            stopPolling()
            tab?.webView?.evaluateJavascript("window.__monoPinned&&window.__monoPinned(true)", null)
            FloatBus.pending = WebHandoff(view, callback, tab, this)
            FloatPlayerService.startWeb(activity)
        }
    }

    /** 从小窗还原为应用内全屏 */
    fun restoreInApp(): Boolean {
        if (mode != Mode.FLOAT) return false
        mode = Mode.INAPP
        FloatBus.service?.takeWebContent(silent = true)
        buildInAppOverlay()
        startPolling()
        return true
    }

    /** 用户点 ✕ 退出全屏 */
    fun exitFromUser() {
        if (mode == Mode.NONE) return
        if (mode == Mode.FLOAT) {
            endFromFloat()
            return
        }
        mode = Mode.NONE
        overlay?.let { activity.overlayRoot.removeView(it) }
        overlay = null
        contentFrame = null
        controls = null
        stopPolling()
        callback?.onCustomViewHidden()
        cleanup()
    }

    /** 页面自行退出全屏（或小窗侧结束） */
    fun onHiddenByPage() {
        if (mode == Mode.NONE) return
        if (mode == Mode.FLOAT) {
            mode = Mode.NONE
            FloatBus.service?.takeWebContent(silent = true)
            cleanup()
            return
        }
        mode = Mode.NONE
        overlay?.let { activity.overlayRoot.removeView(it) }
        overlay = null
        contentFrame = null
        controls = null
        stopPolling()
        cleanup()
    }

    /** 小窗侧主动结束（关闭按钮） */
    fun endFromFloat() {
        if (mode == Mode.FLOAT) {
            mode = Mode.NONE
            FloatBus.service?.takeWebContent(silent = true)
            cleanup()
        }
    }

    private fun cleanup() {
        stopPolling()
        hostTab?.webView?.evaluateJavascript("window.__monoPinned&&window.__monoPinned(false)", null)
        saveRecord()
        customView = null
        callback = null
        hostTab = null
        state = null
    }

    private fun saveRecord() {
        val st = state ?: return
        val tab = hostTab ?: return
        val url = tab.url
        if (url.startsWith("http")) {
            U.runBg { Db.upsertRecord("web", tab.title, url, st.posSec, st.durSec) }
        }
    }

    /** JS 桥兜底：菜单里点「小窗播放当前视频」 */
    fun popFromMenu() {
        val wv = activity.tabs.currentWebView() ?: return
        if (mode != Mode.NONE) return
        wv.evaluateJavascript("(window.__monoPop&&window.__monoPop())", null)
    }

    fun requestRestore() {
        val i = Intent(activity, MainActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        i.putExtra(EXTRA_RESTORE, true)
        activity.startActivity(i)
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        handler.post(pollTask)
    }

    private fun stopPolling() {
        polling = false
        handler.removeCallbacks(pollTask)
    }

    private fun renderState(st: VideoState) {
        if (mode == Mode.INAPP) {
            progressFill?.layoutParams?.width =
                if (st.durSec > 0) (progressTrack?.width ?: 0) * (st.posSec / st.durSec).coerceIn(0.0, 1.0).toInt()
                else 0
            progressFill?.requestLayout()
            timeText?.text = "${U.fmtTime(st.posSec)} / ${U.fmtTime(st.durSec)}"
            playIcon?.setImageResource(if (st.playing) R.drawable.ic_pause else R.drawable.ic_play)
            pipButton?.visibility = if (isVideoFullscreen) View.VISIBLE else View.GONE
        }
    }

    fun hostTabIs(tab: Tab): Boolean = hostTab === tab

    fun onHostTabClosed(tab: Tab) {
        if (hostTab !== tab || mode == Mode.NONE) return
        when (mode) {
            Mode.FLOAT -> endFromFloat()
            else -> exitFromUser()
        }
    }

    companion object {
        const val EXTRA_RESTORE = "restore_theater"
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        fun removeFromParent(v: View) {
            (v.parent as? ViewGroup)?.removeView(v)
        }
    }
}
