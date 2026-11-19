package com.chaptervoice.app.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.chaptervoice.app.data.BookStore
import com.chaptervoice.app.data.BookmarkStore
import com.chaptervoice.app.data.OfflineStore
import com.chaptervoice.app.data.ReadingPositions
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
                    when (nav.section) {
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
                        )
                        Section.SETTINGS -> SettingsScreen(settings = settings, onChanged = { settingsChanged() })
                    }
                }

                if (reading) {
                    MiniPlayer(state = state, onOpen = { nav.playerOpen = true })
                }

                NavigationBar(tonalElevation = 0.dp, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    NavItem(nav, Section.LIBRARY, R.drawable.ic_library, R.string.nav_library)
                    NavItem(nav, Section.WEB, R.drawable.ic_web, R.string.nav_web)
                    NavItem(nav, Section.DOWNLOADS, R.drawable.ic_download, R.string.nav_downloads)
                    NavItem(nav, Section.SETTINGS, R.drawable.ic_settings, R.string.nav_settings)
                }
            }

            AnimatedVisibility(
                visible = nav.playerOpen && reading,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
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
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    nav: AppNav,
    section: Section,
    @DrawableRes icon: Int,
    @StringRes label: Int,
) {
    NavigationBarItem(
        selected = nav.section == section,
        onClick = { nav.section = section },
        icon = { Icon(painterResource(icon), contentDescription = null) },
        label = { Text(stringResource(label)) },
    )
}
