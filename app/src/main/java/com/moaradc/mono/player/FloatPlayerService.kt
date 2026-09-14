package com.moaradc.mono.player

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.moaradc.mono.App
import com.moaradc.mono.MainActivity
import com.moaradc.mono.R
import com.moaradc.mono.data.Db
import com.moaradc.mono.util.U
import com.moaradc.mono.web.MonoJs
import com.moaradc.mono.web.VideoState
import com.moaradc.mono.web.WebHandoff

/** 服务与小窗之间的总线（单进程内共享） */
object FloatBus {
    var service: FloatPlayerService? = null
    var pending: WebHandoff? = null
}

/**
 * 小窗播放器宿主服务（mediaPlayback 前台服务）。
 * 两种内容源：
 *  - web：由 VideoTheater 移交的网页全屏视频画面，服务侧轮询 JS 状态驱动进度条；
 *  - local：本地/外置存储视频，使用 Media3 ExoPlayer。
 */
class FloatPlayerService : Service() {

    companion object {
        const val ACTION_CLOSE = "com.moaradc.mono.action.CLOSE"
        const val EXTRA_MODE = "mode"
        const val MODE_WEB = "web"
        const val MODE_LOCAL = "local"
        const val EXTRA_URI = "uri"
        const val EXTRA_TITLE = "title"
        const val EXTRA_POS = "pos"

        fun startWeb(ctx: Context) {
            val i = Intent(ctx, FloatPlayerService::class.java)
            i.putExtra(EXTRA_MODE, MODE_WEB)
            ctx.startForegroundService(i)
        }

        fun startLocal(ctx: Context, uri: String, title: String, posMs: Long) {
            val i = Intent(ctx, FloatPlayerService::class.java)
            i.putExtra(EXTRA_MODE, MODE_LOCAL)
            i.putExtra(EXTRA_URI, uri)
            i.putExtra(EXTRA_TITLE, title)
            i.putExtra(EXTRA_POS, posMs)
            ctx.startForegroundService(i)
        }
    }

    var window: FloatWindow? = null
        private set

    private var exo: ExoPlayer? = null
    private var handoff: WebHandoff? = null
    private val handler = Handler(Looper.getMainLooper())
    private var title = "小窗播放"

