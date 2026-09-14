package com.moaradc.mono.ui

import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.moaradc.mono.App
import com.moaradc.mono.R
import com.moaradc.mono.data.Db
import com.moaradc.mono.util.Prefs
import com.moaradc.mono.util.U

/** 设置：外观 / 浏览 / 播放 / 数据 / 关于 */
class SettingsActivity : BaseActivity() {

    override fun pageTitle() = "设置"

    private val themeNames = arrayOf("跟随系统", "浅色", "深色")
    private val engineNames = arrayOf("必应", "百度", "谷歌", "DuckDuckGo")

    override fun rebuild() {
        contentList.removeAllViews()
        buildAppearance()
        buildBrowse()
        buildPlayback()
        buildData()
        buildAbout()
    }

    // ---------------------------------------------------------------- 外观

    private fun buildAppearance() {
        addHeader("外观")
        addRow(
            "主题", themeNames[Prefs.theme], R.drawable.ic_moon,
            endText = themeNames[Prefs.theme],
            click = {

            AlertDialog.Builder(this)
                .setTitle("主题")
                .setSingleChoiceItems(themeNames, Prefs.theme) { d, which ->
                    Prefs.theme = which
                    Prefs.bumpUi()
                    App.applyTheme()
                    recreate()
                    d.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
        })
        addSwitchRow("深色时压暗界面", "降低应用界面亮度，小窗播放器不受影响", Prefs.dimOn) {
            Prefs.dimOn = it
            Prefs.bumpUi()
            applyDim()
        }
        dimRow()
    }

    private fun dimRow() {
        val pad = U.dp(this, 20f)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        row.setPadding(pad, U.dp(this, 8f), pad, U.dp(this, 8f))
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val t = TextView(this).apply {
            text = "压暗程度"
            textSize = 15f
            setTextColor(getColor(R.color.ink))
        }
        line.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        val v = TextView(this).apply {
            text = "${Prefs.dimLevel}%"
            textSize = 12f
            setTextColor(getColor(R.color.sub))
        }
        line.addView(v, LinearLayout.LayoutParams(-2, -2))
        row.addView(line)
        val sb = SeekBar(this)
        sb.max = 60
        sb.progress = Prefs.dimLevel
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                Prefs.dimLevel = p
                v.text = "$p%"
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                Prefs.bumpUi()
                applyDim()
            }
        })
        row.addView(sb, LinearLayout.LayoutParams(-1, -2))
        contentList.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    // ---------------------------------------------------------------- 浏览

    private fun buildBrowse() {
        addHeader("浏览")
        addRow("搜索引擎", engineNames[Prefs.engine], R.drawable.ic_search, endText = engineNames[Prefs.engine], click = {
            AlertDialog.Builder(this)
                .setTitle("搜索引擎")
                .setSingleChoiceItems(engineNames, Prefs.engine) { d, which ->
                    Prefs.engine = which
                    d.dismiss()
                    rebuild()
                }
                .setNegativeButton("取消", null)
                .show()
        })
        addSwitchRow("拦截广告与追踪", "已拦截 ${Prefs.adblockCount} 次请求", Prefs.adblock) {
            Prefs.adblock = it
        }
        addSwitchRow("桌面版网站", "请求桌面版 User-Agent", Prefs.desktopUa) {
            Prefs.desktopUa = it
        }
        addSwitchRow("记录网页历史", null, Prefs.saveHistory) {
            Prefs.saveHistory = it
        }
    }

    // ---------------------------------------------------------------- 播放

    private fun buildPlayback() {
        addHeader("播放")
        addSwitchRow("网页全屏自动转小窗", "视频进入全屏时自动弹出小窗播放器", Prefs.autoFloat) {
            Prefs.autoFloat = it
        }
        addSwitchRow("自动续播", "按播放记录从上次位置继续", Prefs.resume) {
            Prefs.resume = it
        }
    }

    // ---------------------------------------------------------------- 数据

    private fun buildData() {
        addHeader("数据")
        addRow("清除网页历史", "${Db.history().size} 条记录", R.drawable.ic_clock, click = {
            confirmClear("清除全部网页历史？") {
                U.runBg { Db.clearHistory() }
                rebuild()
            }
        })
        addRow("清空稍后再看", "${Db.watchLater().size} 条", R.drawable.ic_watch_later, click = {
            confirmClear("清空稍后再看？") {
                U.runBg { Db.clearWatchLater() }
                rebuild()
            }
        })
        addRow("清空播放记录", "${Db.records().size} 条", R.drawable.ic_play_circle, click = {
            confirmClear("清空播放记录？") {
                U.runBg { Db.clearRecords() }
                rebuild()
            }
        })
        addRow("清除网页缓存与数据", "缓存 / Cookie / 表单数据", R.drawable.ic_shield, click = {
            confirmClear("清除全部浏览数据？") {
                try {
                    android.webkit.WebStorage.getInstance().deleteAllData()
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    android.webkit.CookieManager.getInstance().flush()
                } catch (e: Exception) {
                }
                U.toast(this, "已清除")
            }
        })
    }

    private fun confirmClear(msg: String, ok: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton("确定") { _, _ -> ok() }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------------------------------------------------------------- 关于

    private fun buildAbout() {
        addHeader("关于")
        addRow("版本", "1.0.0 (arm64 / arm32)", R.drawable.ic_info)
        addRow("项目主页", "github.com/moaradc/video", R.drawable.ic_globe, click = {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/moaradc/video")))
            } catch (e: Exception) {
            }
        })
        addRow(
            "技术参考", "Media3 ExoPlayer · Android WebView\n悬浮物理特性参考 EasyFloat/Leos Void", R.drawable.ic_bookmark
        )
        val foot = TextView(this).apply {
            text = "MONO · 黑白之间 · 播放与浏览"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.sub))
        }
        contentList.addView(
            foot,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = U.dp(this@SettingsActivity, 24f)
            }
        )
    }
}
