package com.chaptervoice.app.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.chaptervoice.app.core.ChapterFetcher
import com.chaptervoice.app.core.DiscoverItem
import com.chaptervoice.app.core.SiteSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** What searching one site gave. */
data class SiteResults(
    val site: String,
    val items: List<DiscoverItem> = emptyList(),
    val searching: Boolean = true,
    /** The site could not be searched (blocked, offline, no search page found). */
    val failed: Boolean = false,
)

/** Searches several novel sites at once for a book. */
object MultiSearch {
    private const val TIMEOUT_MS = 20_000L

    /** Searches one site, trying its likely search pages until one gives matching books. */
    suspend fun search(context: Context, site: String, query: String): SiteResults = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val agent = runCatching { WebSettings.getDefaultUserAgent(app) }.getOrDefault("Mozilla/5.0")
        val fetcher = ChapterFetcher(agent) { runCatching { CookieManager.getInstance().getCookie(it) }.getOrNull() }
        var reached = false
        val items = withTimeoutOrNull(TIMEOUT_MS) {
            for (url in SiteSearch.candidates(site, query)) {
                val doc = runCatching { fetcher.fetchDocument(url) }.getOrNull() ?: continue
                reached = true
                val found = SiteSearch.results(doc, site, query)
                if (found.isNotEmpty()) return@withTimeoutOrNull found
            }
            emptyList()
        }.orEmpty()
        SiteResults(site, items, searching = false, failed = items.isEmpty() && !reached)
    }
}
