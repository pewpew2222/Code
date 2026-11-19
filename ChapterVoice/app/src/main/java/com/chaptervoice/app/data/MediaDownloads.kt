package com.chaptervoice.app.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.WebSettings
import com.chaptervoice.app.core.Hls
import com.chaptervoice.app.core.MediaFinder
import com.chaptervoice.app.core.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Saves videos and GIFs from web pages to Downloads/ChapterVoice with Android's download manager. */
object MediaDownloads {
    const val FOLDER = "ChapterVoice"

    private fun userAgent(context: Context) =
        runCatching { WebSettings.getDefaultUserAgent(context) }.getOrDefault("Mozilla/5.0")

    private fun cookies(url: String) = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()

    /** Starts downloading [items]; returns how many were started. */
    fun enqueue(
        context: Context,
        items: List<MediaItem>,
        pageUrl: String,
        streamChoice: Map<String, String> = emptyMap(),
        streamName: String? = null,
    ): Int {
        val manager = context.getSystemService(DownloadManager::class.java) ?: return 0
        val agent = userAgent(context)
        var started = 0
        val usedNames = mutableSetOf<String>()
        items.filter { it.downloadable }.forEach { item ->
            if (item.isHls) {
                StreamDownloadWorker.download(context, streamChoice[item.url] ?: item.url, streamName ?: "video", pageUrl)
                started++
                return@forEach
            }
            runCatching {
                var name = item.fileName
                var n = 2
                while (!usedNames.add(name)) name = name.substringBeforeLast('.') + "-${n++}." + name.substringAfterLast('.')
                val request = DownloadManager.Request(Uri.parse(item.url))
                    .setTitle(name)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .addRequestHeader("User-Agent", agent)
                    .addRequestHeader("Referer", pageUrl)
                cookies(item.url)?.takeIf { it.isNotBlank() }?.let { request.addRequestHeader("Cookie", it) }
                MediaFinder.extensionOf(name)?.let { ext ->
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { request.setMimeType(it) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$FOLDER/$name")
                } else {
                    // Older Android needs a storage permission for the shared folder; the app's own folder needs none.
                    request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
                }
                manager.enqueue(request)
                started++
            }
        }
        return started
    }

    /** What an HLS stream holds: its qualities, length, and whether it can be saved. */
    data class StreamInfo(
        val variants: List<Hls.Variant>,
        val duration: Double,
        val encrypted: Boolean,
        val drm: Boolean,
        val live: Boolean,
    ) {
        val saveable: Boolean get() = !drm && !live
    }

    suspend fun streamInfo(context: Context, url: String, pageUrl: String): StreamInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val text = fetchText(context, url, pageUrl)
            if (Hls.isMaster(text)) {
                val variants = Hls.variants(text, url)
                val best = variants.firstOrNull()?.let { Hls.mediaPlaylist(fetchText(context, it.url, pageUrl), it.url) }
                StreamInfo(variants, best?.duration ?: 0.0, best?.encrypted == true, best?.drm == true, best?.live == true)
            } else {
                val playlist = Hls.mediaPlaylist(text, url)
                StreamInfo(emptyList(), playlist.duration, playlist.encrypted, playlist.drm, playlist.live)
            }
        }.getOrNull()
    }

    private fun fetchText(context: Context, url: String, pageUrl: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", userAgent(context))
        connection.setRequestProperty("Referer", pageUrl)
        cookies(url)?.let { connection.setRequestProperty("Cookie", it) }
        try {
            if (connection.responseCode !in 200..299) throw java.io.IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.use { String(it.readBytes()) }
        } finally {
            connection.disconnect()
        }
    }

    /** The size of the file in bytes, if the server tells it. */
    suspend fun sizeOf(context: Context, url: String, pageUrl: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", userAgent(context))
            connection.setRequestProperty("Referer", pageUrl)
            cookies(url)?.let { connection.setRequestProperty("Cookie", it) }
            try {
                connection.contentLengthLong.takeIf { connection.responseCode in 200..299 && it > 0 }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
