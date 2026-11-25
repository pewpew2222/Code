package com.chaptervoice.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.webkit.CookieManager
import android.webkit.WebSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads images (book covers) once and keeps them on disk and in memory.
 *
 * Image servers of novel sites are picky: many refuse requests without the site as
 * referrer or without the cookies the browser got (Cloudflare), send newer formats that
 * Android can't show unless told what it accepts, or redirect from http to https.
 */
object ImageCache {
    private val memory = LruCache<String, Bitmap>(60)

    /** Addresses that failed, with when; tried again after a while (the network may be back). */
    private val failed = HashMap<String, Long>()
    private const val RETRY_AFTER_MS = 5 * 60_000L

    fun cached(url: String): Bitmap? = memory.get(url)

    /**
     * The image at [url], scaled down to at most [maxSize] px, or null if it can't be loaded.
     * [referer] is the page that shows the image (a chapter or the book's page).
     */
    suspend fun load(context: Context, url: String, referer: String? = null, maxSize: Int = 600): Bitmap? =
        withContext(Dispatchers.IO) {
            memory.get(url)?.let { return@withContext it }
            val failedAt = synchronized(failed) { failed[url] }
            if (failedAt != null && System.currentTimeMillis() - failedAt < RETRY_AFTER_MS) return@withContext null
            val dir = File(context.cacheDir, "images").apply { mkdirs() }
            val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
            val file = File(dir, digest)
            var bitmap = if (file.exists()) decode(file.readBytes(), maxSize) else null
            if (bitmap == null) {
                file.delete()
                val bytes = download(context, url, referer)
                bitmap = bytes?.let { decode(it, maxSize) }
                if (bitmap != null && bytes != null) runCatching { file.writeBytes(bytes) }
            }
            if (bitmap == null) {
                synchronized(failed) { failed[url] = System.currentTimeMillis() }
                return@withContext null
            }
            synchronized(failed) { failed.remove(url) }
            memory.put(url, bitmap)
            bitmap
        }

    private fun decode(bytes: ByteArray, maxSize: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /**
     * Tries plainly first (as most image servers want), then as the site's own page would
     * ask: with the page as referrer and the browser's cookies.
     */
    internal fun download(context: Context, url: String, referer: String?): ByteArray? {
        val agent = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrDefault("Mozilla/5.0")
        var address = url.trim().replace(" ", "%20")
        // Android refuses plain http: then the same address over https.
        if (address.startsWith("http://")) {
            val plain = fetch(address, agent, null)
            if (plain is Fetched.Image) return plain.bytes
            if (plain is Fetched.Unreachable) address = "https://" + address.removePrefix("http://")
        }
        val referrers = (listOf<String?>(null) + listOfNotNull(referer, referer?.let(::originOf), originOf(address))).distinct()
        for (ref in referrers) {
            when (val result = fetch(address, agent, ref)) {
                is Fetched.Image -> return result.bytes
                is Fetched.Unreachable -> return null // No answer at all: other referrers won't help.
                is Fetched.Refused -> Unit
            }
        }
        return null
    }

    private sealed interface Fetched {
        class Image(val bytes: ByteArray) : Fetched
        /** The server answered, but not with the image (403, 404…). */
        data object Refused : Fetched
        /** No answer: offline, blocked, or the protocol was refused. */
        data object Unreachable : Fetched
    }

    private fun originOf(url: String): String? =
        runCatching { URL(url).let { "${it.protocol}://${it.host}/" } }.getOrNull()

    private fun fetch(start: String, agent: String, referer: String?): Fetched {
        var address = start
        repeat(6) {
            val connection = try {
                URL(address).openConnection() as HttpURLConnection
            } catch (e: Exception) {
                return Fetched.Unreachable
            }
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            // Followed by hand below: Java does not follow http <-> https redirects.
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", agent)
            // Formats Android can show; without this some servers send AVIF.
            connection.setRequestProperty("Accept", "image/webp,image/apng,image/png,image/jpeg,image/gif,image/*;q=0.8")
            referer?.let { connection.setRequestProperty("Referer", it) }
            cookies(address)?.let { connection.setRequestProperty("Cookie", it) }
            try {
                val code = try {
                    connection.responseCode
                } catch (e: Exception) {
                    return Fetched.Unreachable
                }
                when (code) {
                    200 -> return runCatching { Fetched.Image(connection.inputStream.use { it.readBytes() }) }.getOrDefault(Fetched.Refused)
                    301, 302, 303, 307, 308 -> {
                        val location = connection.getHeaderField("Location") ?: return Fetched.Refused
                        address = runCatching { URL(URL(address), location).toString() }.getOrNull() ?: return Fetched.Refused
                    }
                    else -> return Fetched.Refused
                }
            } finally {
                connection.disconnect()
            }
        }
        return Fetched.Refused
    }

    private fun cookies(url: String): String? =
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()?.takeIf { it.isNotBlank() }
}
