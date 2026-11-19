package com.chaptervoice.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaptervoice.app.AppSettings
import com.chaptervoice.app.R
import com.chaptervoice.app.browser.TabManager
import com.chaptervoice.app.core.BookKey
import com.chaptervoice.app.core.BookNames
import com.chaptervoice.app.data.BookStore
import com.chaptervoice.app.data.BookmarkStore
import com.chaptervoice.app.data.OfflineStore
import com.chaptervoice.app.data.ReadingPositions
import com.chaptervoice.app.data.Shortcuts
import com.chaptervoice.app.service.ReaderService
import com.chaptervoice.app.service.ReaderState
import com.chaptervoice.app.service.ReaderStatus

/** The whole app: the four sections in the bottom bar, the mini player and the full-screen player. */
@Composable
fun AppRoot(
    settings: AppSettings,
    tabs: TabManager,
    incomingUrl: String?,
    onIncomingUrlHandled: () -> Unit,
    continueUrl: String? = null,
    onContinueHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val nav = remember { AppNav() }
    val state by ReaderState.state.collectAsStateWithLifecycle()

    val books by remember { BookStore.get(context) }.items.collectAsStateWithLifecycle()
    val positions by remember { ReadingPositions.get(context) }.items.collectAsStateWithLifecycle()
    val bookmarks by remember { BookmarkStore.get(context) }.items.collectAsStateWithLifecycle()
    val downloads by remember { OfflineStore.get(context) }.entries.collectAsStateWithLifecycle()

    val rulesSwitches by AppSettings.rulesSwitches.collectAsStateWithLifecycle()
    val readingBook = state.chapterUrl?.let { BookKey.of(it) }
    val rulesGlobalOn = remember(rulesSwitches) { settings.textRulesEnabled }
    val rulesBookOn = remember(rulesSwitches, readingBook) { readingBook == null || settings.bookRulesEnabled(readingBook) }
    val reading = state.status != ReaderStatus.IDLE
    var bookRulesKey by remember { mutableStateOf<String?>(null) }

    fun settingsChanged() {
        if (reading) ReaderService.send(context, ReaderService.ACTION_SETTINGS_CHANGED)
    }

    /** Opens [url] in the browser: in the current tab when it is empty, otherwise in a new one. */
    fun openInBrowser(url: String) {
        val active = tabs.active
        if (active.url.isEmpty()) active.load(url) else tabs.newTab(url)
        nav.section = Section.WEB
    }

    /** Shows the page being read, in the tab that follows the reader. */
    fun goToReadingPage() {
        val url = state.chapterUrl ?: return
        val tab = tabs.find(nav.readerTabId)
        if (tab != null) {
            tabs.select(tab.id)
            if (!BookmarkStore.sameUrl(tab.url, url)) tab.load(url)
        } else {
            val active = tabs.active
            val target = if (active.url.isEmpty()) active.also { it.load(url) } else tabs.newTab(url)
            nav.readerTabId = target.id
        }
        nav.playerOpen = false
        nav.section = Section.WEB
    }

    // Books read before the library existed: bring them in from saved positions and downloads.
    LaunchedEffect(Unit) {
        val store = BookStore.get(context)
        positions.forEach { position ->
            val key = BookKey.of(position.url) ?: return@forEach
            store.addIfMissing(key, BookNames.guess(key, position.title), position.url, position.title, position.updatedAt)
        }
        downloads.groupBy { BookKey.of(it.url) }.forEach { (key, entries) ->
            if (key == null) return@forEach
            val first = entries.minByOrNull { it.title.let { t -> Regex("\\d+").find(t)?.value?.toIntOrNull() ?: Int.MAX_VALUE } } ?: return@forEach
            store.addIfMissing(key, BookNames.guess(key, first.title), first.url, first.title, entries.maxOf { it.savedAt })
        }
    }

    // App icon shortcut "Continue: <book>": read on where it stopped, in the full-screen player.
    LaunchedEffect(continueUrl) {
        val url = continueUrl ?: return@LaunchedEffect
        nav.readerTabId = -1L
        ReaderService.send(context, ReaderService.ACTION_PLAY_URL, url)
        nav.playerOpen = true
        onContinueHandled()
    }

    // Keep the app icon shortcuts pointing at the most recent books.
    LaunchedEffect(books) { Shortcuts.update(context, books) }

    LaunchedEffect(incomingUrl) {
        if (incomingUrl != null) nav.section = Section.WEB
    }

    BackHandler(enabled = nav.playerOpen) { nav.playerOpen = false }
    BackHandler(enabled = !nav.playerOpen && nav.section != Section.LIBRARY && nav.section != Section.WEB) {
        nav.section = Section.LIBRARY
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = nav.section,
                        transitionSpec = {
                            // Slide a little in the direction of the tab, and fade.
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (slideInHorizontally(tween(260)) { it / 10 * dir } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(200)) { -it / 10 * dir } + fadeOut(tween(160)))
                        },
                        label = "section",
                    ) { section ->
                    when (section) {
                        Section.LIBRARY -> LibraryScreen(
                            books = books,
                            positions = positions,
                            bookmarks = bookmarks,
                            downloads = downloads,
                            onContinue = { book ->
                                nav.readerTabId = -1L
                                ReaderService.send(context, ReaderService.ACTION_PLAY_URL, book.lastChapterUrl)
                                nav.playerOpen = true
                            },
                            onRemoveBook = { BookStore.get(context).remove(it.key) },
                            onOpenUrl = { openInBrowser(it) },
                            onSearch = {
                                if (tabs.active.url.isNotEmpty()) tabs.newTab()
                                nav.section = Section.WEB
                            },
                            onOpenDownloads = { nav.section = Section.DOWNLOADS },
                            scrollToTop = nav.reselects[Section.LIBRARY] ?: 0,
                        )
                        Section.WEB -> ReaderScreen(
                            settings = settings,
                            tabs = tabs,
                            nav = nav,
                            incomingUrl = incomingUrl,
                            onIncomingUrlHandled = onIncomingUrlHandled,
                        )
                        Section.DOWNLOADS -> DownloadsScreen(
                            onPlay = { url ->
                                nav.readerTabId = -1L
                                ReaderService.send(context, ReaderService.ACTION_PLAY_URL, url)
                                nav.playerOpen = true
                            },
                            scrollToTop = nav.reselects[Section.DOWNLOADS] ?: 0,
                        )
                        Section.SETTINGS -> SettingsScreen(
                            settings = settings,
                            onChanged = { settingsChanged() },
                            scrollToTop = nav.reselects[Section.SETTINGS] ?: 0,
                        )
                    }
                    }
                }

                AnimatedVisibility(
                    visible = reading,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    MiniPlayer(state = state, onOpen = { nav.playerOpen = true })
                }

                val sections = Section.entries
                FloatingNavBar(
                    entries = listOf(
                        NavEntry(R.drawable.ic_library, stringResource(R.string.nav_library)),
                        NavEntry(R.drawable.ic_web, stringResource(R.string.nav_web)),
                        NavEntry(R.drawable.ic_download, stringResource(R.string.nav_downloads)),
                        NavEntry(R.drawable.ic_settings, stringResource(R.string.nav_settings)),
                    ),
                    selected = sections.indexOf(nav.section),
                    onSelect = {
                        val chosen = sections[it]
                        if (chosen != nav.section) {
                            nav.section = chosen
                        } else {
                            // Tapping the open section again: a fresh start page in Web, top of the page elsewhere.
                            if (chosen == Section.WEB && tabs.active.url.isNotEmpty()) {
                                val empty = tabs.tabs.firstOrNull { tab -> tab.url.isEmpty() }
                                tabs.active.captureThumbnail()
                                if (empty != null) tabs.select(empty.id) else tabs.newTab()
                            }
                            nav.reselect(chosen)
                        }
                    },
                )
            }

            AnimatedVisibility(
                visible = nav.playerOpen && reading,
                enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
                exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(220)),
            ) {
                NowPlayingScreen(
                    state = state,
                    settings = settings,
                    rulesGlobalOn = rulesGlobalOn,
                    rulesBookOn = rulesBookOn,
                    onRulesGlobalChange = { settings.textRulesEnabled = it; settingsChanged() },
                    onRulesBookChange = { on -> readingBook?.let { settings.setBookRulesEnabled(it, on) }; settingsChanged() },
                    onClose = { nav.playerOpen = false },
                    onGoToPage = { goToReadingPage() },
                    onOpenVoiceSettings = {
                        nav.playerOpen = false
                        nav.section = Section.SETTINGS
                    },
                    onBookRules = { bookRulesKey = readingBook },
                )
            }
        }
    }
    BookRulesFor(settings, bookRulesKey, onChanged = { settingsChanged() }, onDismiss = { bookRulesKey = null })
}

@Composable
private fun BookRulesFor(settings: AppSettings, key: String?, onChanged: () -> Unit, onDismiss: () -> Unit) {
    if (key != null) BookRulesDialog(settings = settings, bookKey = key, onChanged = onChanged, onDismiss = onDismiss)
}
