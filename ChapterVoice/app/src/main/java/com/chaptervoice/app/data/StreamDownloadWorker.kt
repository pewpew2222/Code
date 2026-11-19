package com.chaptervoice.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chaptervoice.app.R
import com.chaptervoice.app.core.Hls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Saves an HLS video stream as one .mp4: reads the playlist, downloads every piece
 * (decrypting AES-128 pieces with the key the site gives its player), joins them and
 * rewraps them as MP4 with Android's own MediaMuxer.
 */
class StreamDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val pageUrl get() = inputData.getString(KEY_REFERER).orEmpty()
    private val agent by lazy { runCatching { WebSettings.getDefaultUserAgent(applicationContext) }.getOrDefault("Mozilla/5.0") }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val name = inputData.getString(KEY_NAME) ?: "video"
        val joined = File(applicationContext.cacheDir, "stream-$id.part")
        val muxed = File(applicationContext.cacheDir, "stream-$id.mp4")
        try {
            showProgress(name, 0, 0)
            var playlistUrl = url
            var text = fetchText(playlistUrl)
            if (Hls.isMaster(text)) {
                playlistUrl = Hls.variants(text, playlistUrl).firstOrNull()?.url ?: throw IOException("No video in the playlist")
                text = fetchText(playlistUrl)
            }
            val playlist = Hls.mediaPlaylist(text, playlistUrl)
            if (playlist.drm) throw IOException(applicationContext.getString(R.string.stream_drm))
            if (playlist.live) throw IOException(applicationContext.getString(R.string.stream_live))
            if (playlist.segments.isEmpty()) throw IOException("Empty playlist")

            val keys = HashMap<String, ByteArray>()
            joined.outputStream().buffered().use { out ->
                playlist.initUrl?.let { out.write(fetchBytes(it)) }
                playlist.segments.forEachIndexed { i, segment ->
                    ensureActive()
                    var data = withRetries { fetchBytes(segment.url) }
                    val key = segment.key
                    if (key?.method == "AES-128" && key.url != null) {
                        val secret = keys.getOrPut(key.url) { withRetries { fetchBytes(key.url) } }
                        data = decrypt(data, secret, Hls.ivFor(segment))
                    }
                    out.write(data)
                    showProgress(name, i + 1, playlist.segments.size)
                }
            }

            // Rewrap as MP4; if the phone can't, keep the joined pieces as they are.
            val (file, ext, mime) = if (runCatching { remux(joined, muxed) }.isSuccess && muxed.length() > 0) {
                Triple(muxed, "mp4", "video/mp4")
            } else if (playlist.initUrl != null) {
                Triple(joined, "mp4", "video/mp4")
            } else {
                Triple(joined, "ts", "video/mp2t")
            }
            val saved = save(file, "${name.substringBeforeLast('.')}.$ext", mime)
            showDone(name, saved, mime)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            showFailed(name, e.message ?: e.javaClass.simpleName)
            Result.failure()
        } finally {
            joined.delete()
            muxed.delete()
        }
    }

    private suspend fun <T> withRetries(block: () -> T): T {
        var last: Throwable? = null
        repeat(3) { attempt ->
            try {
                return block()
            } catch (e: IOException) {
                last = e
                kotlinx.coroutines.delay(1000L * (attempt + 1))
            }
        }
        throw last!!
    }

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", agent)
        if (pageUrl.isNotEmpty()) {
            connection.setRequestProperty("Referer", pageUrl)
            runCatching { Uri.parse(pageUrl) }.getOrNull()?.let { connection.setRequestProperty("Origin", "${it.scheme}://${it.host}") }
        }
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()?.let { connection.setRequestProperty("Cookie", it) }
        if (connection.responseCode !in 200..299) {
            val code = connection.responseCode
            connection.disconnect()
            throw IOException(applicationContext.getString(R.string.error_http, code))
        }
        return connection
    }

    private fun fetchBytes(url: String): ByteArray = open(url).let { c -> try { c.inputStream.use { it.readBytes() } } finally { c.disconnect() } }
    private fun fetchText(url: String): String = String(fetchBytes(url))

    private fun decrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    /** Copies the audio and video tracks of [input] (MPEG-TS or fMP4) into an MP4 file. */
    private fun remux(input: File, output: File) {
        val extractor = MediaExtractor()
        extractor.setDataSource(input.absolutePath)
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val trackMap = HashMap<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    trackMap[i] = muxer.addTrack(format)
                }
            }
            if (trackMap.isEmpty()) throw IOException("No tracks")
            muxer.start()
            val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            var firstTime = -1L
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val track = trackMap[extractor.sampleTrackIndex]
                if (track != null) {
                    if (firstTime < 0) firstTime = extractor.sampleTime
                    info.set(0, size, (extractor.sampleTime - firstTime).coerceAtLeast(0), extractor.sampleFlags and MediaCodec.BUFFER_FLAG_KEY_FRAME)
                    muxer.writeSampleData(track, buffer, info)
                }
                extractor.advance()
            }
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            extractor.release()
        }
    }

    /** Puts the file in Downloads/ChapterVoice and returns its address. */
    private fun save(file: File, fileName: String, mime: String): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + MediaDownloads.FOLDER)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = applicationContext.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Could not save")
            resolver.openOutputStream(uri)?.use { out -> copy(file, out) } ?: throw IOException("Could not save")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        }
        val dir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: applicationContext.filesDir
        val target = File(dir, fileName)
        target.outputStream().use { copy(file, it) }
        return null // Older Android: in the app's own folder, which other apps can't open.
    }

    private fun copy(file: File, out: OutputStream) = file.inputStream().use { it.copyTo(out, 1 shl 16) }

    private fun channel(): NotificationManager {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.downloads_channel), NotificationManager.IMPORTANCE_LOW),
        )
        return manager
    }

    private suspend fun showProgress(name: String, done: Int, total: Int) {
        channel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(name)
            .setContentText(if (total > 0) applicationContext.getString(R.string.stream_progress, done, total) else applicationContext.getString(R.string.stream_starting))
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, applicationContext.getString(R.string.cancel), WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_ID, notification)
        }
        runCatching { setForeground(info) }
    }

    private fun showDone(name: String, uri: Uri?, mime: String) {
        val pending = uri?.let {
            val open = Intent(Intent.ACTION_VIEW).setDataAndType(it, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(applicationContext, id.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(applicationContext.getString(R.string.stream_done))
            .setContentText(name)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { channel().notify(id.hashCode(), notification) }
    }

    private fun showFailed(name: String, reason: String) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(applicationContext.getString(R.string.stream_failed, name))
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setAutoCancel(true)
            .build()
        runCatching { channel().notify(id.hashCode(), notification) }
    }

    companion object {
        private const val KEY_URL = "url"
        private const val KEY_NAME = "name"
        private const val KEY_REFERER = "referer"
        private const val CHANNEL_ID = "downloads"
        private const val PROGRESS_ID = 3
        private const val WORK = "stream"

        /** Saves the HLS stream at [playlistUrl] (one quality's playlist, or the master: best quality). */
        fun download(context: Context, playlistUrl: String, name: String, pageUrl: String) {
            val request = OneTimeWorkRequestBuilder<StreamDownloadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(KEY_URL to playlistUrl, KEY_NAME to name, KEY_REFERER to pageUrl))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
