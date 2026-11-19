package com.chaptervoice.app.core

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** A video or animated image found on a page. */
data class MediaItem(
    val url: String,
    val kind: Kind,
    /** A still image to show for it (a video's poster), if any. */
    val preview: String? = null,
    val width: Int = 0,
    val height: Int = 0,
) {
    enum class Kind { VIDEO, GIF, STREAM }

    /** An HLS stream (.m3u8): many small pieces that are joined into one video when saved. */
    val isHls: Boolean get() = kind == Kind.STREAM && MediaFinder.extensionOf(url) == "m3u8"

    /** Files, and HLS streams; DASH streams cannot be saved yet. */
    val downloadable: Boolean get() = kind != Kind.STREAM || isHls

    val fileName: String
        get() {
            val name = url.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')
                .let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
                .replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
            val ext = MediaFinder.extensionOf(url)
            return when {
                name.isEmpty() -> "media." + (ext ?: if (kind == Kind.GIF) "gif" else "mp4")
                ext == null -> name + if (kind == Kind.GIF) ".gif" else ".mp4"
                else -> name
            }
        }
}

/**
 * Finds the videos and GIFs on a page: <video> and its sources, GIF images, links to
 * media files, the page's og:video, and media files the page loaded over the network.
 */
object MediaFinder {
    private val VIDEO_EXT = setOf("mp4", "webm", "mov", "m4v", "mkv", "ogv", "3gp")
    private val GIF_EXT = setOf("gif", "gifv")
    private val STREAM_EXT = setOf("m3u8", "mpd")

    fun extensionOf(url: String): String? =
        url.substringBefore('?').substringBefore('#').substringAfterLast('/').substringAfterLast('.', "")
            .lowercase().takeIf { it.isNotEmpty() && it.length <= 5 && it.all(Char::isLetterOrDigit) }

    /** What kind of media an address points at, judged by its file extension; null if none. */
    fun kindOf(url: String): MediaItem.Kind? = when (extensionOf(url)) {
        in VIDEO_EXT -> MediaItem.Kind.VIDEO
        in GIF_EXT -> MediaItem.Kind.GIF
        in STREAM_EXT -> MediaItem.Kind.STREAM
        else -> null
    }

    /**
     * [playing] are the addresses the page's video players are using right now;
     * [loaded] are media addresses seen in the page's network requests.
     */
    fun find(doc: Document, playing: List<String> = emptyList(), loaded: Collection<String> = emptyList()): List<MediaItem> {
        val found = LinkedHashMap<String, MediaItem>()
        fun add(url: String?, kind: MediaItem.Kind?, preview: String? = null, w: Int = 0, h: Int = 0) {
            val clean = url?.trim().orEmpty()
            if (!clean.startsWith("http") || kind == null) return
            // .gifv pages are videos with a .mp4 next to them (imgur).
            val (address, type) = if (extensionOf(clean) == "gifv") clean.substringBeforeLast('.') + ".mp4" to MediaItem.Kind.VIDEO else clean to kind
            val key = address.substringBefore('#')
            val existing = found[key]
            if (existing == null) {
                found[key] = MediaItem(address, type, preview, w, h)
            } else if (existing.preview == null && preview != null) {
                found[key] = existing.copy(preview = preview)
            }
        }

        doc.select("video").forEach { video ->
            val poster = video.absUrl("poster").ifEmpty { null }
            val w = video.attr("width").toIntOrNull() ?: 0
            val h = video.attr("height").toIntOrNull() ?: 0
            add(video.absUrl("src"), kindOf(video.absUrl("src")) ?: MediaItem.Kind.VIDEO, poster, w, h)
            video.select("source[src]").forEach { source ->
                val type = source.attr("type").lowercase()
                val kind = when {
                    "mpegurl" in type || "dash" in type -> MediaItem.Kind.STREAM
                    else -> kindOf(source.absUrl("src")) ?: MediaItem.Kind.VIDEO
                }
                add(source.absUrl("src"), kind, poster, w, h)
            }
        }
        playing.forEach { add(it, kindOf(it) ?: MediaItem.Kind.VIDEO) }

        doc.select("img").forEach { img ->
            for (attr in listOf("data-src", "data-original", "data-gif", "src")) {
                val src = img.absUrl(attr)
                if (kindOf(src) == MediaItem.Kind.GIF) {
                    add(src, MediaItem.Kind.GIF, src, img.attr("width").toIntOrNull() ?: 0, img.attr("height").toIntOrNull() ?: 0)
                    break
                }
            }
        }

        doc.select("a[href]").forEach { a ->
            val href = a.absUrl("href")
            val kind = kindOf(href) ?: return@forEach
            add(href, kind, previewInside(a))
        }

        doc.select("meta[property=og:video], meta[property=og:video:url], meta[property=og:video:secure_url]").forEach {
            val url = it.absUrl("content").ifEmpty { it.attr("content") }
            add(url, kindOf(url) ?: MediaItem.Kind.VIDEO, doc.selectFirst("meta[property=og:image]")?.attr("content"))
        }

        loaded.forEach { add(it, kindOf(it)) }
        return found.values.toList()
    }

    private fun previewInside(a: Element): String? = a.selectFirst("img")?.let { img ->
        listOf("data-src", "src").map { img.absUrl(it) }.firstOrNull { it.startsWith("http") }
    }
}
