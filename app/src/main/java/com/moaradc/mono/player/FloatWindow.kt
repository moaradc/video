package com.moaradc.mono.player

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.moaradc.mono.R
import com.moaradc.mono.util.U
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 全局小窗播放器窗口。
 *
 * 拖拽物理：
 *  - 手指拖动 1:1 跟随（VelocityTracker 实时采样真实速度）；
 *  - 松手后按释放瞬间的真实速度继续滑行，指数摩擦「渐停」；
 *  - 碰到屏幕边缘时按冲击速度自适应的恢复系数反弹，并允许小幅
 *    「陷进」边缘再弹出（类似皮球触壁），停止后自动吸附回边界。
 */
class FloatWindow(private val ctx: Context) {

    interface Listener {
        fun onPlayPause()
        fun onClose()
        fun onExpand()
    }

    var listener: Listener? = null
    var webMode = true

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    // —— 视图 ——
    val root = FrameLayout(ctx)
    private val contentFrame = FrameLayout(ctx)
    private val gestureLayer = View(ctx)
    private val controls = FrameLayout(ctx)
    private val btnColumn = LinearLayout(ctx)
    private val centerPlay = ImageView(ctx)
    private val progressTrack = FrameLayout(ctx)
    private val progressFill = View(ctx)
    private val timeText = TextView(ctx)
    private val resizeHandle = View(ctx)
    private val bubble = FrameLayout(ctx)
    private val bubbleGlyph = ImageView(ctx)

    // —— 窗口参数 ——
    private val params = WindowManager.LayoutParams(
        0, 0,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    )

    private var added = false
    private var bubbleMode = false
    private var smallSize = true
    private var durMsCache = 0L
    private var posMsCache = 0L

    // —— 物理 ——
    private val choreographer = Choreographer.getInstance()
    private var vx = 0f
    private var vy = 0f
    private var animating = false
    private var lastFrameNanos = 0L
    private var tracker: android.view.VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var downTime = 0L
    private var moved = false
    private var lastTap = 0L

    private val maxOvershoot = U.dp(ctx, 18f)
    private val stopSpeed = U.dp(ctx, 26f)

    init {
        root.setBackgroundResource(R.drawable.bg_float_window)
        root.clipToOutline = true

        contentFrame.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        root.addView(contentFrame)

        gestureLayer.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        gestureLayer.setOnTouchListener { _, ev -> onGesture(ev) }
        root.addView(gestureLayer)

        buildControls()
        controls.visibility = View.INVISIBLE
        root.addView(controls, FrameLayout.LayoutParams(MATCH, MATCH))

        buildBubble()
        bubble.visibility = View.GONE
        root.addView(
            bubble,
            FrameLayout.LayoutParams(U.dp(ctx, 52f), U.dp(ctx, 52f), Gravity.CENTER)
        )

        params.gravity = Gravity.TOP or Gravity.START
    }

    // ---------------------------------------------------------------- 控件

    @SuppressLint("ClickableViewAccessibility")
    private fun buildControls() {
        btnColumn.orientation = LinearLayout.VERTICAL
        btnColumn.layoutParams = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END)
        btnColumn.setPadding(U.dp(ctx, 6f), U.dp(ctx, 6f), U.dp(ctx, 6f), U.dp(ctx, 6f))
        btnColumn.addView(iconBtn(R.drawable.ic_close) { listener?.onClose() })
        btnColumn.addView(iconBtn(R.drawable.ic_shrink) { collapseToBubble() })
        btnColumn.addView(iconBtn(R.drawable.ic_expand) { listener?.onExpand() })
        controls.addView(btnColumn)

        // 居中播放/暂停
        centerPlay.setImageResource(R.drawable.ic_pause)
        centerPlay.setColorFilter(Color.WHITE)
        centerPlay.setBackgroundResource(R.drawable.bg_float_play)
        centerPlay.setPadding(U.dp(ctx, 12f), U.dp(ctx, 12f), U.dp(ctx, 12f), U.dp(ctx, 12f))
        centerPlay.layoutParams = FrameLayout.LayoutParams(U.dp(ctx, 54f), U.dp(ctx, 54f), Gravity.CENTER)
        centerPlay.setOnClickListener { listener?.onPlayPause() }
        controls.addView(centerPlay)

