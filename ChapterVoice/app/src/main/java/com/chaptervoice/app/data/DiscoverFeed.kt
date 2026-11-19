package com.chaptervoice.app.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.chaptervoice.app.core.ChapterFetcher
import com.chaptervoice.app.core.DiscoverItem
import com.chaptervoice.app.core.DiscoverParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Suggested books per site, and which sites could not be read. */
data class DiscoverState(
    val sites: List<String> = emptyList(),
    val items: Map<String, List<DiscoverItem>> = emptyMap(),
    val failed: Set<String> = emptySet(),
    val loading: Boolean = false,
)

/**
 * Popular books from the sites you read on, for "Discover more" on the start page.
 * Fetched at most every few hours (or when your sites change) and kept between starts.
 */
object DiscoverFeed {
    const val MAX_SITES = 5
    private val INTERVAL_MS = TimeUnit.HOURS.toMillis(6)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(DiscoverState())
    val state: StateFlow<DiscoverState> = mutable.asStateFlow()
    private var loaded = false

    /** The sites to suggest from: those of your books first, then sites with a known popular list. */
    fun sitesFor(books: List<Book>): List<String> =
        (books.sortedByDescending { it.updatedAt }.map { it.key.substringBefore('/').removePrefix("www.") } +
            DiscoverParser.KNOWN.map { it.site })
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_SITES)

    /** Shows [preview] and fetches nothing (for screenshots). */
    internal fun showOnly(preview: DiscoverState) {
        loaded = true
        frozen = true
        mutable.value = preview
    }
    private var frozen = false

    fun refreshIfDue(context: Context, sites: List<String>, force: Boolean = false) {
        if (frozen) return
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("discover", Context.MODE_PRIVATE)
        if (!loaded) {
            loaded = true
            mutable.value = load(prefs.getString("feed", null))
        }
        val current = mutable.value
        if (current.loading) return
        val fresh = System.currentTimeMillis() - prefs.getLong("fetched_at", 0) < INTERVAL_MS
        if (!force && fresh && current.sites == sites) return
        mutable.value = current.copy(sites = sites, loading = true)
        scope.launch {
            val fetcher = fetcher(app)
            val results = sites.map { site ->
                async { site to runCatching { fetchSite(fetcher, site) }.getOrNull() }
            }.awaitAll()
            val items = results.mapNotNull { (site, list) -> list?.takeIf { it.isNotEmpty() }?.let { site to it } }.toMap()
            val failed = results.filter { it.second.isNullOrEmpty() }.map { it.first }.toSet()
            // Keep the last good list of a site that failed this time.
            val merged = sites.associateWith { items[it] ?: current.items[it].orEmpty() }.filterValues { it.isNotEmpty() }
            val next = DiscoverState(sites, merged, failed, loading = false)
            mutable.value = next
            prefs.edit()
                .putLong("fetched_at", if (items.isNotEmpty()) System.currentTimeMillis() else 0)
                .putString("feed", save(next))
                .apply()
        }
    }

    private fun fetchSite(fetcher: ChapterFetcher, site: String): List<DiscoverItem> {
        val source = DiscoverParser.sourceFor(site)
        val doc = fetcher.fetchDocument(source.url)
        return DiscoverParser.parse(doc, site)
    }

    private val pages = ConcurrentHashMap<String, DiscoverParser.BookPage>()

    /** The book page's description and first chapter, fetched once per app start. */
    suspend fun bookPage(context: Context, url: String): DiscoverParser.BookPage? {
        pages[url]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val doc = fetcher(context.applicationContext).fetchDocument(url)
                DiscoverParser.parseBookPage(doc, url)
            }.getOrNull()?.also { pages[url] = it }
        }
    }

    private fun fetcher(app: Context): ChapterFetcher {
        val agent = runCatching { WebSettings.getDefaultUserAgent(app) }.getOrDefault("Mozilla/5.0")
        return ChapterFetcher(agent) { runCatching { CookieManager.getInstance().getCookie(it) }.getOrNull() }
    }

    private fun save(state: DiscoverState): String = JSONObject().apply {
        put("sites", JSONArray(state.sites))
        put("failed", JSONArray(state.failed.toList()))
        put("items", JSONObject().apply {
            state.items.forEach { (site, list) ->
                put(site, JSONArray().apply {
                    list.forEach { item ->
                        put(JSONObject().apply {
                            put("title", item.title)
                            put("url", item.url)
                            put("cover", item.coverUrl ?: "")
                            put("summary", item.summary)
                            put("tags", JSONArray(item.tags))
                        })
                    }
                })
            }
        })
    }.toString()

    private fun load(json: String?): DiscoverState = runCatching {
        val root = JSONObject(json ?: return DiscoverState())
        fun JSONArray.strings() = (0 until length()).map { getString(it) }
        val items = root.getJSONObject("items").let { obj ->
            obj.keys().asSequence().associateWith { site ->
                val array = obj.getJSONArray(site)
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    DiscoverItem(
                        title = o.getString("title"),
                        url = o.getString("url"),
                        coverUrl = o.optString("cover").ifEmpty { null },
                        site = site,
                        summary = o.optString("summary"),
                        tags = o.optJSONArray("tags")?.strings().orEmpty(),
                    )
                }
            }
        }
        DiscoverState(root.getJSONArray("sites").strings(), items, root.optJSONArray("failed")?.strings().orEmpty().toSet())
    }.getOrDefault(DiscoverState())
}
