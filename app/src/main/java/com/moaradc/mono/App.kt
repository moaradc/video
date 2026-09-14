package com.moaradc.mono

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.appcompat.app.AppCompatDelegate
import com.moaradc.mono.data.Db
import com.moaradc.mono.util.Prefs

class App : Application() {

    override fun onCreate() {
        installCrashHook()
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

    private fun installCrashHook() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                CrashText.value =
                    "THREAD: ${t.name}\n" + android.util.Log.getStackTraceString(e) +
                    "\n-- prev handler: $prev"
            } catch (ignored: Exception) {
            }
            try {
                startActivity(
                    android.content.Intent(this, CrashReportActivity::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                  android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            } catch (ignored: Exception) {
            }
            Thread {
                try { Thread.sleep(1500) } catch (ignored: InterruptedException) {}
                prev?.uncaughtException(t, e)
                android.os.Process.killProcess(android.os.Process.myPid())
            }.start()
        }
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
