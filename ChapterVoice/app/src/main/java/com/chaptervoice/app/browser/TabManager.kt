package com.chaptervoice.app.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chaptervoice.app.AdBlocker
import com.chaptervoice.app.AppSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * One browser tab. Its WebView is created the first time the tab is shown, so restored
 * tabs do not all load at start-up. A tab with an empty [url] shows the start page.
 */
class BrowserTab internal constructor(
    val id: Long,
    initialUrl: String,
    initialTitle: String,
    private val factory: (BrowserTab) -> WebView,
) {
    var url by mutableStateOf(initialUrl)
        internal set
    var title by mutableStateOf(initialTitle)
        internal set
    var progress by mutableIntStateOf(100)
        internal set
    var canGoBack by mutableStateOf(false)
        internal set
    var canGoForward by mutableStateOf(false)
        internal set

    private var view: WebView? = null

    val webView: WebView
        get() = view ?: factory(this).also { created ->
            view = created
            if (url.isNotEmpty()) created.loadUrl(url)
        }

    fun load(address: String) {
        if (view == null) {
            url = address
            webView
        } else {
            view?.loadUrl(address)
            url = address
        }
    }

    fun goBack() = view?.goBack()
    fun goForward() = view?.goForward()
    fun reload() = view?.reload()

    internal fun destroy() {
        view?.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        view = null
    }
}

/** Open tabs, the active tab, and saving/restoring them between app starts. */
class TabManager(private val context: Context, private val settings: AppSettings) {

    val tabs = mutableStateListOf<BrowserTab>()
    var activeId by mutableLongStateOf(-1L)
        private set

    /** A link the user long-pressed, for the "open in new tab" menu. */
    var linkMenuUrl by mutableStateOf<String?>(null)

    private val prefs = context.getSharedPreferences("tabs", Context.MODE_PRIVATE)
    private var nextId = 1L

    init {
        restore()
    }

    val active: BrowserTab
        get() = tabs.firstOrNull { it.id == activeId } ?: tabs.first()

    fun find(id: Long): BrowserTab? = tabs.firstOrNull { it.id == id }

    fun newTab(url: String = "", activate: Boolean = true): BrowserTab {
        val tab = BrowserTab(nextId++, url, "", ::createWebView)
        val insertAt = tabs.indexOfFirst { it.id == activeId }.let { if (it < 0) tabs.size else it + 1 }
        tabs.add(insertAt, tab)
        if (activate) activeId = tab.id
        save()
        return tab
    }

    fun select(id: Long) {
        if (find(id) != null) activeId = id
        save()
    }

    fun close(id: Long) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        tabs[index].destroy()
        tabs.removeAt(index)
        if (tabs.isEmpty()) {
            newTab()
            return
        }
        if (activeId == id) activeId = tabs[(index - 1).coerceAtLeast(0)].id
        save()
    }

    fun closeAll() {
        tabs.forEach { it.destroy() }
        tabs.clear()
        newTab()
    }

    fun destroyAll() = tabs.forEach { it.destroy() }

    fun save() {
        val json = JSONArray()
        tabs.forEach { json.put(JSONObject().put("url", it.url).put("title", it.title)) }
        prefs.edit()
            .putString("tabs", json.toString())
            .putInt("active", tabs.indexOfFirst { it.id == activeId }.coerceAtLeast(0))
            .apply()
    }

    private fun restore() {
        runCatching {
            val json = JSONArray(prefs.getString("tabs", "[]"))
            for (i in 0 until json.length()) {
                val o = json.getJSONObject(i)
                tabs += BrowserTab(nextId++, o.optString("url"), o.optString("title"), ::createWebView)
            }
            tabs.getOrNull(prefs.getInt("active", 0))?.let { activeId = it.id }
        }
        if (tabs.isEmpty()) {
            // First start: one tab, opened at the last page from older versions (if any).
            tabs += BrowserTab(nextId++, settings.lastUrlOrNull().orEmpty(), "", ::createWebView)
        }
        if (find(activeId) == null) activeId = tabs.first().id
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(tab: BrowserTab): WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = false
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        setOnLongClickListener {
            val hit = hitTestResult
            val link = hit.extra
            if (link != null && link.startsWith("http") &&
                (hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE || hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)
            ) {
                linkMenuUrl = link
                true
            } else {
                false
            }
        }

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                tab.url = url
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                if (this@TabManager.settings.adBlock) view.evaluateJavascript(AdBlocker.injectCssScript, null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (this@TabManager.settings.adBlock) view.evaluateJavascript(AdBlocker.injectCssScript, null)
                CookieManager.getInstance().flush()
                tab.url = url
                tab.title = view.title.orEmpty()
                tab.canGoBack = view.canGoBack()
                tab.canGoForward = view.canGoForward()
                save()
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                tab.url = url
                tab.canGoBack = view.canGoBack()
                tab.canGoForward = view.canGoForward()
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                if (this@TabManager.settings.adBlock) AdBlocker.intercept(request) else null

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url
                // intent://, market:// and similar: almost always ads trying to open an app store.
                if (target.scheme != "http" && target.scheme != "https") return true
                if (!this@TabManager.settings.adBlock) return false
                val currentHost = view.url?.let { Uri.parse(it).host }
                return target.host != currentHost && AdBlocker.isBlocked(target)
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                tab.progress = newProgress
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                tab.title = title.orEmpty()
            }

            /**
             * Pop-ups are never opened. A link the user tapped that asks for a new
             * window (target="_blank") opens in a new tab.
             */
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                if (!isUserGesture) return false
                val hit = view.hitTestResult
                val link = hit.extra ?: return false
                val isLink = hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                    hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                if (isLink && link.startsWith("http") &&
                    !(this@TabManager.settings.adBlock && AdBlocker.isBlocked(Uri.parse(link)))
                ) {
                    newTab(link)
                }
                return false
            }
        }
    }
}
