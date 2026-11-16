package com.chaptervoice.app.data

import android.content.Context
import android.net.Uri
import com.chaptervoice.app.core.Chapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class OfflineEntry(
    /** The address the chapter was requested with (what "next chapter" links point to). */
    val url: String,
    val title: String,
    val host: String,
    val savedAt: Long,
    val bytes: Long,
)

/**
 * Chapters saved for reading without internet. Each chapter is one small JSON file;
 * an index keeps the list for the UI. The oldest chapters are dropped past [MAX_ENTRIES].
 */
class OfflineStore private constructor(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "offline").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val mutable = MutableStateFlow(loadIndex())
    val entries: StateFlow<List<OfflineEntry>> = mutable.asStateFlow()

    fun has(url: String): Boolean = fileFor(url).exists()

    fun get(url: String): Chapter? = runCatching {
        val file = fileFor(url)
        if (!file.exists()) return null
        val o = JSONObject(file.readText())
        val paragraphs = o.getJSONArray("paragraphs").let { arr -> (0 until arr.length()).map { arr.getString(it) } }
        Chapter(
            url = o.getString("url"),
            title = o.optString("title"),
            paragraphs = paragraphs,
            nextUrl = o.optString("nextUrl").ifEmpty { null },
            pageTitle = o.optString("pageTitle"),
            heading = o.optString("heading"),
        )
    }.getOrNull()

    @Synchronized
    fun put(requestedUrl: String, chapter: Chapter) {
        val json = JSONObject()
            .put("url", chapter.url)
            .put("title", chapter.title)
            .put("paragraphs", JSONArray(chapter.paragraphs))
            .put("nextUrl", chapter.nextUrl ?: "")
            .put("pageTitle", chapter.pageTitle)
            .put("heading", chapter.heading)
            .toString()
        val file = fileFor(requestedUrl)
        file.writeText(json)
        val entry = OfflineEntry(
            url = requestedUrl,
            title = chapter.title.ifBlank { chapter.pageTitle },
            host = hostOf(requestedUrl),
            savedAt = System.currentTimeMillis(),
            bytes = file.length(),
        )
        var list = mutable.value.filterNot { key(it.url) == key(requestedUrl) } + entry
        while (list.size > MAX_ENTRIES) {
            fileFor(list.first().url).delete()
            list = list.drop(1)
        }
        save(list)
    }

    @Synchronized
    fun remove(url: String) {
        fileFor(url).delete()
        save(mutable.value.filterNot { key(it.url) == key(url) })
    }

    @Synchronized
    fun removeHost(host: String) {
        val (gone, kept) = mutable.value.partition { it.host == host }
        gone.forEach { fileFor(it.url).delete() }
        save(kept)
    }

    @Synchronized
    fun clear() {
        mutable.value.forEach { fileFor(it.url).delete() }
        save(emptyList())
    }

    private fun save(list: List<OfflineEntry>) {
        mutable.value = list
        val json = JSONArray()
        list.forEach {
            json.put(
                JSONObject().put("u", it.url).put("t", it.title).put("h", it.host)
                    .put("s", it.savedAt).put("b", it.bytes),
            )
        }
        runCatching { indexFile.writeText(json.toString()) }
    }

    private fun loadIndex(): List<OfflineEntry> = runCatching {
        if (!indexFile.exists()) return emptyList()
        val json = JSONArray(indexFile.readText())
        (0 until json.length()).map { i ->
            val o = json.getJSONObject(i)
            OfflineEntry(o.getString("u"), o.optString("t"), o.optString("h"), o.optLong("s"), o.optLong("b"))
        }.filter { fileFor(it.url).exists() }
    }.getOrDefault(emptyList())

    private fun key(url: String) = url.substringBefore('#').trimEnd('/')

    private fun fileFor(url: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(key(url).toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) } + ".json")
    }

    private fun hostOf(url: String) = runCatching { Uri.parse(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")

    companion object {
        private const val MAX_ENTRIES = 1000

        @Volatile
        private var instance: OfflineStore? = null

        fun get(context: Context): OfflineStore =
            instance ?: synchronized(this) { instance ?: OfflineStore(context).also { instance = it } }
    }
}
