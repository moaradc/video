package com.moaradc.mono.web

import android.graphics.Bitmap
import android.os.Message
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.moaradc.mono.MainActivity
import com.moaradc.mono.util.Prefs
import java.io.ByteArrayInputStream
import java.util.HashSet

/** WebView 工厂：统一的设置与回调接线 */
object WebFactory {

    const val UA_DESKTOP =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    fun applyUaAll(views: List<WebView>) {
        views.forEach { applyUa(it) }
    }

    private fun applyUa(wv: WebView) {
        try {
            wv.settings.userAgentString =
                if (Prefs.desktopUa) UA_DESKTOP else null
        } catch (e: Exception) {
        }
    }

    fun create(activity: MainActivity, tab: Tab): WebView {
        val wv = WebView(activity)
        wv.layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.setSupportZoom(false)
        s.mediaPlaybackRequiresUserGesture = true
        s.setSupportMultipleWindows(true)
        s.javaScriptCanOpenWindowsAutomatically = true
        s.allowFileAccess = false
        s.allowContentAccess = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.setGeolocationEnabled(false)
        applyUa(wv)

        wv.webViewClient = MonoWebClient(activity, tab)
        wv.webChromeClient = MonoChromeClient(activity, tab)
        wv.setDownloadListener { url, _, contentDisposition, mime, _ ->
            activity.onDownloadRequested(url, contentDisposition, mime)
        }
        wv.setOnLongClickListener {
            activity.onWebViewLongClick(wv)
            true
        }
        return wv
    }

    fun applyAll(activity: MainActivity) {
        activity.tabs.allWebViews().forEach { applyUa(it); it.reload() }
    }
}

/** 广告/追踪域名拦截表（内置精简版） */
object AdHosts {
    private val hosts: HashSet<String> = HashSet()

    init {
        val list = listOf(
            "doubleclick.net", "googlesyndication.com", "googletagservices.com", "googleadservices.com",
            "adservice.google.com", "pagead2.googlesyndication.com", "admob.com", "ads.google.com",
            "adnxs.com", "adsrvr.org", "criteo.com", "criteo.net", "pubmatic.com", "rubiconproject.com",
            "openx.net", "smartadserver.com", "taboola.com", "outbrain.com", "moatads.com", "adsafeprotected.com",
            "scorecardresearch.com", "chartbeat.com", "chartbeat.net", "quantserve.com", "quantcount.com",
            "casalemedia.com", "bidswitch.net", "adform.net", "adroll.com", "amplitude.com",
            "mixpanel.com", "segment.io", "segment.com", "hotjar.com", "hotjar.io", "mouseflow.com",
            "fullstory.com", "clarity.ms", "yandex-metrica.com", "mc.yandex.ru", "top-mail.ru",
            "cnzz.com", "umeng.com", "umeng.co", "umengcloud.com", "mmstat.com", "alog.umeng.com",
            "51.la", "51la", "cnzz.zstat.cn", "ipinyou.com", "mediav.com", "tanx.com", "alimama.com",
            "miaozhen.com", "admaster.com", "adtrack.co", "irtech.cn", "adcdn.com", "admob-exchange.com",
            "adview.cn", "domob.cn", "domob.org", "duomeng.cn", "madhouse.cn", "mediamandala.com",
            "inmobi.com", "mopub.com", "chartboost.com", "unityads.unity3d.com", "unityads.unity.cn",
            "vungle.com", "applovin.com", "applovin.com.", "ironsrc.com", "adcolony.com", "tapjoy.com",
            "fyber.com", "chartboost.net", "startapp.com", "appnext.com", "loopme.me", "revmob.com",
            "mobfox.com", "adtech.de", "advertising.com", "adtechus.com", "yieldmo.com", "sharethrough.com",
            "sonobi.com", "gumgum.com", "triplelift.com", "3lift.com", "yieldlab.net", "zedo.com",
            "adhigh.net", "adriver.ru", "betweendigital.com", "exoclick.com", "juicyads.com", "trafficjunky.com",
            "popads.net", "propellerads.com", "adcash.com", "zeropark.com", "clickadu.com", "richads.com",
            "adsterra.com", "adsterratech.com", "hilltopads.net", "adcash.com.", "popcash.net",
            "megte.com", "unblocksit.es", "pushwoosh.com", "onesignal.com", "sendpulse.com", "webpushs.com",
            "admtpmp127.com", "dataxu.com", "tubemogul.com", "serving-sys.com", "flashtalking.com",
            "doubleverify.com", "moatpixel.com", "adsymptotic.com", "branch.io.", "appsflyer.com.",
            "gtag", "analytics.tiktok.com", "ads.tiktok.com", "business-api.tiktok.com",
            "graph.facebook.com", "connect.facebook.net", "pixel.facebook.com", "ads.facebook.com",
            "analytics.google.com", "stats.g.doubleclick.net", "region1.google-analytics.com",
            "px.ads.linkedin.com", "snap.licdn.com", "ads.linkedin.com", "ads.twitter.com", "static.ads-twitter.com",
            "analytics.twitter.com", "bat.bing.com", "adnexus.com", "adnxs-simple.com", "cdn.adnxs.com",
            "ads.yahoo.com", "adserver.yahoo.com", "beacon.sina.com.cn", "beacon.tudou.com", "atm.youku.com",
            "vip.lscdn.com", "logstat.tudou.com", "pv.sohu.com", "htm.blog.sohu.com", "imp.ad-plus.cn",
            "wave.pushnate.com", "adx.ads.oppomobile.com", "adsfs.oppomobile.com", "adxapi.ads.oppomobile.com",
            "mi.gdt.qq.com", "gdt.qq.com", "win.gdt.qq.com", "isdspeed.qq.com", "report.qq.com",
            "google-analytics.com", "ssl.google-analytics.com", "www.google-analytics.com",
            "firebase-settings.crashlytics.com", "app-measurement.com", "crashlytics.com",
            "analytics.163.com", "ad.163.com", "g.163.com", "broadcasthe.net-ad", "popads.net."
        )
        hosts.addAll(list.filter { it.contains('.') && !it.endsWith(".") })
    }

