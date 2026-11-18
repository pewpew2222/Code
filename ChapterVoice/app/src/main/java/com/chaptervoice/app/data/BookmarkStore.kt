package com.chaptervoice.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Bookmark(val url: String, val title: String, val updatedAt: Long)

/**
 * Bookmarks, newest first. When the reader moves from a bookmarked chapter to the
 * next one, the bookmark moves along, so it always points at where you are in the book.
 */
class BookmarkStore private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(load())
    val items: StateFlow<List<Bookmark>> = mutable.asStateFlow()

    fun isBookmarked(url: String): Boolean = mutable.value.any { sameUrl(it.url, url) }

    fun toggle(url: String, title: String) {
        if (isBookmarked(url)) remove(url) else save(listOf(Bookmark(url, title.ifBlank { url }, now())) + mutable.value)
    }

    fun remove(url: String) = save(mutable.value.filterNot { sameUrl(it.url, url) })

    /** Moves bookmarks that point at [fromUrl] to [toUrl]. */
    @Synchronized
    fun advance(fromUrl: String, toUrl: String, toTitle: String) {
        val current = mutable.value
        if (current.none { sameUrl(it.url, fromUrl) }) return
        val moved = current.filter { sameUrl(it.url, fromUrl) }
            .map { it.copy(url = toUrl, title = toTitle.ifBlank { it.title }, updatedAt = now()) }
        val rest = current.filterNot { sameUrl(it.url, fromUrl) || sameUrl(it.url, toUrl) }
        save(moved.take(1) + rest)
    }

    @Synchronized
    private fun save(list: List<Bookmark>) {
        mutable.value = list
        val json = JSONArray()
        list.forEach { json.put(JSONObject().put("url", it.url).put("title", it.title).put("updatedAt", it.updatedAt)) }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun load(): List<Bookmark> = runCatching {
        val json = JSONArray(prefs.getString(KEY, "[]"))
        (0 until json.length()).map { i ->
            val o = json.getJSONObject(i)
            Bookmark(o.getString("url"), o.optString("title"), o.optLong("updatedAt"))
        }
    }.getOrDefault(emptyList())

    private fun now() = System.currentTimeMillis()

    companion object {
        private const val KEY = "items"

        @Volatile
        private var instance: BookmarkStore? = null

        fun get(context: Context): BookmarkStore =
            instance ?: synchronized(this) { instance ?: BookmarkStore(context).also { instance = it } }

        fun sameUrl(a: String, b: String): Boolean =
            a.substringBefore('#').trimEnd('/') == b.substringBefore('#').trimEnd('/')
    }
}
