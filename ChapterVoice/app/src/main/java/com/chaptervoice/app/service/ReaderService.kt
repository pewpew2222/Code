package com.chaptervoice.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.chaptervoice.app.AppSettings
import com.chaptervoice.app.MainActivity
import com.chaptervoice.app.R
import com.chaptervoice.app.core.Chapter
import com.chaptervoice.app.core.ChapterFetcher
import com.chaptervoice.app.core.ChapterParser
import com.chaptervoice.app.core.TextChunker
import com.chaptervoice.app.core.TextCleaner
import com.chaptervoice.app.data.BookmarkStore
import com.chaptervoice.app.data.DownloadWorker
import com.chaptervoice.app.data.OfflineStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import java.net.URI

/**
 * Reads chapters aloud in the foreground and moves on to the next chapter by itself.
 *
 * All state is touched on the main thread; TTS callbacks are posted back to it.
 */
class ReaderService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_PLAY_URL = "com.chaptervoice.PLAY_URL"
        const val ACTION_PLAY_PENDING_PAGE = "com.chaptervoice.PLAY_PENDING_PAGE"
        const val ACTION_TOGGLE = "com.chaptervoice.TOGGLE"
        const val ACTION_NEXT_PARAGRAPH = "com.chaptervoice.NEXT_PARAGRAPH"
        const val ACTION_PREV_PARAGRAPH = "com.chaptervoice.PREV_PARAGRAPH"
        const val ACTION_NEXT_CHAPTER = "com.chaptervoice.NEXT_CHAPTER"
        const val ACTION_SETTINGS_CHANGED = "com.chaptervoice.SETTINGS_CHANGED"
        const val ACTION_STOP = "com.chaptervoice.STOP"
        const val EXTRA_URL = "url"

        private const val CHANNEL_ID = "reader"
        private const val NOTIFICATION_ID = 1

        fun send(context: Context, action: String, url: String? = null) {
            val intent = Intent(context, ReaderService::class.java).setAction(action)
            url?.let { intent.putExtra(EXTRA_URL, it) }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settings: AppSettings
    private lateinit var fetcher: ChapterFetcher
    private lateinit var tts: TextToSpeech
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private lateinit var wakeLock: PowerManager.WakeLock

    private var ttsReady = false
    private var afterTtsReady: (() -> Unit)? = null

    private var chapter: Chapter? = null
    private var pieces: List<String> = emptyList()
    private var index = 0
    private var playing = false

    /** Increased whenever queued speech becomes stale, so old callbacks are ignored. */
    private var generation = 0
    private var loadJob: Job? = null
    private var prefetch: Pair<String, Deferred<Result<Chapter>>>? = null
    private var chaptersRead = 0

    /** The address the current chapter was opened with (its key in the offline store). */
    private var requestedUrl: String? = null

    private var focusRequest: AudioFocusRequestCompat? = null
    private var resumeOnFocusGain = false
    private var noisyRegistered = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        val userAgent = runCatching { WebSettings.getDefaultUserAgent(this) }
            .getOrDefault("Mozilla/5.0 (Linux; Android) Mobile")
        fetcher = ChapterFetcher(userAgent) { url -> CookieManager.getInstance().getCookie(url) }
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChapterVoice:reader")
            .apply { setReferenceCounted(false) }

        if (TextCleaner.dictionary == null) {
            scope.launch(Dispatchers.IO) {
                TextCleaner.dictionary = runCatching {
                    val words = assets.open("words_en.txt").bufferedReader().useLines { it.toList() }
                    val acronyms = assets.open("acronyms_en.txt").bufferedReader().useLines { it.toList() }
                    TextCleaner.Dictionary.load(words.asSequence(), acronyms.asSequence())
                }.getOrNull()
            }
        }

        createChannel()
        mediaSession = MediaSessionCompat(this, "ChapterVoice").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onStop() = stopReading()
                override fun onSkipToNext() = nextChapter()
                override fun onSkipToPrevious() = skipParagraph(-1)
                override fun onFastForward() = skipParagraph(1)
                override fun onRewind() = skipParagraph(-1)
            })
            isActive = true
        }

        tts = TextToSpeech(this, this)
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                scope.launch { onPieceStarted(utteranceId) }
            }

            override fun onDone(utteranceId: String) {
                scope.launch { onPieceDone(utteranceId) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                scope.launch { onPieceDone(utteranceId) }
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                scope.launch { onPieceDone(utteranceId) }
            }
        })
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            showError(getString(R.string.error_tts_init))
            return
        }
        ttsReady = true
        applyVoiceSettings()
        afterTtsReady?.invoke()
        afterTtsReady = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        when (intent?.action) {
            ACTION_PLAY_URL -> intent.getStringExtra(EXTRA_URL)?.let { url ->
                chaptersRead = 0
                whenTtsReady { load(url, html = null) }
            }
            ACTION_PLAY_PENDING_PAGE -> ReaderState.pendingPage?.let { (url, html) ->
                ReaderState.pendingPage = null
                chaptersRead = 0
                whenTtsReady { load(url, html) }
            }
            ACTION_TOGGLE -> if (playing) pause() else resume()
            ACTION_NEXT_PARAGRAPH -> skipParagraph(1)
            ACTION_PREV_PARAGRAPH -> skipParagraph(-1)
            ACTION_NEXT_CHAPTER -> nextChapter()
            ACTION_SETTINGS_CHANGED -> onSettingsChanged()
            ACTION_STOP -> stopReading()
        }
        if (chapter == null && loadJob?.isActive != true && intent?.action !in setOf(ACTION_PLAY_URL, ACTION_PLAY_PENDING_PAGE)) {
            // Nothing to control (e.g. a stale notification button): shut down again.
            stopReading()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        generation++
        scope.cancel()
        tts.stop()
        tts.shutdown()
        abandonFocus()
        unregisterNoisy()
        if (wakeLock.isHeld) wakeLock.release()
        mediaSession.release()
        super.onDestroy()
    }

    private fun whenTtsReady(action: () -> Unit) {
        if (ttsReady) action() else afterTtsReady = action
    }

    // ---------------------------------------------------------------- loading chapters

    private fun load(url: String, html: String?) {
        generation++
        tts.stop()
        playing = false
        loadJob?.cancel()
        settings.lastUrl = url
        requestedUrl = url
        ReaderState.update {
            it.copy(status = ReaderStatus.LOADING, chapterUrl = url, message = null, currentText = "")
        }
        updateNotification()

        val previous = chapter
        loadJob = scope.launch {
            val result = if (html != null) {
                withContext(Dispatchers.Default) { runCatching { ChapterParser.parseHtml(html, url, settings.nextMode) } }
            } else {
                takePrefetch(url)?.await() ?: fetch(url, referrer = previous?.url)
            }

            val loaded = result.getOrElse { error ->
                showError(describe(error))
                return@launch
            }
            if (loaded.paragraphs.isEmpty()) {
                showError(getString(R.string.error_no_text))
                return@launch
            }
            if (previous != null && html == null &&
                (loaded.url == previous.url || loaded.textFingerprint == previous.textFingerprint)
            ) {
                // The site redirected back to the same page or to an index: the novel has ended.
                finish(getString(R.string.message_no_more_chapters))
                return@launch
            }
            start(loaded)
        }
    }

    /** Saved (offline) chapters are used first; otherwise the chapter is downloaded. */
    private suspend fun fetch(url: String, referrer: String?): Result<Chapter> =
        withContext(Dispatchers.IO) {
            OfflineStore.get(this@ReaderService).get(url)?.let { return@withContext Result.success(it) }
            runCatching { fetcher.fetch(url, settings.nextMode, referrer) }
        }

    private fun takePrefetch(url: String): Deferred<Result<Chapter>>? {
        val (prefetchUrl, deferred) = prefetch ?: return null
        prefetch = null
        return if (prefetchUrl == url) deferred else null.also { deferred.cancel() }
    }

    private fun prefetchNext(current: Chapter) {
        prefetch?.second?.cancel()
        val next = current.nextUrl ?: return
        prefetch = next to scope.async { fetch(next, referrer = current.url) }
    }

    private fun start(loaded: Chapter) {
        val previous = chapter
        chapter = loaded
        if (previous != null && previous.url != loaded.url) {
            // A bookmark on the previous chapter moves along to this one.
            BookmarkStore.get(this).advance(previous.url, loaded.url, loaded.pageTitle.ifBlank { loaded.title })
        }
        val text = buildList {
            if (settings.readTitle && loaded.heading.isNotBlank()) add(loaded.heading)
            addAll(loaded.paragraphs)
        }.map { TextCleaner.clean(it) }
        pieces = TextChunker.chunk(text, maxPieceLength())
        index = 0
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, loaded.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, hostOf(loaded.url))
                .build(),
        )
        prefetchNext(loaded)
        val next = loaded.nextUrl
        if (next != null && settings.preloadCount > 0) DownloadWorker.preload(this, next, settings.preloadCount)
        resume()
    }

    private fun maxPieceLength(): Int =
        TextToSpeech.getMaxSpeechInputLength().coerceAtMost(3000)

    // ---------------------------------------------------------------- playback

    private fun resume() {
        if (chapter == null || pieces.isEmpty()) return
        if (!requestFocus()) return
        playing = true
        if (!wakeLock.isHeld) wakeLock.acquire(6 * 60 * 60 * 1000L)
        registerNoisy()
        speakFrom(index)
    }

    private fun pause() {
        if (!playing) return
        playing = false
        generation++
        tts.stop()
        if (wakeLock.isHeld) wakeLock.release()
        unregisterNoisy()
        publishState(ReaderStatus.PAUSED)
    }

    private fun speakFrom(start: Int) {
        generation++
        index = start.coerceIn(0, pieces.lastIndex)
        val params = Bundle()
        for (i in index..pieces.lastIndex) {
            val mode = if (i == index) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(pieces[i], mode, params, "$generation:$i")
        }
        publishState(ReaderStatus.PLAYING)
    }

    private fun parseId(id: String): Int? {
        val (gen, i) = id.split(':').takeIf { it.size == 2 } ?: return null
        return if (gen.toIntOrNull() == generation) i.toIntOrNull() else null
    }

    private fun onPieceStarted(id: String) {
        val i = parseId(id) ?: return
        index = i
        publishState(ReaderStatus.PLAYING)
    }

    private fun onPieceDone(id: String) {
        val i = parseId(id) ?: return
        if (playing && i == pieces.lastIndex) onChapterFinished()
    }

    private fun onChapterFinished() {
        chaptersRead++
        if (settings.deleteAfterRead) {
            listOfNotNull(requestedUrl, chapter?.url).forEach { OfflineStore.get(this).remove(it) }
        }
        ReaderState.update { it.copy(chaptersRead = chaptersRead) }
        val limit = settings.chapterLimit
        when {
            !settings.autoContinue -> finish(getString(R.string.message_chapter_done))
            limit in 1..chaptersRead -> finish(getString(R.string.message_limit_reached, chaptersRead))
            else -> nextChapter()
        }
    }

    private fun nextChapter() {
        val current = chapter ?: return
        val next = current.nextUrl
        if (next == null) {
            finish(getString(R.string.message_no_next_found))
            return
        }
        scope.launch {
            // A short pause between chapters sounds more natural.
            if (playing) delay(600)
            load(next, html = null)
        }
    }

    private fun skipParagraph(delta: Int) {
        if (pieces.isEmpty()) return
        val target = (index + delta).coerceIn(0, pieces.lastIndex)
        if (playing) {
            speakFrom(target)
        } else {
            index = target
            publishState(ReaderStatus.PAUSED)
        }
    }

    private fun onSettingsChanged() {
        if (!ttsReady) return
        applyVoiceSettings()
        if (playing) speakFrom(index)
    }

    private fun applyVoiceSettings() {
        tts.setSpeechRate(settings.speechRate)
        tts.setPitch(settings.pitch)
    }

    /** Stops after the current chapter but keeps the notification so the user can continue. */
    private fun finish(message: String) {
        playing = false
        generation++
        tts.stop()
        if (wakeLock.isHeld) wakeLock.release()
        unregisterNoisy()
        if (pieces.isNotEmpty()) index = pieces.lastIndex
        publishState(ReaderStatus.FINISHED, message)
    }

    private fun stopReading() {
        playing = false
        generation++
        loadJob?.cancel()
        prefetch?.second?.cancel()
        prefetch = null
        tts.stop()
        chapter = null
        pieces = emptyList()
        abandonFocus()
        unregisterNoisy()
        if (wakeLock.isHeld) wakeLock.release()
        ReaderState.update { ReaderUiState() }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun showError(message: String) {
        playing = false
        generation++
        if (wakeLock.isHeld) wakeLock.release()
        unregisterNoisy()
        publishState(ReaderStatus.ERROR, message)
    }

    private fun describe(error: Throwable): String = when (error) {
        is HttpStatusException -> getString(R.string.error_http, error.statusCode)
        is java.net.UnknownHostException -> getString(R.string.error_offline)
        is java.net.SocketTimeoutException -> getString(R.string.error_timeout)
        else -> getString(R.string.error_generic, error.message ?: error.javaClass.simpleName)
    }

    private fun publishState(status: ReaderStatus, message: String? = null) {
        val current = chapter
        ReaderState.update {
            it.copy(
                status = status,
                chapterUrl = current?.url ?: it.chapterUrl,
                chapterTitle = current?.title,
                position = if (pieces.isEmpty()) 0 else index + 1,
                total = pieces.size,
                currentText = pieces.getOrNull(index).orEmpty(),
                message = message,
                chaptersRead = chaptersRead,
            )
        }
        val state = when (status) {
            ReaderStatus.PLAYING -> PlaybackStateCompat.STATE_PLAYING
            ReaderStatus.LOADING -> PlaybackStateCompat.STATE_BUFFERING
            ReaderStatus.ERROR -> PlaybackStateCompat.STATE_ERROR
            ReaderStatus.IDLE -> PlaybackStateCompat.STATE_NONE
            else -> PlaybackStateCompat.STATE_PAUSED
        }
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_FAST_FORWARD or PlaybackStateCompat.ACTION_REWIND,
                )
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
        updateNotification()
    }

    // ---------------------------------------------------------------- audio focus

    private fun requestFocus(): Boolean {
        val request = focusRequest ?: AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributesCompat.Builder()
                    .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                    .setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        resumeOnFocusGain = false
                        pause()
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        resumeOnFocusGain = playing
                        pause()
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnFocusGain) {
                        resumeOnFocusGain = false
                        resume()
                    }
                }
            }
            .build()
            .also { focusRequest = it }
        return AudioManagerCompat.requestAudioFocus(audioManager, request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { AudioManagerCompat.abandonAudioFocusRequest(audioManager, it) }
        focusRequest = null
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(
            this, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        unregisterReceiver(noisyReceiver)
        noisyRegistered = false
    }

    // ---------------------------------------------------------------- notification

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val state = ReaderState.state.value
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = when (state.status) {
            ReaderStatus.LOADING -> getString(R.string.status_loading)
            ReaderStatus.ERROR, ReaderStatus.FINISHED -> state.message.orEmpty()
            else -> getString(R.string.status_position, state.position, state.total)
        }
        val playPause = if (playing) {
            NotificationCompat.Action(R.drawable.ic_pause, getString(R.string.action_pause), actionIntent(ACTION_TOGGLE, 1))
        } else {
            NotificationCompat.Action(R.drawable.ic_play, getString(R.string.action_play), actionIntent(ACTION_TOGGLE, 1))
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(state.chapterTitle ?: getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openApp)
            .setDeleteIntent(actionIntent(ACTION_STOP, 5))
            .setOngoing(playing)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(R.drawable.ic_rewind, getString(R.string.action_prev_paragraph), actionIntent(ACTION_PREV_PARAGRAPH, 2))
            .addAction(playPause)
            .addAction(R.drawable.ic_skip_next, getString(R.string.action_next_chapter), actionIntent(ACTION_NEXT_CHAPTER, 3))
            .addAction(R.drawable.ic_stop, getString(R.string.action_stop), actionIntent(ACTION_STOP, 4))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode, Intent(this, ReaderService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun hostOf(url: String): String = runCatching { URI(url).host.removePrefix("www.") }.getOrDefault("")
}
