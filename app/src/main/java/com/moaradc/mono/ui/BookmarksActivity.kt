package com.moaradc.mono.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.moaradc.mono.MainActivity
import com.moaradc.mono.R
import com.moaradc.mono.data.Bookmark
import com.moaradc.mono.data.Db
import com.moaradc.mono.util.U

/** 书签（收藏）管理 */
class BookmarksActivity : BaseActivity() {

    override fun pageTitle() = "书签管理"

    override fun actionLabel() = "添加"

    override fun onAction() {
        addDialog()
    }

    override fun rebuild() {
        U.runBg {
            val list = Db.bookmarks()
            U.runMain { render(list) }
        }
    }

    private fun render(list: List<Bookmark>) {
        contentList.removeAllViews()
        if (list.isEmpty()) {
            emptyHint("暂无书签，点右上角「添加」")
            return
        }
        list.forEachIndexed { i, b ->
            addRow(
                b.title,
                U.hostOf(b.url),
                null,
                click = { open(b) },
                longClick = {
                    AlertDialog.Builder(this)
                        .setTitle(b.title)
                        .setItems(arrayOf("编辑", "删除", "在新页面打开")) { _, which ->
                            when (which) {
                                0 -> editDialog(b)
                                1 -> {
                                    U.runBg { Db.deleteBookmark(b.id) }
                                    U.runMain { rebuild() }
                                }
                                2 -> open(b)
                            }
                        }
                        .show()
                    true
                }
            )
            // 有 favicon 用 favicon，否则行首加字标
            if (i != list.size - 1) addDivider()
        }
    }

    private fun open(b: Bookmark) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse(b.url))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun addDialog() {
        val pad = U.dp(this, 20f)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val eTitle = EditText(this).apply { hint = "名称" }
        val eUrl = EditText(this).apply { hint = "网址" }
        col.addView(eTitle)
        col.addView(eUrl)
        AlertDialog.Builder(this)
            .setTitle("添加书签")
            .setView(col)
            .setPositiveButton("添加") { _, _ ->
                val t = eTitle.text.toString().trim()
                val u = eUrl.text.toString().trim()
                if (u.isNotEmpty()) {
                    U.runBg {
                        Db.addBookmark(t.ifBlank { U.hostOf(u) }, U.normalizeUrl(u), null)
                        U.runMain { rebuild() }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun editDialog(b: Bookmark) {
        val pad = U.dp(this, 20f)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val eTitle = EditText(this).apply { hint = "名称"; setText(b.title) }
        val eUrl = EditText(this).apply { hint = "网址"; setText(b.url) }
        col.addView(eTitle)
        col.addView(eUrl)
        AlertDialog.Builder(this)
            .setTitle("编辑书签")
            .setView(col)
            .setPositiveButton("保存") { _, _ ->
                val t = eTitle.text.toString().trim()
                val u = eUrl.text.toString().trim()
                if (u.isNotEmpty()) {
                    U.runBg {
                        Db.updateBookmark(b.id, t.ifBlank { U.hostOf(u) }, U.normalizeUrl(u))
                        U.runMain { rebuild() }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun decodeIcon(bytes: ByteArray?): Bitmap? {
        if (bytes == null) return null
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
}
