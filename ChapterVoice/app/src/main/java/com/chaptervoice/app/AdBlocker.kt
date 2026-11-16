package com.chaptervoice.app

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.compose.runtime.mutableIntStateOf
import com.chaptervoice.app.core.HostBlocklist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Ad blocking for the in-app browser:
 * - requests to known ad and tracker domains are answered with an empty response,
 * - common ad containers, and elements the user picked, are hidden with CSS,
 * - pop-ups and redirects to ad domains or app stores are stopped (see TabManager).
 *
 * The main domain list ships with the app and is refreshed once a week. On top of it
 * come a small extra list and the user's own rules (block/allow a domain, hide an element).
 */
object AdBlocker {
    private const val TAG = "AdBlocker"
    private const val ASSET = "blocklist_hosts.txt"
    private const val EXTRA_ASSET = "blocklist_extra.txt"
    private const val UPDATED_FILE = "blocklist_hosts.txt"
    private const val UPDATE_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    private val UPDATE_INTERVAL_MS = TimeUnit.DAYS.toMillis(7)

    enum class HostStatus { BLOCKED_BY_LIST, BLOCKED_BY_YOU, ALLOWED_BY_YOU, ALLOWED }

    @Volatile private var blocklist: HostBlocklist = HostBlocklist.EMPTY
    @Volatile private var extra: HostBlocklist = HostBlocklist.EMPTY
    @Volatile private var userBlocked: Set<String> = emptySet()
    @Volatile private var userAllowed: Set<String> = emptySet()
    @Volatile private var hidden: Map<String, List<String>> = emptyMap()

    private var prefs: SharedPreferences? = null
    private var started = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Bumped when user rules change, so the UI can refresh. */
    val rulesVersion = mutableIntStateOf(0)

    val domainCount: Int get() = blocklist.size + extra.size

    /** CSS that hides typical ad slots. Kept conservative so story text is never hidden. */
    private const val HIDE_ADS_CSS =
        "ins.adsbygoogle,.adsbygoogle,[id^='google_ads'],[id^='div-gpt-ad'],[data-ad-slot],[data-ad-client]," +
            "[data-ad],[data-adunit],iframe[src*='doubleclick'],iframe[src*='googlesyndication']," +
            "iframe[id^='google_ads'],.ad-container,.ad-wrapper,.ad-slot,.ad-banner,.ads-banner," +
            ".adsbox,.ad-box,[class^='ads-'],[class*=' ads-'],[id^='ads-'],[id^='ad-'],[class^='ad-']," +
            "[class*=' ad-'],[class*='advert'],[id*='advert'],[class*='sponsor'],[id*='taboola']," +
            "[class*='taboola'],[id*='outbrain'],[class*='outbrain'],[id*='mgid'],[class*='mgid']," +
            "[id^='ezoic-'],[class*='ezoic-ad'],span[data-ez-ph-id],div[id^='atContainer']"

    /** Script that (re)applies the hiding CSS for the page's site. */
    fun cssScript(pageUrl: String?): String {
        val host = hostOf(pageUrl)
        // One rule per user selector, so a selector the browser rejects cannot break the others.
        val css = (listOf(HIDE_ADS_CSS) + hiddenFor(host)).joinToString("\n") { "$it{display:none!important;}" }
        return """
            (function(){
              var s = document.getElementById('cv-adblock');
              if (!s) {
                s = document.createElement('style');
                s.id = 'cv-adblock';
                (document.head || document.documentElement).appendChild(s);
              }
              s.textContent = ${JSONObject.quote(css)};
            })();
        """.trimIndent()
    }

    fun start(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("adblock_rules", Context.MODE_PRIVATE)
        loadRules()
        scope.launch {
            extra = runCatching {
                appContext.assets.open(EXTRA_ASSET).bufferedReader().useLines { HostBlocklist.parse(it) }
            }.getOrDefault(HostBlocklist.EMPTY)

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

    // ---------------------------------------------------------------- decisions

    fun status(host: String?): HostStatus {
        val h = host?.lowercase() ?: return HostStatus.ALLOWED
        return when {
            matches(userAllowed, h) -> HostStatus.ALLOWED_BY_YOU
            matches(userBlocked, h) -> HostStatus.BLOCKED_BY_YOU
            blocklist.isBlocked(h) || extra.isBlocked(h) -> HostStatus.BLOCKED_BY_LIST
            else -> HostStatus.ALLOWED
        }
    }

    fun isBlocked(url: Uri): Boolean = isBlockedHost(url.host)

    fun isBlockedHost(host: String?): Boolean =
        status(host).let { it == HostStatus.BLOCKED_BY_LIST || it == HostStatus.BLOCKED_BY_YOU }

    /** Returns an empty response for blocked sub-resources, or null to load normally. */
    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (request.isForMainFrame || !isBlocked(request.url)) return null
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    private fun matches(set: Set<String>, host: String): Boolean {
        var current = host
        while (true) {
            if (current in set) return true
            val dot = current.indexOf('.')
            if (dot < 0) return false
            current = current.substring(dot + 1)
        }
    }

    // ---------------------------------------------------------------- user rules

    /** Block a domain that the lists allow, or undo "allow" for one they block. */
    fun block(host: String) {
        userAllowed = userAllowed - host
        if (!isBlockedHost(host)) userBlocked = userBlocked + host
        saveRules()
    }

    /** Allow a domain: undo your own block, or override the lists. */
    fun allow(host: String) {
        userBlocked = userBlocked - host
        if (isBlockedHost(host)) userAllowed = userAllowed + host
        saveRules()
    }

    fun hiddenFor(host: String): List<String> = hidden[host].orEmpty()

    fun hide(pageUrl: String?, selector: String) {
        val host = hostOf(pageUrl).ifEmpty { return }
        hidden = hidden + (host to (hiddenFor(host) + selector).distinct())
        saveRules()
    }

    fun clearHidden(pageUrl: String?) {
        hidden = hidden - hostOf(pageUrl)
        saveRules()
    }

    private fun loadRules() {
        val p = prefs ?: return
        userBlocked = p.getStringSet("blocked", emptySet()).orEmpty().toSet()
        userAllowed = p.getStringSet("allowed", emptySet()).orEmpty().toSet()
        hidden = runCatching {
            val json = JSONObject(p.getString("hidden", "{}")!!)
            json.keys().asSequence().associateWith { key ->
                val arr = json.getJSONArray(key)
                (0 until arr.length()).map { arr.getString(it) }
            }
        }.getOrDefault(emptyMap())
    }

    private fun saveRules() {
        val json = JSONObject()
        hidden.forEach { (host, list) -> json.put(host, JSONArray(list)) }
        prefs?.edit()
            ?.putStringSet("blocked", userBlocked)
            ?.putStringSet("allowed", userAllowed)
            ?.putString("hidden", json.toString())
            ?.apply()
        rulesVersion.intValue++
    }

    fun hostOf(url: String?): String =
        runCatching { Uri.parse(url).host.orEmpty().lowercase().removePrefix("www.") }.getOrDefault("")
}
