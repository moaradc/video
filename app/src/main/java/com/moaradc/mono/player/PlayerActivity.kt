package com.moaradc.mono.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.moaradc.mono.R
import com.moaradc.mono.data.Db
import com.moaradc.mono.util.U

/**
 * 本地 / 外置存储视频全屏播放器。
 * 手势：左右滑动快进快退，左半屏上下滑亮度，右半屏上下滑音量。
 */
class PlayerActivity : AppCompatActivity() {

    private var uri: String? = null
    private var title: String = ""
    private var startPos = 0L

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var indicator: TextView
    private lateinit var titleView: TextView

    private val handler = Handler(Looper.getMainLooper())

    private val speeds = arrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var speedIdx = 2

    private val ticker = object : Runnable {
        override fun run() {
            saveRecord()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        uri = intent.getStringExtra("uri")
        title = intent.getStringExtra("title") ?: ""
        startPos = intent.getLongExtra("pos", 0L)

        if (uri == null) {
            finish()
            return
        }

        buildUi()
        startPlayback()
        handler.postDelayed(ticker, 5000)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newUri = intent.getStringExtra("uri") ?: return
        if (newUri == uri) return
        uri = newUri
        title = intent.getStringExtra("title") ?: ""
        startPos = intent.getLongExtra("pos", 0L)
        titleView.text = title.ifBlank { "未命名" }
        startPlayback()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        playerView = PlayerView(this).apply {
            useController = true
            controllerShowTimeoutMs = 3200
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(playerView)

        // 顶部信息与按钮
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(U.dp(this@PlayerActivity, 10f), U.dp(this@PlayerActivity, 14f), U.dp(this@PlayerActivity, 10f), U.dp(this@PlayerActivity, 10f))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        }
        val back = iconBtn(R.drawable.ic_close) { finish() }
        titleView = TextView(this).apply {
            text = title.ifBlank { "未命名" }
            setTextColor(Color.WHITE)
            textSize = 13f
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            maxLines = 1
        }
        (titleView.layoutParams as? LinearLayout.LayoutParams)?.let { }
        top.addView(back)
        top.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = U.dp(this@PlayerActivity, 12f)
        })
        top.addView(iconBtn(R.drawable.ic_zap) { showSpeedDialog() })
        top.addView(iconBtn(R.drawable.ic_rotate) { toggleOrientation() })
        top.addView(iconBtn(R.drawable.ic_pip) { popToFloat() })
        root.addView(top)

        // 手势指示
        indicator = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setBackgroundColor(0x88000000.toInt())
            setPadding(U.dp(this@PlayerActivity, 18f), U.dp(this@PlayerActivity, 10f), U.dp(this@PlayerActivity, 18f), U.dp(this@PlayerActivity, 10f))
            visibility = View.INVISIBLE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        root.addView(indicator)

