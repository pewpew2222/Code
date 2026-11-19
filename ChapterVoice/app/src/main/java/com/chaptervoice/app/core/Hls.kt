package com.chaptervoice.app.core

import java.net.URI

/**
 * HLS playlists (.m3u8): a master playlist lists the qualities, a media playlist lists
 * the pieces of one quality. Only what is needed to save a video is read.
 */
object Hls {

    /** One quality in a master playlist. */
    data class Variant(val url: String, val bandwidth: Long, val width: Int, val height: Int) {
        val label: String get() = if (height > 0) "${height}p" else "${bandwidth / 1000} kbit/s"
    }

    data class Key(val method: String, val url: String?, val iv: ByteArray?) {
        override fun equals(other: Any?) = other is Key && method == other.method && url == other.url && iv.contentEquals(other.iv)
        override fun hashCode() = method.hashCode() * 31 + (url?.hashCode() ?: 0)
    }

    data class Segment(val url: String, val duration: Double, val sequence: Long, val key: Key?)

    data class MediaPlaylist(
        val segments: List<Segment>,
        /** The fragmented-MP4 header (EXT-X-MAP), when the pieces are fMP4 rather than MPEG-TS. */
        val initUrl: String?,
        /** A live stream keeps growing and has no end to save. */
        val live: Boolean,
        /** Protected with DRM (SAMPLE-AES and similar), which cannot be saved. */
        val drm: Boolean,
    ) {
        val duration: Double get() = segments.sumOf { it.duration }
        val encrypted: Boolean get() = segments.any { it.key?.method == "AES-128" }
    }

    fun isMaster(text: String) = text.contains("#EXT-X-STREAM-INF")

    /** The qualities in a master playlist, best first. */
    fun variants(text: String, baseUrl: String): List<Variant> {
        val lines = text.lines().map { it.trim() }
        val result = mutableListOf<Variant>()
        lines.forEachIndexed { i, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF:")) return@forEachIndexed
            val attrs = attributes(line.substringAfter(':'))
            val uri = lines.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return@forEachIndexed
            val (w, h) = attrs["RESOLUTION"]?.split('x')?.let { (it.getOrNull(0)?.toIntOrNull() ?: 0) to (it.getOrNull(1)?.toIntOrNull() ?: 0) } ?: (0 to 0)
            result += Variant(resolve(baseUrl, uri), attrs["BANDWIDTH"]?.toLongOrNull() ?: 0, w, h)
        }
        return result.distinctBy { it.url }.sortedWith(compareByDescending<Variant> { it.height }.thenByDescending { it.bandwidth })
    }

    fun mediaPlaylist(text: String, baseUrl: String): MediaPlaylist {
        val segments = mutableListOf<Segment>()
        var sequence = 0L
        var key: Key? = null
        var duration = 0.0
        var initUrl: String? = null
        var drm = false
        var ended = false
        text.lines().map { it.trim() }.forEach { line ->
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> sequence = line.substringAfter(':').trim().toLongOrNull() ?: 0
                line.startsWith("#EXT-X-KEY:") -> {
                    val attrs = attributes(line.substringAfter(':'))
                    val method = attrs["METHOD"].orEmpty().uppercase()
                    key = when (method) {
                        "NONE", "" -> null
                        "AES-128" -> Key(method, attrs["URI"]?.let { resolve(baseUrl, it) }, attrs["IV"]?.let(::hex))
                        else -> { drm = true; Key(method, null, null) }
                    }
                    if (attrs["KEYFORMAT"]?.let { it != "identity" } == true) drm = true
                }
                line.startsWith("#EXT-X-MAP:") -> initUrl = attributes(line.substringAfter(':'))["URI"]?.let { resolve(baseUrl, it) }
                line.startsWith("#EXTINF:") -> duration = line.substringAfter(':').substringBefore(',').trim().toDoubleOrNull() ?: 0.0
                line.startsWith("#EXT-X-ENDLIST") -> ended = true
                line.isNotEmpty() && !line.startsWith("#") -> {
                    segments += Segment(resolve(baseUrl, line), duration, sequence, key)
                    sequence++
                    duration = 0.0
                }
            }
        }
        return MediaPlaylist(segments, initUrl, live = !ended, drm = drm)
    }

    /** The IV for a piece: the playlist's, or else its sequence number as 16 bytes. */
    fun ivFor(segment: Segment): ByteArray = segment.key?.iv ?: ByteArray(16).also { iv ->
        var n = segment.sequence
        for (i in 15 downTo 8) {
            iv[i] = (n and 0xff).toByte()
            n = n shr 8
        }
    }

    private val ATTR = Regex("([A-Z0-9-]+)=(\"[^\"]*\"|[^,]*)")

    internal fun attributes(text: String): Map<String, String> =
        ATTR.findAll(text).associate { it.groupValues[1] to it.groupValues[2].trim('"') }

    private fun hex(value: String): ByteArray? {
        val digits = value.removePrefix("0x").removePrefix("0X")
        if (digits.length % 2 != 0 || digits.isEmpty()) return null
        return runCatching { ByteArray(digits.length / 2) { digits.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }.getOrNull()
            ?.let { if (it.size < 16) ByteArray(16 - it.size) + it else it.takeLast(16).toByteArray() }
    }

    fun resolve(base: String, relative: String): String =
        runCatching { URI(base).resolve(relative.replace(" ", "%20")).toString() }.getOrDefault(relative)
}
