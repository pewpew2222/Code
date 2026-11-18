package com.chaptervoice.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
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
import com.chaptervoice.app.AppSettings
import com.chaptervoice.app.R
import com.chaptervoice.app.core.Chapter
import com.chaptervoice.app.core.ChapterFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException

/**
 * Downloads a run of chapters: the start page, then its "next chapter", and so on.
 * Chapters already saved are skipped without using the network.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val start = inputData.getString(KEY_URL) ?: return Result.failure()
        val count = inputData.getInt(KEY_COUNT, 5).coerceIn(1, 200)
        val visible = inputData.getBoolean(KEY_VISIBLE, false)

        val settings = AppSettings(applicationContext)
        val store = OfflineStore.get(applicationContext)
        val userAgent = runCatching { WebSettings.getDefaultUserAgent(applicationContext) }
            .getOrDefault("Mozilla/5.0 (Linux; Android) Mobile")
        val fetcher = ChapterFetcher(userAgent) { url -> CookieManager.getInstance().getCookie(url) }

        if (visible) showProgress(0, count, null)

        var url: String? = start
        var previous: Chapter? = null
        var done = 0
        var error: String? = null
        while (url != null && done < count && !isStopped) {
            val requested: String = url
            val cached = store.get(requested)
            val chapter = cached ?: try {
                withContext(Dispatchers.IO) { fetcher.fetch(requested, settings.nextMode, previous?.url) }
            } catch (e: Exception) {
                error = describe(e)
                break
            }
            if (chapter.paragraphs.isEmpty()) {
                error = applicationContext.getString(R.string.error_no_text)
                break
            }
            // Redirected back to the same page or an index: the novel has ended.
            if (previous != null && (chapter.url == previous.url || chapter.textFingerprint == previous.textFingerprint)) break
            if (cached == null) store.put(requested, chapter)

            done++
            setProgress(workDataOf(KEY_DONE to done, KEY_COUNT to count, KEY_TITLE to chapter.title))
            if (visible) showProgress(done, count, chapter.title)
            previous = chapter
            url = chapter.nextUrl
            if (cached == null) delay(400) // go easy on the site
        }
        return Result.success(workDataOf(KEY_DONE to done, KEY_COUNT to count, KEY_ERROR to error))
    }

    private suspend fun showProgress(done: Int, total: Int, title: String?) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.downloads_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(applicationContext.getString(R.string.downloading, done, total))
            .setContentText(title.orEmpty())
            .setProgress(total, done, false)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, applicationContext.getString(R.string.cancel), WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
        // Not allowed in some background situations; the download then simply runs without it.
        runCatching { setForeground(info) }
    }

    private fun describe(error: Throwable): String = when (error) {
        is HttpStatusException -> applicationContext.getString(R.string.error_http, error.statusCode)
        is java.net.UnknownHostException -> applicationContext.getString(R.string.error_offline)
        is java.net.SocketTimeoutException -> applicationContext.getString(R.string.error_timeout)
        else -> applicationContext.getString(R.string.error_generic, error.message ?: error.javaClass.simpleName)
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_COUNT = "count"
        const val KEY_VISIBLE = "visible"
        const val KEY_DONE = "done"
        const val KEY_TITLE = "title"
        const val KEY_ERROR = "error"

        const val WORK_DOWNLOAD = "download"
        private const val WORK_PRELOAD = "preload"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 2

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Downloads [count] chapters starting with [url]. Queued after any running download. */
        fun download(context: Context, url: String, count: Int) {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_URL to url, KEY_COUNT to count, KEY_VISIBLE to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_DOWNLOAD, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        /** Keeps the next [count] chapters saved while reading (quietly, no notification). */
        fun preload(context: Context, nextUrl: String, count: Int) {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_URL to nextUrl, KEY_COUNT to count, KEY_VISIBLE to false))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_PRELOAD, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancelDownloads(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_DOWNLOAD)
        }
    }
}
