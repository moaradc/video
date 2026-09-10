package com.moaradc.mono.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 轻量偏好设置：仅 SharedPreferences，无额外依赖。
 */
object Prefs {
    private const val FILE = "mono_prefs"
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    /** 主题：0 跟随系统 / 1 浅色 / 2 深色 */
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) = sp.edit().putInt("theme", v).apply()

    /** 深色模式下额外压暗界面（不影响小窗播放器） */
    var dimOn: Boolean
        get() = sp.getBoolean("dim_on", true)
        set(v) = sp.edit().putBoolean("dim_on", v).apply()

    /** 压暗程度 0~60% */
    var dimLevel: Int
        get() = sp.getInt("dim_level", 35)
        set(v) = sp.edit().putInt("dim_level", v).apply()

    /** 搜索引擎：0 必应 / 1 百度 / 2 谷歌 / 3 DuckDuckGo */
    var engine: Int
        get() = sp.getInt("engine", 0)
        set(v) = sp.edit().putInt("engine", v).apply()

    /** 广告拦截 */
    var adblock: Boolean
        get() = sp.getBoolean("adblock", true)
        set(v) = sp.edit().putBoolean("adblock", v).apply()

    /** 已拦截请求计数 */
    var adblockCount: Long
        get() = sp.getLong("adblock_count", 0L)
        set(v) = sp.edit().putLong("adblock_count", v).apply()

    /** 强制桌面版 UA */
    var desktopUa: Boolean
        get() = sp.getBoolean("desktop_ua", false)
        set(v) = sp.edit().putBoolean("desktop_ua", v).apply()

    /** 记录网页历史 */
    var saveHistory: Boolean
        get() = sp.getBoolean("save_history", true)
        set(v) = sp.edit().putBoolean("save_history", v).apply()

    /** 网页视频全屏时自动进入小窗 */
    var autoFloat: Boolean
        get() = sp.getBoolean("auto_float", true)
        set(v) = sp.edit().putBoolean("auto_float", v).apply()

    /** 续播 */
    var resume: Boolean
        get() = sp.getBoolean("resume", true)
        set(v) = sp.edit().putBoolean("resume", v).apply()

    /** UI 版本号：外观相关设置变化时自增，用于触发界面重建 */
    var uiRev: Int
        get() = sp.getInt("ui_rev", 0)
        set(v) = sp.edit().putInt("ui_rev", v).apply()

    fun bumpUi() {
        uiRev = uiRev + 1
    }
}
