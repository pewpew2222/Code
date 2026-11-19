package com.chaptervoice.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Where reading stopped in a chapter: paragraph [index] (0-based) of [total]. */
data class ReadingPosition(
    val url: String,
    val title: String,
    val host: String,
    val index: Int,
    val total: Int,
    val updatedAt: Long,
)

/**
 * Remembers the paragraph reached in each started chapter, so reading can continue
 * there after the app was closed. A chapter's entry is removed once it is finished.
 * Newest first; the oldest are dropped past [MAX_ENTRIES].
 */
class ReadingPositions private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("reading_positions", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(load())
    val items: StateFlow<List<ReadingPosition>> = mutable.asStateFlow()

    fun get(url: String): ReadingPosition? = mutable.value.firstOrNull { BookmarkStore.sameUrl(it.url, url) }

    @Synchronized
    fun save(url: String, title: String, index: Int, total: Int) {
        val current = get(url)
        if (current != null && current.index == index && current.total == total && current.title == title) return
        val entry = ReadingPosition(url, title, hostOf(url), index, total, System.currentTimeMillis())
        store((listOf(entry) + mutable.value.filterNot { BookmarkStore.sameUrl(it.url, url) }).take(MAX_ENTRIES))
    }

    @Synchronized
    fun remove(url: String) {
        if (get(url) == null) return
        store(mutable.value.filterNot { BookmarkStore.sameUrl(it.url, url) })
    }

    private fun store(list: List<ReadingPosition>) {
        mutable.value = list
        val json = JSONArray()
        list.forEach {
            json.put(
                JSONObject().put("u", it.url).put("t", it.title).put("h", it.host)
                    .put("i", it.index).put("n", it.total).put("v", it.updatedAt),
            )
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun load(): List<ReadingPosition> = runCatching {
        val json = JSONArray(prefs.getString(KEY, "[]"))
        (0 until json.length()).map { i ->
            val o = json.getJSONObject(i)
            ReadingPosition(o.getString("u"), o.optString("t"), o.optString("h"), o.optInt("i"), o.optInt("n"), o.optLong("v"))
        }
    }.getOrDefault(emptyList())

    private fun hostOf(url: String) = runCatching { Uri.parse(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")

    companion object {
        private const val KEY = "items"
        private const val MAX_ENTRIES = 50

        @Volatile
        private var instance: ReadingPositions? = null

        fun get(context: Context): ReadingPositions =
            instance ?: synchronized(this) { instance ?: ReadingPositions(context).also { instance = it } }
    }
}
