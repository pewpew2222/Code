package com.chaptervoice.app.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.chaptervoice.app.AppSettings
import com.chaptervoice.app.core.Chapter
import com.chaptervoice.app.core.ChapterFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** New chapters found for a book that was read up to its newest chapter. */
data class BookUpdate(val bookKey: String, val newChapters: Int, val firstNewUrl: String, val firstNewTitle: String)

/**
 * Looks for new chapters of the books you have caught up with: is there now a chapter
 * after the last one read? Counts up to [MAX_COUNT]. Runs at most every few hours.
 */
object UpdateChecker {
    private const val MAX_BOOKS = 8
    const val MAX_COUNT = 5
    private val INTERVAL_MS = TimeUnit.HOURS.toMillis(3)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow<Map<String, BookUpdate>>(emptyMap())
    val updates: StateFlow<Map<String, BookUpdate>> = mutable.asStateFlow()
    @Volatile private var running = false
    private var loaded = false

    fun checkIfDue(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("updates", Context.MODE_PRIVATE)
        if (!loaded) {
            loaded = true
            mutable.value = load(prefs.getString("found", "{}").orEmpty())
        }
        val last = prefs.getLong("checked_at", 0)
        if (running || (!force && System.currentTimeMillis() - last < INTERVAL_MS)) return
        running = true
        scope.launch {
            try {
                val settings = AppSettings(app)
                val agent = runCatching { WebSettings.getDefaultUserAgent(app) }.getOrDefault("Mozilla/5.0")
                val fetcher = ChapterFetcher(agent) { CookieManager.getInstance().getCookie(it) }
                val found = mutableMapOf<String, BookUpdate>()
                BookStore.get(app).items.value.filter { it.caughtUp }.take(MAX_BOOKS).forEach { book ->
                    runCatching { newChapters(fetcher, settings, book) }.getOrNull()?.let { found[book.key] = it }
                }
                mutable.value = found
                prefs.edit().putLong("checked_at", System.currentTimeMillis()).putString("found", save(found)).apply()
            } finally {
                running = false
            }
        }
    }

    /** Clears a book's update once it is being read again. */
    fun clear(context: Context, bookKey: String) {
        if (bookKey !in mutable.value) return
        mutable.value = mutable.value - bookKey
        context.applicationContext.getSharedPreferences("updates", Context.MODE_PRIVATE)
            .edit().putString("found", save(mutable.value)).apply()
    }

    private fun newChapters(fetcher: ChapterFetcher, settings: AppSettings, book: Book): BookUpdate? {
        var current: Chapter = fetcher.fetch(book.lastChapterUrl, settings.nextMode)
        var first: Chapter? = null
        var firstUrl = ""
        var count = 0
        while (count < MAX_COUNT) {
            val nextUrl = current.nextUrl ?: break
            val next = runCatching { fetcher.fetch(nextUrl, settings.nextMode, current.url) }.getOrNull() ?: break
            // An empty page, or the same text again (a redirect to an index), is no chapter.
            if (next.paragraphs.isEmpty() || next.url == current.url || next.textFingerprint == current.textFingerprint) break
            if (first == null) {
                first = next
                firstUrl = nextUrl
            }
            count++
            current = next
        }
        return first?.let { BookUpdate(book.key, count, firstUrl, it.title) }
    }

    private fun save(map: Map<String, BookUpdate>): String = JSONObject().apply {
        map.values.forEach { put(it.bookKey, JSONObject().put("n", it.newChapters).put("u", it.firstNewUrl).put("t", it.firstNewTitle)) }
    }.toString()

    private fun load(json: String): Map<String, BookUpdate> = runCatching {
        val o = JSONObject(json)
        o.keys().asSequence().associateWith { key ->
            val v = o.getJSONObject(key)
            BookUpdate(key, v.optInt("n"), v.optString("u"), v.optString("t"))
        }
    }.getOrDefault(emptyMap())
}