        // 底部：时间 + 进度线
        val bottom = LinearLayout(ctx)
        bottom.orientation = LinearLayout.VERTICAL
        bottom.layoutParams = FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM)
        bottom.setPadding(U.dp(ctx, 12f), 0, U.dp(ctx, 12f), U.dp(ctx, 6f))

        timeText.setTextColor(Color.WHITE)
        timeText.textSize = 10f
        bottom.addView(
            timeText,
            LinearLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL }
        )

        progressTrack.setBackgroundColor(0x40FFFFFF)
        progressTrack.layoutParams = LinearLayout.LayoutParams(MATCH, U.dp(ctx, 2f)).apply {
            topMargin = U.dp(ctx, 5f)
        }
        progressFill.setBackgroundColor(Color.WHITE)
        progressFill.layoutParams = FrameLayout.LayoutParams(0, MATCH)
        progressTrack.addView(progressFill)
        bottom.addView(progressTrack)
        controls.addView(bottom)

        // 右下角拖拽缩放
        resizeHandle.setBackgroundResource(R.drawable.bg_resize)
        resizeHandle.layoutParams = FrameLayout.LayoutParams(U.dp(ctx, 26f), U.dp(ctx, 26f), Gravity.BOTTOM or Gravity.END)
        resizeHandle.setOnTouchListener { _, ev -> onResize(ev) }
        controls.addView(resizeHandle)
    }

    private fun iconBtn(icon: Int, onClick: () -> Unit): ImageView {
        val iv = ImageView(ctx)
        iv.setImageResource(icon)
        iv.setColorFilter(Color.WHITE)
        iv.setBackgroundResource(R.drawable.bg_float_btn)
        iv.layoutParams = LinearLayout.LayoutParams(U.dp(ctx, 34f), U.dp(ctx, 34f)).apply {
            topMargin = U.dp(ctx, 6f)
        }
        iv.setOnClickListener { onClick() }
        return iv
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildBubble() {
        bubble.setBackgroundResource(R.drawable.bg_bubble)
        bubbleGlyph.setImageResource(R.drawable.ic_play)
        bubbleGlyph.setColorFilter(Color.WHITE)
        bubble.addView(
            bubbleGlyph,
            FrameLayout.LayoutParams(U.dp(ctx, 20f), U.dp(ctx, 20f), Gravity.CENTER)
        )
        bubble.layoutParams = FrameLayout.LayoutParams(U.dp(ctx, 52f), U.dp(ctx, 52f))
        bubble.setOnTouchListener { _, ev -> onGesture(ev) }
    }

    // ---------------------------------------------------------------- 生命周期

    fun show() {
        if (added) return
        val b = bounds()
        val w = cardWidth()
        params.width = w
        params.height = cardHeight(w)
        params.x = max(0, b.right - w - U.dp(ctx, 10f))
        params.y = max(0, (b.bottom * 0.58f).toInt() - params.height / 2)
        wm.addView(root, params)
        added = true
    }

    fun dismiss() {
        stopAnim()
        handler.removeCallbacksAndMessages(null)
        if (added) {
            try { wm.removeView(root) } catch (e: Exception) {}
            added = false
        }
    }

    fun setContent(v: View) {
        contentFrame.removeAllViews()
        (v.parent as? ViewGroup)?.removeView(v)
        contentFrame.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    fun clearContent() {
        contentFrame.removeAllViews()
    }

    val hasContent: Boolean get() = contentFrame.childCount > 0

    // ---------------------------------------------------------------- 播放状态

    fun updateProgress(posMs: Long, durMs: Long, playing: Boolean) {
        posMsCache = posMs
        durMsCache = durMs
        playingCache = playing
        centerPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        if (durMs > 0) {
            val frac = (posMs.toFloat() / durMs).coerceIn(0f, 1f)
            progressFill.post {
                val lp = progressFill.layoutParams
                lp.width = (progressTrack.width * frac).toInt()
                progressFill.layoutParams = lp
            }
            timeText.text = "${U.fmtMs(posMs)} / ${U.fmtMs(durMs)}"
        }
    }

    private var playingCache = false

    fun setPlayingIcon(playing: Boolean) {
        playingCache = playing
        centerPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun toggleControls() {
        if (bubbleMode) return
        controls.visibility = if (controls.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
        if (controls.visibility == View.VISIBLE) {
            updateProgress(posMsCache, durMsCache, playingCache)
            handler.removeCallbacks(hideControls)
            handler.postDelayed(hideControls, 3000)
        }
    }

    private val hideControls = Runnable { controls.visibility = View.INVISIBLE }

    // ---------------------------------------------------------------- 手势

    @SuppressLint("ClickableViewAccessibility")
    private fun onGesture(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopAnim()
                tracker?.recycle()
                tracker = android.view.VelocityTracker.obtain()
                tracker?.addMovement(ev)
                downX = ev.rawX
                downY = ev.rawY
                startX = params.x
                startY = params.y
                downTime = SystemClock.uptimeMillis()
                moved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(ev)
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                if (abs(dx) > U.dp(ctx, 8f) || abs(dy) > U.dp(ctx, 8f)) moved = true
                if (moved) {
                    params.x = (startX + dx).toInt()
                    params.y = (startY + dy).toInt()
                    safeUpdate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.addMovement(ev)
                tracker?.computeCurrentVelocity(1000, 16000f)
                val v0 = tracker?.xVelocity ?: 0f
                val v1 = tracker?.yVelocity ?: 0f
                tracker?.recycle()
                tracker = null
                val dur = SystemClock.uptimeMillis() - downTime
                if (!moved && dur < 300 && ev.actionMasked == MotionEvent.ACTION_UP) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastTap < 320) {
                        lastTap = 0
                        toggleSize()
                    } else {
                        lastTap = now
                        if (bubbleMode) uncollapse() else toggleControls()
                    }
                } else if (ev.actionMasked == MotionEvent.ACTION_UP) {
                    startFling(v0, v1)
                }
                return true
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun onResize(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopAnim()
                downX = ev.rawX
                startW = params.width
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val b = bounds()
                val w = (startW + (ev.rawX - downX)).toInt()
                    .coerceIn(U.dp(ctx, 170f), (b.right * 0.95f).toInt())
                params.width = w
                params.height = cardHeight(w)
                params.x = params.x.coerceIn(b.left, b.right - w)
                params.y = params.y.coerceIn(b.top, b.bottom - params.height)
                safeUpdate()
                return true
            }
        }
        return true
    }

    private var startW = 0

    private fun toggleSize() {
        if (bubbleMode) return
        smallSize = !smallSize
        val b = bounds()
        val w = cardWidth()
        params.width = w
        params.height = cardHeight(w)
        params.x = params.x.coerceIn(b.left, b.right - w)
        params.y = params.y.coerceIn(b.top, b.bottom - params.height)
        safeUpdate()
    }

    private fun cardWidth(): Int {
        val b = bounds()
        val small = (b.right * 0.44f).toInt()
        val large = (b.right * 0.62f).toInt()
        val w = if (smallSize) small else large
        return w.coerceIn(U.dp(ctx, 190f), (b.right * 0.95f).toInt())
    }

    private fun cardHeight(w: Int): Int = (w * 9f / 16f).toInt()

    // ---------------------------------------------------------------- 气泡模式

    private var savedBeforeBubble = Triple(0, 0, 0)

    private fun collapseToBubble() {
        if (bubbleMode) return
        bubbleMode = true
        savedBeforeBubble = Triple(params.x, params.y, params.width)
        contentFrame.visibility = View.INVISIBLE
        controls.visibility = View.GONE
        bubble.visibility = View.VISIBLE
        root.setBackgroundResource(R.drawable.bg_bubble)
        val b = bounds()
        params.width = U.dp(ctx, 52f)
        params.height = U.dp(ctx, 52f)
        params.x = params.x.coerceIn(b.left, b.right - params.width)
        params.y = params.y.coerceIn(b.top, b.bottom - params.height)
        safeUpdate()
    }

    private fun uncollapse() {
        if (!bubbleMode) return
        bubbleMode = false
        root.setBackgroundResource(R.drawable.bg_float_window)
        bubble.visibility = View.GONE
        contentFrame.visibility = View.VISIBLE
        val b = bounds()
        params.width = cardWidth()
        params.height = cardHeight(params.width)
        params.x = params.x.coerceIn(b.left, b.right - params.width)
        params.y = params.y.coerceIn(b.top, b.bottom - params.height)
        safeUpdate()
        toggleControls()
    }

    // ---------------------------------------------------------------- 物理

    private fun startFling(vx0: Float, vy0: Float) {
        vx = vx0
        vy = vy0
        if (abs(vx) < stopSpeed && abs(vy) < stopSpeed) {
            settle()
            return
        }
        animating = true
        lastFrameNanos = System.nanoTime()
        choreographer.postFrameCallback(frameCb)
    }

    private fun stopAnim() {
        animating = false
        choreographer.removeFrameCallback(frameCb)
    }

    private fun repostFrame() {
        choreographer.postFrameCallback(frameCb)
    }

    private val frameCb: Choreographer.FrameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        if (!animating) return@FrameCallback
        val dt = ((frameTimeNanos - lastFrameNanos) / 1_000_000_000.0).toFloat()
            .coerceIn(0.001f, 0.05f)
        lastFrameNanos = frameTimeNanos

        val damp = exp(-FRICTION * dt)
        vx *= damp
        vy *= damp

        var nx = params.x + vx * dt
        var ny = params.y + vy * dt
        val b = bounds()

        if (nx < b.left) {
            nx = b.left - min(b.left - nx, maxOvershoot.toFloat())
            vx = bounceV(vx)
        } else if (nx > b.right - curW) {
            nx = b.right - curW + min(nx - (b.right - curW), maxOvershoot.toFloat())
            vx = bounceV(vx)
        }
        if (ny < b.top) {
            ny = b.top - min(b.top - ny, maxOvershoot.toFloat())
            vy = bounceV(vy)
        } else if (ny > b.bottom - curH) {
            ny = b.bottom - curH + min(ny - (b.bottom - curH), maxOvershoot.toFloat())
            vy = bounceV(vy)
        }

        params.x = nx.toInt()
        params.y = ny.toInt()
        safeUpdate()

        val stopped = abs(vx) < stopSpeed && abs(vy) < stopSpeed
        val inside = nx >= b.left && nx <= b.right - curW && ny >= b.top && ny <= b.bottom - curH
        if (stopped && inside) {
            animating = false
            settle()
        } else {
            repostFrame()
        }
    }

    /** 自适应恢复系数：冲击越快，反弹越明显（0.22 ~ 0.5） */
    private fun bounceV(v: Float): Float =
        -v * (0.22f + 0.28f * min(abs(v) / 3500f, 1f))

    /** 结束后吸附回安全区域（把边缘的「陷入量」弹回） */
    private fun settle() {
        val b = bounds()
        val tx = (params.x).coerceIn(b.left, b.right - curW)
        val ty = (params.y).coerceIn(b.top, b.bottom - curH)
        if (tx == params.x && ty == params.y) return
        val anim = ValueAnimator.ofFloat(0f, 1f)
        val sx = params.x.toFloat()
        val sy = params.y.toFloat()
        anim.duration = 160
        anim.addUpdateListener { a ->
            val f = a.animatedFraction
            params.x = (sx + (tx - sx) * f).toInt()
            params.y = (sy + (ty - sy) * f).toInt()
            safeUpdate()
        }
        anim.start()
    }

    private val curW: Int get() = if (bubbleMode) U.dp(ctx, 52f) else params.width
    private val curH: Int get() = if (bubbleMode) U.dp(ctx, 52f) else params.height

    private fun bounds(): android.graphics.Rect {
        val pt = android.graphics.Point()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealSize(pt)
        return android.graphics.Rect(0, 0, pt.x, pt.y)
    }

    private fun safeUpdate() {
        try {
            wm.updateViewLayout(root, params)
        } catch (e: Exception) {
        }
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val FRICTION = 2.4f
    }
}