    fun isAd(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.lowercase()
        if (hosts.contains(h)) return true
        var idx = h.indexOf('.')
        while (idx >= 0) {
            if (hosts.contains(h.substring(idx + 1))) return true
            idx = h.indexOf('.', idx + 1)
        }
        return false
    }
}

class MonoWebClient(private val activity: MainActivity, private val tab: Tab) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        if (Prefs.adblock && !request.isForMainFrame) {
            val host = request.url.host
            if (AdHosts.isAd(host)) {
                Prefs.adblockCount = Prefs.adblockCount + 1
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }
        }
        return null
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        tab.loading = true
        tab.url = url
        tab.isHome = false
        U2.onTabStarted(activity, tab)
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView, url: String) {
        tab.loading = false
        if (url.startsWith("http")) {
            view.evaluateJavascript(MonoJs.HOOK, null)
        }
        U2.onTabFinished(activity, tab)
        super.onPageFinished(view, url)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        if (!isReload && url.startsWith("http") && Prefs.saveHistory) {
            U2.recordVisit(activity, tab, url)
        }
        super.doUpdateVisitedHistory(view, url, isReload)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: android.webkit.WebResourceError
    ) {
        if (request.isForMainFrame) {
            U2.onTabError(activity, tab, request.url.toString())
        }
        super.onReceivedError(view, request, error)
    }
}

class MonoChromeClient(private val activity: MainActivity, private val tab: Tab) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        U2.onProgress(activity, tab, newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        tab.title = (title ?: "").ifBlank { U2.hostOf(tab.url) }
        tab.url = view.url ?: tab.url
        U2.onTabChanged(activity, tab)
        super.onReceivedTitle(view, title)
    }

    override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
        tab.favicon = icon
        super.onReceivedIcon(view, icon)
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        activity.theater.show(view, callback, tab)
        super.onShowCustomView(view, callback)
    }

    override fun onHideCustomView() {
        activity.theater.onHiddenByPage()
        super.onHideCustomView()
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message
    ): Boolean {
        val newTab = activity.tabs.newTab()
        val wv = activity.tabs.webViewFor(newTab)
        val transport = resultMsg.obj as WebView.WebViewTransport
        transport.webView = wv
        resultMsg.sendToTarget()
        return true
    }

    override fun onCloseWindow(window: WebView?) {
        activity.tabs.tabs.firstOrNull { it.webView === window }?.let { activity.tabs.close(it) }
        super.onCloseWindow(window)
    }
}

/** 避免循环引用的小转发层 */
object U2 {
    fun onTabStarted(activity: MainActivity, tab: Tab) = activity.onTabStarted(tab)
    fun onTabFinished(activity: MainActivity, tab: Tab) = activity.onTabFinished(tab)
    fun onTabError(activity: MainActivity, tab: Tab, url: String) = activity.onTabError(tab, url)
    fun onProgress(activity: MainActivity, tab: Tab, p: Int) = activity.onProgress(tab, p)
    fun onTabChanged(activity: MainActivity, tab: Tab) = activity.onTabUi(tab)
    fun recordVisit(activity: MainActivity, tab: Tab, url: String) = activity.recordVisit(tab, url)
    fun hostOf(url: String): String {
        return try {
            val u = java.net.URI(url)
            u.host ?: url
        } catch (e: Exception) {
            url
        }
    }
}
