package com.moaradc.mono.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.moaradc.mono.R
import com.moaradc.mono.util.Prefs
import com.moaradc.mono.util.U

/**
 * 子页面基类：顶部标题栏 + 内容滚动区 + 深色压暗层。
 */
abstract class BaseActivity : AppCompatActivity() {

    lateinit var root: FrameLayout
    private lateinit var toolbar: LinearLayout
    lateinit var actionView: TextView
    lateinit var contentList: LinearLayout
    private lateinit var scrim: View
    private var appliedRev = -1

    abstract fun pageTitle(): String
    open fun actionLabel(): String? = null
    open fun onAction() {}
    abstract fun rebuild()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        buildLayout()
        appliedRev = Prefs.uiRev
        rebuild()
    }

    override fun onResume() {
        super.onResume()
        if (appliedRev != Prefs.uiRev) {
            recreate()
        } else {
            rebuild()
        }
    }

    @SuppressLint("ResourceType")
    private fun buildLayout() {
        root = FrameLayout(this)
        root.setBackgroundColor(getColor(R.color.bg))
        setContentView(root)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        root.addView(col, FrameLayout.LayoutParams(-1, -1))

        toolbar = LinearLayout(this)
        toolbar.orientation = LinearLayout.HORIZONTAL
        toolbar.gravity = Gravity.CENTER_VERTICAL
        toolbar.setPadding(U.dp(this, 6f), 0, U.dp(this, 12f), 0)
        col.addView(toolbar, LinearLayout.LayoutParams(-1, U.dp(this, 52f)))

        val back = ImageView(this)
        back.setImageResource(R.drawable.ic_back)
        back.setColorFilter(getColor(R.color.ink))
        back.setPadding(U.dp(this, 12f), U.dp(this, 14f), U.dp(this, 12f), U.dp(this, 14f))
        back.setOnClickListener { finish() }
        toolbar.addView(back, LinearLayout.LayoutParams(U.dp(this, 44f), -1))

        val title = TextView(this)
        title.text = pageTitle()
        title.textSize = 17f
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        title.setTextColor(getColor(R.color.ink))
        toolbar.addView(title, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = U.dp(this@BaseActivity, 4f)
        })

        actionView = TextView(this)
        actionLabel()?.let { actionView.text = it; actionView.visibility = View.VISIBLE }
            ?: run { actionView.visibility = View.GONE }
        actionView.textSize = 13f
        actionView.setTextColor(getColor(R.color.ink))
        actionView.setPadding(U.dp(this, 8f), U.dp(this, 6f), U.dp(this, 8f), U.dp(this, 6f))
        actionView.setOnClickListener { onAction() }
        toolbar.addView(actionView, LinearLayout.LayoutParams(-2, -2))

        val line = View(this)
        line.setBackgroundColor(getColor(R.color.line))
        col.addView(line, LinearLayout.LayoutParams(-1, U.dp(this, 1f)))

        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        contentList = LinearLayout(this)
        contentList.orientation = LinearLayout.VERTICAL
        contentList.setPadding(0, U.dp(this, 6f), 0, U.dp(this, 24f))
        scroll.addView(contentList, FrameLayout.LayoutParams(-1, -2))
        col.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        scrim = View(this)
        scrim.setBackgroundColor(Color.BLACK)
        scrim.visibility = View.GONE
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        applyDim()
    }

    fun applyDim() {
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        scrim.visibility = if (dark && Prefs.dimOn) View.VISIBLE else View.GONE
        scrim.alpha = Prefs.dimLevel / 100f
    }

    // ---------------------------------------------------------------- 行构建

    fun addDivider() {
        val v = View(this)
        v.setBackgroundColor(getColor(R.color.line))
        val lp = LinearLayout.LayoutParams(-1, U.dp(this, 1f))
        lp.setMargins(U.dp(this, 20f), 0, U.dp(this, 20f), 0)
        contentList.addView(v, lp)
    }

    fun addHeader(text: String) {
        val t = TextView(this)
        t.text = text
        t.textSize = 12f
        t.setTextColor(getColor(R.color.sub))
        t.setTypeface(null, android.graphics.Typeface.BOLD)
        t.letterSpacing = 0.08f
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.setMargins(U.dp(this, 20f), U.dp(this, 16f), U.dp(this, 20f), U.dp(this, 6f))
        contentList.addView(t, lp)
    }

    fun addRow(
        title: String,
        sub: String? = null,
        icon: Int? = null,
        endText: String? = null,
        click: (() -> Unit)? = null,
        longClick: (() -> Boolean)? = null
    ): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val pad = U.dp(this, 20f)
        row.setPadding(pad, U.dp(this, 14f), pad, U.dp(this, 14f))
        row.isClickable = true
        row.background = ripple()

        if (icon != null) {
            val iv = ImageView(this)
            iv.setImageResource(icon)
            iv.setColorFilter(getColor(R.color.ink))
            row.addView(iv, LinearLayout.LayoutParams(U.dp(this, 22f), U.dp(this, 22f)).apply {
                marginEnd = U.dp(this@BaseActivity, 14f)
            })
        }

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))

        val t = TextView(this)
        t.text = title
        t.textSize = 15f
        t.setTextColor(getColor(R.color.ink))
        t.maxLines = 2
        t.ellipsize = android.text.TextUtils.TruncateAt.END
        col.addView(t)

        if (sub != null) {
            val s = TextView(this)
            s.text = sub
            s.textSize = 12f
            s.setTextColor(getColor(R.color.sub))
            s.maxLines = 1
            s.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            (s.layoutParams as? LinearLayout.LayoutParams)?.setMargins(0, U.dp(this, 2f), 0, 0)
            col.addView(s)
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = U.dp(this, 2f)
            s.layoutParams = lp
        }

        if (endText != null) {
            val e = TextView(this)
            e.text = endText
            e.textSize = 12f
            e.setTextColor(getColor(R.color.sub))
            row.addView(e, LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = U.dp(this@BaseActivity, 8f)
            })
        } else {
            val chev = ImageView(this)
            chev.setImageResource(R.drawable.ic_chevron_right)
            chev.setColorFilter(getColor(R.color.sub))
            row.addView(chev, LinearLayout.LayoutParams(U.dp(this, 16f), U.dp(this, 16f)).apply {
                marginStart = U.dp(this@BaseActivity, 8f)
            })
        }

        click?.let { c -> row.setOnClickListener { c() } }
        longClick?.let { l -> row.setOnLongClickListener { l() } }
        contentList.addView(row, LinearLayout.LayoutParams(-1, -2))
        return row
    }

    fun addSwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val pad = U.dp(this, 20f)
        row.setPadding(pad, U.dp(this, 14f), pad, U.dp(this, 14f))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        val t = TextView(this)
        t.text = title
        t.textSize = 15f
        t.setTextColor(getColor(R.color.ink))
        col.addView(t)
        if (sub != null) {
            val s = TextView(this)
            s.text = sub
            s.textSize = 12f
            s.setTextColor(getColor(R.color.sub))
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = U.dp(this, 2f)
            s.layoutParams = lp
            col.addView(s)
        }
        val sw = SwitchCompat(this)
        sw.isChecked = checked
        sw.thumbDrawable?.setTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.accent)))
        sw.trackDrawable?.setTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.line)))
        sw.setOnCheckedChangeListener { _, v -> onChange(v) }
        row.addView(sw, LinearLayout.LayoutParams(-2, -2))
        contentList.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    fun emptyHint(text: String = "这里空空如也") {
        val t = TextView(this)
        t.text = text
        t.textSize = 13f
        t.gravity = Gravity.CENTER
        t.setTextColor(getColor(R.color.sub))
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = U.dp(this, 80f)
        contentList.addView(t, lp)
    }

    private fun ripple(): android.graphics.drawable.Drawable {
        val d = android.graphics.drawable.GradientDrawable()
        d.setColor(getColor(R.color.bg))
        val ripple = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(getColor(R.color.line)),
            d, null
        )
        return ripple
    }

    protected fun timeAgoLabel(t: Long): String {
        val diff = System.currentTimeMillis() - t
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3600_000 -> "${diff / 60_000} 分钟前"
            diff < 86400_000 -> "${diff / 3600_000} 小时前"
            diff < 172800_000 -> "昨天"
            diff < 604800_000 -> "${diff / 86400_000} 天前"
            else -> java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date(t))
        }
    }
}
