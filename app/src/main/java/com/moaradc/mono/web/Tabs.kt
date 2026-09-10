package com.moaradc.mono.web

import android.graphics.Bitmap
import android.webkit.WebView

/** 一个标签页：主页标签 webView 为空，用 url=="mono://home" 标识 */
class Tab(val id: Int) {
    var webView: WebView? = null
    var title: String = "起始页"
    var url: String = HOME_URL
    var isHome: Boolean = true
    var favicon: Bitmap? = null
    var loading: Boolean = false

    companion object {
        const val HOME_URL = "mono://home"
    }
}

/** 标签管理：懒创建 WebView，切换时挂载/卸载 */
class TabManager(private val host: BrowserHost) {

    val tabs = mutableListOf<Tab>()
    var current: Tab? = null
        private set
    private var nextId = 1

    val count: Int get() = tabs.size

    fun newTab(url: String? = null): Tab {
        val tab = Tab(nextId++)
        tabs.add(tab)
        if (url != null) loadIn(tab, url)
        if (tab == current || current == null) switch(tab)
        host.onTabChanged(current)
        return tab
    }

    fun loadIn(tab: Tab, url: String) {
        if (url == Tab.HOME_URL) {
            tab.isHome = true
            tab.url = Tab.HOME_URL
            tab.title = "起始页"
            host.onTabChanged(tab)
        } else {
            tab.isHome = false
            tab.url = url
            host.webViewFor(tab).loadUrl(url)
            host.onTabChanged(tab)
        }
    }

    fun webViewFor(tab: Tab): WebView {
        tab.webView?.let { return it }
        val wv = host.createWebView(tab)
        tab.webView = wv
        return wv
    }

    fun switch(tab: Tab) {
        if (current === tab) {
            host.onTabChanged(tab)
            return
        }
        current?.webView?.let { host.detachWebView(it) }
        current = tab
        if (!tab.isHome) host.attachWebView(host.webViewFor(tab))
        host.onTabChanged(tab)
    }

    fun close(tab: Tab) {
        val idx = tabs.indexOf(tab)
        if (idx < 0) return
        tabs.remove(tab)
        tab.webView?.let { host.destroyWebView(it) }
        tab.webView = null
        if (current === tab) {
            current = tabs.getOrNull((idx).coerceAtMost(tabs.size - 1)) ?: tabs.firstOrNull()
            current?.let { switch(it) } ?: host.onTabChanged(null)
        }
        host.onTabClosed(tab)
    }

    fun allWebViews(): List<WebView> = tabs.mapNotNull { it.webView }

    fun currentWebView(): WebView? = current?.takeIf { !it.isHome }?.webView

    fun closeAll() {
        val copy = tabs.toList()
        tabs.clear()
        copy.forEach { it.webView?.let { w -> host.destroyWebView(w) } }
        current = null
        host.onTabChanged(null)
    }
}

/** 由 MainActivity 实现的宿主回调 */
interface BrowserHost {
    fun createWebView(tab: Tab): WebView
    fun onTabChanged(tab: Tab?)
    fun onTabClosed(tab: Tab)
    fun attachWebView(wv: WebView)
    fun detachWebView(wv: WebView)
    fun destroyWebView(wv: WebView)
}
