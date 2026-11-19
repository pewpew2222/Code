package com.chaptervoice.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A book in the library: the last chapter listened to, and its cover if one was found. */
data class Book(
    val key: String,
    val name: String,
    val coverUrl: String?,
    /** True once a cover was looked for, found or not, so the site is not asked again. */
    val coverChecked: Boolean,
    val lastChapterUrl: String,
    val lastChapterTitle: String,
    val updatedAt: Long,
)

/** Books you have listened to, most recent first. Updated by the reader as chapters start. */
class BookStore private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("books", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(load())
    val items: StateFlow<List<Book>> = mutable.asStateFlow()

    fun get(key: String): Book? = mutable.value.firstOrNull { it.key == key }

    /** The reader started [chapterUrl] of book [key]: move the book to the top. */
    @Synchronized
    fun touch(key: String, name: String, chapterUrl: String, chapterTitle: String, coverUrl: String?) {
        val old = get(key)
        val book = Book(
            key = key,
            name = old?.name?.takeIf { it.isNotBlank() } ?: name,
            coverUrl = old?.coverUrl ?: coverUrl,
            coverChecked = old?.coverChecked == true || coverUrl != null,
            lastChapterUrl = chapterUrl,
            lastChapterTitle = chapterTitle,
            updatedAt = System.currentTimeMillis(),
        )
        save(listOf(book) + mutable.value.filterNot { it.key == key })
    }

    /** Adds a book found elsewhere (older reading positions, downloads) unless it is known. */
    @Synchronized
    fun addIfMissing(key: String, name: String, chapterUrl: String, chapterTitle: String, readAt: Long) {
        if (get(key) != null) return
        val book = Book(key, name, null, false, chapterUrl, chapterTitle, readAt)
        save((mutable.value + book).sortedByDescending { it.updatedAt })
    }

    @Synchronized
    fun setCover(key: String, coverUrl: String?) {
        save(mutable.value.map { if (it.key == key) it.copy(coverUrl = coverUrl ?: it.coverUrl, coverChecked = true) else it })
    }

    @Synchronized
    fun remove(key: String) = save(mutable.value.filterNot { it.key == key })

    private fun save(list: List<Book>) {
        mutable.value = list.take(MAX_BOOKS)
        val json = JSONArray()
        mutable.value.forEach {
            json.put(
                JSONObject().put("k", it.key).put("n", it.name).put("c", it.coverUrl ?: "").put("cc", it.coverChecked)
                    .put("u", it.lastChapterUrl).put("t", it.lastChapterTitle).put("v", it.updatedAt),
            )
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun load(): List<Book> = runCatching {
        val json = JSONArray(prefs.getString(KEY, "[]"))
        (0 until json.length()).map { i ->
            val o = json.getJSONObject(i)
            Book(
                o.getString("k"), o.optString("n"), o.optString("c").ifEmpty { null }, o.optBoolean("cc"),
                o.optString("u"), o.optString("t"), o.optLong("v"),
            )
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val KEY = "items"
        private const val MAX_BOOKS = 100

        @Volatile
        private var instance: BookStore? = null

        fun get(context: Context): BookStore =
            instance ?: synchronized(this) { instance ?: BookStore(context).also { instance = it } }
    }
}
