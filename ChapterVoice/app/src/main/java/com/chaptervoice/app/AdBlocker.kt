package com.chaptervoice.app

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.chaptervoice.app.core.HostBlocklist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Ad blocking for the in-app browser:
 * - requests to known ad and tracker domains are answered with an empty response,
 * - common ad containers are hidden with CSS,
 * - pop-ups and redirects to ad domains or app stores are stopped (see ReaderScreen).
 *
 * The domain list ships with the app and is refreshed from the internet once a week.
 */
object AdBlocker {
    private const val TAG = "AdBlocker"
    private const val ASSET = "blocklist_hosts.txt"
    private const val UPDATED_FILE = "blocklist_hosts.txt"
    private const val UPDATE_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    private val UPDATE_INTERVAL_MS = TimeUnit.DAYS.toMillis(7)

    @Volatile
    private var blocklist: HostBlocklist = HostBlocklist.EMPTY
    private var started = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val domainCount: Int get() = blocklist.size

    /** CSS that hides typical ad slots. Kept conservative so story text is never hidden. */
    private const val HIDE_ADS_CSS =
        "ins.adsbygoogle,.adsbygoogle,[id^='google_ads'],[id^='div-gpt-ad'],[data-ad-slot],[data-ad-client]," +
            "[data-ad],[data-adunit],iframe[src*='doubleclick'],iframe[src*='googlesyndication']," +
            "iframe[id^='google_ads'],.ad-container,.ad-wrapper,.ad-slot,.ad-banner,.ads-banner," +
            ".adsbox,.ad-box,[class^='ads-'],[class*=' ads-'],[id^='ads-'],[id^='ad-'],[class^='ad-']," +
            "[class*=' ad-'],[class*='advert'],[id*='advert'],[class*='sponsor'],[id*='taboola']," +
            "[class*='taboola'],[id*='outbrain'],[class*='outbrain'],[id*='mgid'],[class*='mgid']" +
            "{display:none!important;}"

    val injectCssScript: String =
        """
        (function(){
          if (document.getElementById('cv-adblock')) return;
          var s = document.createElement('style');
          s.id = 'cv-adblock';
          s.textContent = ${org.json.JSONObject.quote(HIDE_ADS_CSS)};
          (document.head || document.documentElement).appendChild(s);
        })();
        """.trimIndent()

    fun start(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        scope.launch {
            val updated = File(appContext.filesDir, UPDATED_FILE)
            blocklist = runCatching {
                if (updated.exists()) updated.useLines { HostBlocklist.parse(it) }
                else appContext.assets.open(ASSET).bufferedReader().useLines { HostBlocklist.parse(it) }
            }.getOrElse {
                Log.w(TAG, "Could not load blocklist", it)
                HostBlocklist.EMPTY
            }
            val lastModified = if (updated.exists()) updated.lastModified() else 0L
            if (System.currentTimeMillis() - lastModified > UPDATE_INTERVAL_MS) update(updated)
        }
    }

    private fun update(target: File) {
        runCatching {
            val connection = URL(UPDATE_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            try {
                if (connection.responseCode != 200) return
                val temp = File(target.parentFile, "$UPDATED_FILE.tmp")
                connection.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
                val fresh = temp.useLines { HostBlocklist.parse(it) }
                if (fresh.size < 1_000) { // something went wrong; keep the old list
                    temp.delete()
                    return
                }
                temp.renameTo(target)
                blocklist = fresh
            } finally {
                connection.disconnect()
            }
        }.onFailure { Log.i(TAG, "Blocklist update failed", it) }
    }

    fun isBlocked(url: Uri): Boolean = blocklist.isBlocked(url.host)

    /** Returns an empty response for blocked sub-resources, or null to load normally. */
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (request.isForMainFrame || !isBlocked(request.url)) return null
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }
}
