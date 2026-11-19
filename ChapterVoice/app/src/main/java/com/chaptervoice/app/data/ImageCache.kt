package com.chaptervoice.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.webkit.WebSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Downloads images (book covers) once and keeps them on disk and in memory. */
object ImageCache {
    private val memory = LruCache<String, Bitmap>(40)
    private val failed = HashSet<String>()

    fun cached(url: String): Bitmap? = memory.get(url)

    /** The image at [url], scaled down to at most [maxSize] px, or null if it can't be loaded. */
    suspend fun load(context: Context, url: String, referer: String? = null, maxSize: Int = 600): Bitmap? =
        withContext(Dispatchers.IO) {
            memory.get(url)?.let { return@withContext it }
            if (synchronized(failed) { url in failed }) return@withContext null
            val dir = File(context.cacheDir, "images").apply { mkdirs() }
            val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
            val file = File(dir, digest)
            val bytes = if (file.exists()) file.readBytes() else download(context, url, referer)?.also { file.writeBytes(it) }
            val bitmap = bytes?.let { decode(it, maxSize) }
            if (bitmap == null) {
                synchronized(failed) { failed += url }
                file.delete()
                return@withContext null
            }
            memory.put(url, bitmap)
            bitmap
        }

    private fun decode(bytes: ByteArray, maxSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun download(context: Context, url: String, referer: String?): ByteArray? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", runCatching { WebSettings.getDefaultUserAgent(context) }.getOrDefault("Mozilla/5.0"))
        referer?.let { connection.setRequestProperty("Referer", it) }
        try {
            if (connection.responseCode != 200) null else connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