    // ---------------------------------------------------------------- 生命周期

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1001, buildNotification())
        if (window == null) window = FloatWindow(this)
        when (intent?.action) {
            ACTION_CLOSE -> {
                closeAll()
                return START_NOT_STICKY
            }
        }
        val mode = intent?.getStringExtra(EXTRA_MODE)
        when (mode) {
            MODE_WEB -> attachWeb()
            MODE_LOCAL -> {
                val uri = intent.getStringExtra(EXTRA_URI) ?: return START_NOT_STICKY
                val t = intent.getStringExtra(EXTRA_TITLE) ?: "本地视频"
                val pos = intent.getLongExtra(EXTRA_POS, 0L)
                startLocal(uri, t, pos)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopLocal()
        window?.dismiss()
        window = null
        if (FloatBus.service === this) FloatBus.service = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务划掉应用时保持小窗（前台服务仍存活）
        super.onTaskRemoved(rootIntent)
    }

    // ---------------------------------------------------------------- 网页模式

    @Synchronized
    private fun attachWeb() {
        val h = FloatBus.pending ?: run {
            // 没有待移交内容：清掉小窗
            if (window?.hasContent != true) closeAll()
            return
        }
        FloatBus.pending = null
        stopLocal()
        handoff = h
        title = h.tab?.title ?: "网页视频"
        window?.listener = object : FloatWindow.Listener {
            override fun onPlayPause() {
                handoff?.tab?.webView?.evaluateJavascript(MonoJs.TOGGLE) { res ->
                    if (res == "\"1\"") window?.setPlayingIcon(true) else window?.setPlayingIcon(false)
                }
            }

            override fun onClose() {
                endWebSession()
            }

            override fun onExpand() {
                val t = handoff?.theater
                if (t != null) t.requestRestore() else endWebSession()
            }
        }
        window?.show()
        window?.setContent(h.view)
        startWebPolling()
    }

    private val webPoll = object : Runnable {
        override fun run() {
            val h = handoff ?: return
            val wv = h.tab?.webView
            if (wv == null) {
                endWebSession()
                return
            }
            if (window != null && window!!.hasContent) {
                wv.evaluateJavascript(MonoJs.STATE) { res ->
                    val st = VideoState.parse(res)
                    if (st != null) {
                        window?.updateProgress((st.posSec * 1000).toLong(), (st.durSec * 1000).toLong(), st.playing)
                        lastWebState = st
                    }
                }
            }
            handler.postDelayed(this, 500)
        }
    }
    private var lastWebState: VideoState? = null
    private var lastSaveTime = 0L

    private fun startWebPolling() {
        handler.removeCallbacks(webPoll)
        handler.removeCallbacks(webSave)
        handler.postDelayed(webPoll, 400)
        handler.postDelayed(webSave, 3000)
    }

    private val webSave = object : Runnable {
        override fun run() {
            val h = handoff
            val st = lastWebState
            if (h != null && st != null) {
                val url = h.tab?.url ?: ""
                val t = h.tab?.title ?: url
                if (url.isNotBlank()) {
                    U.runBg { Db.upsertRecord("web", t, url, st.posSec, st.durSec) }
                }
            }
            handler.postDelayed(this, 3000)
        }
    }

    private fun stopWebPolling() {
        handler.removeCallbacks(webPoll)
        handler.removeCallbacks(webSave)
    }

    /** 影院方（VideoTheater）主动收回画面（点小窗“放大”） */
    fun takeWebContent(silent: Boolean) {
        stopWebPolling()
        handoff = null
        window?.clearContent()
        if (window != null && !silent) {
            // 保留窗口实例但内容为空：直接关闭
        }
        closeIfEmpty()
    }

    /** 由 VideoTheater 调用：结束整个会话（服务侧仅卸内容，不动 callback） */
    fun releaseWeb() {
        stopWebPolling()
        handoff = null
        window?.clearContent()
        closeIfEmpty()
    }

    private fun endWebSession() {
        val h = handoff
        stopWebPolling()
        handoff = null
        window?.clearContent()
        h?.theater?.endFromFloat()
        closeIfEmpty()
    }

    private fun closeIfEmpty() {
        if (exo == null) stopSelf()
    }

    private fun closeAll() {
        stopWebPolling()
        val h = handoff
        handoff = null
        window?.clearContent()
        h?.theater?.endFromFloat()
        stopLocal()
        stopSelf()
    }

    // ---------------------------------------------------------------- 本地模式

    private fun startLocal(uri: String, t: String, posMs: Long) {
        releaseWebOnly()
        handoff = null
        title = t
        val player = ExoPlayer.Builder(this).build()
        exo = player
        player.setWakeMode(androidx.media3.common.C.WAKE_MODE_LOCAL)
        val pv = PlayerView(this)
        pv.useController = false
        pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        pv.player = player
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        if (posMs > 0) player.seekTo(posMs)
        player.playWhenReady = true

        window?.listener = object : FloatWindow.Listener {
            override fun onPlayPause() {
                exo?.let { p -> p.playWhenReady = !p.playWhenReady }
            }

            override fun onClose() {
                saveLocalRecord()
                closeAllLocal()
            }

            override fun onExpand() {
                val p = exo ?: return
                val cur = p.currentPosition
                val dur = p.duration.takeIf { it > 0 } ?: 0L
                saveLocalRecord()
                PlayerActivity.start(this@FloatPlayerService, uri, title, cur)
                closeAllLocal()
            }
        }
        window?.show()
        window?.setContent(pv)
        handler.removeCallbacks(localPoll)
        handler.postDelayed(localPoll, 400)
    }

    private val localPoll = object : Runnable {
        override fun run() {
            val p = exo ?: return
            if (window != null) {
                val dur = p.duration.takeIf { it > 0 } ?: 0L
                window?.updateProgress(p.currentPosition, dur, p.isPlaying)
            }
            handler.postDelayed(this, 500)
        }
    }

    private fun saveLocalRecord() {
        val p = exo ?: return
        val uri = p.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
        val dur = p.duration.takeIf { it > 0 } ?: 0L
        val pos = p.currentPosition
        val t = title
        U.runBg { Db.upsertRecord("local", t, uri, pos / 1000.0, dur / 1000.0) }
    }

    private fun closeAllLocal() {
        stopLocal()
        stopSelf()
    }

    private fun stopLocal() {
        handler.removeCallbacks(localPoll)
        exo?.let { p ->
            p.stop()
            p.release()
        }
        exo = null
        if (window?.hasContent == true) window?.clearContent()
    }

    private fun releaseWebOnly() {
        stopWebPolling()
        handoff = null
    }

    // ---------------------------------------------------------------- 通知

    private fun buildNotification(): Notification {
        val piClose = PendingIntent.getService(
            this, 1,
            Intent(this, FloatPlayerService::class.java).setAction(ACTION_CLOSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val piOpen = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        @Suppress("DEPRECATION")
        return Notification.Builder(this, App.CH_PLAY)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle("Mono · 小窗播放中")
            .setContentText(title)
            .setOngoing(true)
            .setContentIntent(piOpen)
            .addAction(0, "关闭", piClose)
            .build()
    }
}
