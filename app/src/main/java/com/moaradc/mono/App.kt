package com.moaradc.mono

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.appcompat.app.AppCompatDelegate
import com.moaradc.mono.data.Db
import com.moaradc.mono.util.Prefs

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Db.init(this)
        applyTheme()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PLAY, "小窗播放", NotificationManager.IMPORTANCE_LOW).apply {
                description = "小窗播放器常驻通知"
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val CH_PLAY = "playback"

        fun applyTheme() {
            AppCompatDelegate.setDefaultNightMode(
                when (Prefs.theme) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }
}