        // 手势层：滑动拦截（点按透传给 PlayerView 控制器）
        val gesture = GestureLayer(this)
        root.addView(gesture, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)
    }

    private fun iconBtn(icon: Int, onClick: () -> Unit): ImageView {
        val iv = ImageView(this)
        iv.setImageResource(icon)
        iv.setColorFilter(Color.WHITE)
        val pad = U.dp(this, 9f)
        iv.setPadding(pad, pad, pad, pad)
        iv.setBackgroundResource(R.drawable.bg_float_btn)
        iv.layoutParams = LinearLayout.LayoutParams(U.dp(this, 40f), U.dp(this, 40f)).apply {
            marginStart = U.dp(this@PlayerActivity, 8f)
        }
        iv.setOnClickListener { onClick() }
        return iv
    }

    private fun startPlayback() {
        val u = uri ?: return
        player?.release()
        player = ExoPlayer.Builder(this).build().also { p ->
            playerView.player = p
            p.setMediaItem(MediaItem.fromUri(Uri.parse(u)))
            if (startPos > 0) p.seekTo(startPos)
            p.prepare()
            p.playWhenReady = true
            p.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playerView.keepScreenOn = true
                }
            })
        }
    }

    // ---------------------------------------------------------------- 手势层

    private inner class GestureLayer(ctx: Context) : FrameLayout(ctx) {
        private var downX = 0f
        private var downY = 0f
        private var mode = 0 // 0 无 1 横滑(快进) 2 左滑(亮度) 3 右滑(音量)
        private var acc = 0f
        private var base = 0f

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x; downY = ev.y; acc = 0f
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (mode == 0) {
                        if (kotlin.math.abs(dx) > U.dp(context, 26f) && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                            mode = 1; base = player?.currentPosition?.toFloat() ?: 0f
                        } else if (kotlin.math.abs(dy) > U.dp(context, 26f) && kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                            mode = if (ev.x < width / 2f) 2 else 3
                            base = if (mode == 2) window.attributes.screenBrightness
                            else getVolume()
                            if (base < 0f) base = 0.5f
                        }
                    }
                    if (mode != 0) return true
                }
            }
            return false
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    when (mode) {
                        1 -> {
                            acc = (ev.x - downX) / 4f // px → 秒
                            val target = (base + acc * 1000f).coerceAtLeast(0f)
                            val dur = player?.duration ?: 0L
                            val cap = if (dur > 0) (dur - 500).toFloat() else Float.MAX_VALUE
                            showIndicator(
                                (if (acc > 0) "快进 " else "快退 ") + U.fmtMs(
                                    kotlin.math.abs(acc).toLong() * 1000L
                                ) + "\n" + U.fmtMs(target.toLong()) + " / " + U.fmtMs(
                                    (if (dur > 0) dur else 0L)
                                )
                            )
                        }
                        2 -> {
                            val v = (base - (ev.y - downY) / height * 1.2f).coerceIn(0.02f, 1f)
                            brightness(v)
                        }
                        3 -> {
                            val v = (base + (downY - ev.y) / height * 1.2f).coerceIn(0f, 1f)
                            volume(v)
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (mode == 1) {
                        val target = (base + acc * 1000f).coerceAtLeast(0f)
                        val dur = player?.duration ?: 0L
                        val t = if (dur > 0) target.toLong().coerceAtMost(dur - 300) else target.toLong()
                        player?.seekTo(t.coerceAtLeast(0))
                    }
                    mode = 0
                    hideIndicator()
                }
            }
            return true
        }
    }

    private fun getVolume(): Float {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    private fun volume(v: Float) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max).toInt().coerceIn(0, max), 0)
        showIndicator("音量 ${(v * 100).toInt()}%")
    }

    private fun brightness(v: Float) {
        window.attributes = window.attributes.apply { screenBrightness = v }
        showIndicator("亮度 ${(v * 100).toInt()}%")
    }

    private fun showIndicator(text: String) {
        indicator.text = text
        indicator.visibility = View.VISIBLE
    }

    private fun hideIndicator() {
        indicator.visibility = View.INVISIBLE
    }

    // ---------------------------------------------------------------- 顶部按钮

    private fun showSpeedDialog() {
        val names = speeds.map { if (it == 1.0f) "正常 1x" else String.format("%.2fx", it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("播放速度")
            .setSingleChoiceItems(names, speedIdx) { d, w ->
                speedIdx = w
                player?.setPlaybackSpeed(speeds[w])
                d.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun toggleOrientation() {
        requestedOrientation =
            if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun popToFloat() {
        val u = uri ?: return
        val pos = player?.currentPosition ?: 0L
        FloatPlayerService.startLocal(this, u, title.ifBlank { U.hostOf(u) }, pos)
        finish()
    }

    // ---------------------------------------------------------------- 记录

    private fun saveRecord() {
        val p = player ?: return
        val u = uri ?: return
        val pos = p.currentPosition
        val dur = p.duration
        if (dur > 0 && pos > 3000) {
            U.runBg { Db.upsertRecord("local", title.ifBlank { U.hostOf(u) }, u, pos / 1000.0, dur / 1000.0) }
        }
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        if (Build.VERSION.SDK_INT >= 28) {
            @Suppress("DEPRECATION")
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    override fun onPause() {
        super.onPause()
        player?.let { if (!isFinishing) it.pause() }
        saveRecord()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        saveRecord()
        player?.release()
        player = null
        playerView.player = null
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context, uri: String, title: String, posMs: Long) {
            val i = Intent(ctx, PlayerActivity::class.java)
            i.putExtra("uri", uri)
            i.putExtra("title", title)
            i.putExtra("pos", posMs)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }
    }
}
