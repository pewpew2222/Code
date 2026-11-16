package com.chaptervoice.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class HistoryEntry(val url: String, val title: String, val visitedAt: Long)

/**
 * Visited pages, newest first. Visiting a page again moves it to the top instead of
 * adding a duplicate. Saved to a file in the background.
 */
class HistoryStore private constructor(context: Context) {

    private val file = File(context.applicationContext.filesDir, "history.json")
    private val writer = Executors.newSingleThreadExecutor()
    private val mutable = MutableStateFlow(load())
    val items: StateFlow<List<HistoryEntry>> = mutable.asStateFlow()

    @Synchronized
    fun record(url: String, title: String) {
        if (!url.startsWith("http")) return
        val current = mutable.value
        val previousTitle = current.firstOrNull { BookmarkStore.sameUrl(it.url, url) }?.title.orEmpty()
        val entry = HistoryEntry(url, title.ifBlank { previousTitle }, System.currentTimeMillis())
        save(listOf(entry) + current.filterNot { BookmarkStore.sameUrl(it.url, url) }.take(MAX_ENTRIES - 1))
    }

    /** Fills in the title once the page reports it. */
    @Synchronized
    fun updateTitle(url: String, title: String) {
        if (title.isBlank()) return
        val current = mutable.value
        val index = current.indexOfFirst { BookmarkStore.sameUrl(it.url, url) }
        if (index < 0 || current[index].title == title) return
        save(current.toMutableList().also { it[index] = it[index].copy(title = title) })
    }

    @Synchronized
    fun remove(entry: HistoryEntry) = save(mutable.value.filterNot { it == entry })

    @Synchronized
    fun clear() = save(emptyList())

    private fun save(list: List<HistoryEntry>) {
        mutable.value = list
        writer.execute {
            val json = JSONArray()
            list.forEach { json.put(JSONObject().put("u", it.url).put("t", it.title).put("v", it.visitedAt)) }
            val temp = File(file.parentFile, file.name + ".tmp")
            runCatching {
                temp.writeText(json.toString())
                temp.renameTo(file)
            }
        }
    }

    private fun load(): List<HistoryEntry> = runCatching {
        if (!file.exists()) return emptyList()
        val json = JSONArray(file.readText())
        (0 until json.length()).map { i ->
            val o = json.getJSONObject(i)
            HistoryEntry(o.getString("u"), o.optString("t"), o.optLong("v"))
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val MAX_ENTRIES = 2000

        @Volatile
        private var instance: HistoryStore? = null

        fun get(context: Context): HistoryStore =
            instance ?: synchronized(this) { instance ?: HistoryStore(context).also { instance = it } }
    }
}
