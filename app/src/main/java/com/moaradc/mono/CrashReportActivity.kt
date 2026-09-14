package com.moaradc.mono

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** 崩溃诊断页：显示完整堆栈，便于用户复制反馈 */
class CrashReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 72, 48, 48)
        }
        box.addView(TextView(this).apply {
            text = "应用启动遇到错误\n请点击「复制日志」后把内容发给开发者（或截图本页）"
            textSize = 15f
            setTextColor(Color.BLACK)
        })
        box.addView(Button(this).apply {
            text = "复制日志"
            setOnClickListener {
                try {
                    val cb = getSystemService(ClipboardManager::class.java)
                    cb.setPrimaryClip(ClipData.newPlainText("mono_crash", CrashText.value))
                    Toast.makeText(this@CrashReportActivity, "已复制", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this@CrashReportActivity, "复制失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
        box.addView(ScrollView(this).apply {
            addView(TextView(this@CrashReportActivity).apply {
                text = CrashText.value
                setTextIsSelectable(true)
                textSize = 11f
                setTextColor(Color.DKGRAY)
            })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(box)
        window.setBackgroundDrawableResource(android.R.color.white)
    }
}
