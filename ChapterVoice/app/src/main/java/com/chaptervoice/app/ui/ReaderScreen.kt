package com.chaptervoice.app.ui

import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Surface
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaptervoice.app.AppSettings
import com.chaptervoice.app.R
import com.chaptervoice.app.browser.BrowserTab
import com.chaptervoice.app.core.BookKey
import com.chaptervoice.app.browser.TabManager
import com.chaptervoice.app.data.BookmarkStore
import com.chaptervoice.app.data.DownloadWorker
import com.chaptervoice.app.data.HistoryStore
import com.chaptervoice.app.data.ReadingPositions
import com.chaptervoice.app.service.ReaderService
import com.chaptervoice.app.service.ReaderState
import com.chaptervoice.app.service.ReaderStatus
import org.json.JSONTokener

private enum class Overlay { NONE, TABS, BOOKMARKS, HISTORY, BLOCKER }

@Composable
fun ReaderScreen(
    settings: AppSettings,
    tabs: TabManager,
    nav: AppNav,
    incomingUrl: String?,
    onIncomingUrlHandled: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val state by ReaderState.state.collectAsStateWithLifecycle()
    val bookmarkStore = remember { BookmarkStore.get(context) }
    val bookmarks by bookmarkStore.items.collectAsStateWithLifecycle()
    val positionStore = remember { ReadingPositions.get(context) }
    val positions by positionStore.items.collectAsStateWithLifecycle()
    val historyStore = remember { HistoryStore.get(context) }
    val history by historyStore.items.collectAsStateWithLifecycle()

    val active = tabs.active
    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }
    var showMenu by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var bookRulesKey by remember { mutableStateOf<String?>(null) }

    // Swiping the address bar slides the page towards the neighbouring tab.
    val scope = rememberCoroutineScope()
    val pageOffset = remember { Animatable(0f) }
    var pageWidth by remember { mutableStateOf(1f) }

    fun swipePage(dx: Float) {
        if (overlay != Overlay.NONE) return
        val direction = if (pageOffset.value + dx < 0) 1 else -1
        // Resistance when there is no tab on that side.
        val factor = if (tabs.neighbour(direction) != null) 1f else 0.25f
        scope.launch { pageOffset.snapTo(pageOffset.value + dx * factor) }
    }

    fun finishPageSwipe() {
        scope.launch {
            val direction = if (pageOffset.value < 0) 1 else -1
            val target = tabs.neighbour(direction)
            if (target != null && kotlin.math.abs(pageOffset.value) > pageWidth * 0.25f) {
                active.captureThumbnail()
                pageOffset.animateTo(-direction * pageWidth, tween(140))
                tabs.select(target.id)
                pageOffset.snapTo(direction * pageWidth)
                pageOffset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
            } else {
                pageOffset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
            }
        }
    }
    val isBookmarked = active.url.isNotEmpty() && bookmarks.any { BookmarkStore.sameUrl(it.url, active.url) }

    fun open(url: String, inNewTab: Boolean = false) {
        overlay = Overlay.NONE
        if (inNewTab) tabs.newTab(url) else active.load(url)
    }

    BackHandler(enabled = overlay != Overlay.NONE) { overlay = Overlay.NONE }
    BackHandler(enabled = overlay == Overlay.NONE && tabs.pickTabId == active.id) { tabs.cancelPick() }
    BackHandler(enabled = overlay == Overlay.NONE && active.canGoBack) { active.goBack() }

    LaunchedEffect(incomingUrl) {
        if (incomingUrl != null) {
            open(incomingUrl, inNewTab = active.url.isNotEmpty())
            onIncomingUrlHandled()
        }
    }

    // Follow the reader in its tab when it moves on to the next chapter.
    LaunchedEffect(state.chapterUrl) {
        val url = state.chapterUrl ?: return@LaunchedEffect
        val tab = tabs.find(nav.readerTabId) ?: return@LaunchedEffect
        if (state.status != ReaderStatus.IDLE && !BookmarkStore.sameUrl(url, tab.url)) tab.load(url)
    }

    fun readThisPage() {
        if (active.url.isNotEmpty()) {
            nav.readerTabId = active.id
            readCurrentPage(context, active.webView)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ------------------------------------------------------------ toolbar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AddressBar(
                url = active.url,
                bookmarked = isBookmarked,
                onGo = { open(normalizeAddress(it)) },
                onToggleBookmark = { bookmarkStore.toggle(active.url, active.title) },
                onSwipe = { dx -> swipePage(dx) },
                onSwipeEnd = { finishPageSwipe() },
                modifier = Modifier.weight(1f),
            )
            TabCounter(count = tabs.tabs.size) {
                if (overlay == Overlay.TABS) {
                    overlay = Overlay.NONE
                } else {
                    active.captureThumbnail()
                    overlay = Overlay.TABS
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.read_this_page)) },
                        enabled = active.url.isNotEmpty(),
                        onClick = { showMenu = false; readThisPage() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tab_new)) },
                        onClick = { showMenu = false; overlay = Overlay.NONE; tabs.newTab() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.bookmarks)) },
                        onClick = { showMenu = false; overlay = Overlay.BOOKMARKS },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history)) },
                        onClick = { showMenu = false; overlay = Overlay.HISTORY },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.forward)) },
                        enabled = active.canGoForward,
                        onClick = { showMenu = false; active.goForward() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reload)) },
                        enabled = active.url.isNotEmpty(),
                        onClick = { showMenu = false; active.reload() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.download_chapters)) },
                        enabled = active.url.isNotEmpty(),
                        onClick = { showMenu = false; showDownloadDialog = true },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.downloads)) },
                        onClick = { showMenu = false; nav.section = Section.DOWNLOADS },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.blocker_title)) },
                        enabled = active.url.isNotEmpty(),
                        onClick = { showMenu = false; overlay = Overlay.BLOCKER },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.book_rules_title)) },
                        enabled = state.chapterUrl != null || active.url.isNotEmpty(),
                        onClick = {
                            showMenu = false
                            // The book being read aloud, otherwise the page on screen.
                            bookRulesKey = BookKey.of(state.chapterUrl ?: active.url)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings)) },
                        onClick = { showMenu = false; nav.section = Section.SETTINGS },
                    )
                }
            }
        }
        if (overlay == Overlay.NONE && active.url.isNotEmpty() && active.progress < 100) {
            LinearProgressIndicator(progress = { active.progress / 100f }, modifier = Modifier.fillMaxWidth())
        }

        // ------------------------------------------------------------ content
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { pageWidth = it.size.width.toFloat().coerceAtLeast(1f) }
                .graphicsLayer {
                    translationX = pageOffset.value
                    alpha = 1f - (kotlin.math.abs(pageOffset.value) / pageWidth * 0.5f).coerceIn(0f, 0.5f)
                },
        ) {
            when {
                overlay == Overlay.TABS -> TabsGrid(
                    tabs = tabs,
                    readerTabId = nav.readerTabId,
                    reading = state.status != ReaderStatus.IDLE,
                    onOpen = { tabs.select(it); overlay = Overlay.NONE },
                    onNewTab = { tabs.newTab(); overlay = Overlay.NONE },
                )
                overlay == Overlay.BOOKMARKS -> BookmarksList(
                    bookmarks = bookmarks,
                    onOpen = { open(it) },
                    onOpenInNewTab = { open(it, inNewTab = true) },
                    onDelete = { bookmarkStore.remove(it) },
                    onClose = { overlay = Overlay.NONE },
                )
                overlay == Overlay.HISTORY -> HistoryList(
                    history = history,
                    onOpen = { open(it) },
                    onDelete = { historyStore.remove(it) },
                    onClear = { historyStore.clear() },
                    onClose = { overlay = Overlay.NONE },
                )
                overlay == Overlay.BLOCKER -> BlockerScreen(
                    tab = active,
                    adBlockEnabled = settings.adBlock,
                    onHideElement = { overlay = Overlay.NONE; tabs.startPick(active) },
                    onRulesChanged = { tabs.applyAdCss(active) },
                    onReload = { overlay = Overlay.NONE; active.reload() },
                    onClose = { overlay = Overlay.NONE },
                )
                active.url.isEmpty() -> StartPage(
                    bookmarks = bookmarks,
                    positions = positions,
                    onOpen = { open(it) },
                    onContinue = { url ->
                        // Open the chapter here and read on from the saved paragraph.
                        open(url)
                        nav.readerTabId = active.id
                        ReaderService.send(context, ReaderService.ACTION_PLAY_URL, url)
                    },
                    onForgetPosition = { positionStore.remove(it) },
                )
                else -> Column {
                    if (tabs.pickTabId == active.id) {
                        PickBar(
                            selector = tabs.pickSelector,
                            onLarger = { tabs.pickLarger() },
                            onHide = { tabs.confirmPick() },
                            onCancel = { tabs.cancelPick() },
                        )
                    }
                    ActiveWebView(active)
                }
            }
        }

        // Read the page on screen, unless it is the one already being read.
        val readingThisPage = state.status != ReaderStatus.IDLE && state.chapterUrl != null &&
            BookmarkStore.sameUrl(state.chapterUrl.orEmpty(), active.url)
        if (overlay == Overlay.NONE && active.url.isNotEmpty() && !readingThisPage) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = { readThisPage() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.read_this_page))
                }
            }
        }
    }

    tabs.linkMenuUrl?.let { link ->
        AlertDialog(
            onDismissRequest = { tabs.linkMenuUrl = null },
            title = { Text(link, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { tabs.linkMenuUrl = null; tabs.newTab(link) }) {
                    Text(stringResource(R.string.open_in_new_tab))
                }
            },
            dismissButton = {
                TextButton(onClick = { tabs.linkMenuUrl = null; tabs.newTab(link, activate = false) }) {
                    Text(stringResource(R.string.open_in_background_tab))
                }
            },
        )
    }

    bookRulesKey?.let { key ->
        BookRulesDialog(
            settings = settings,
            bookKey = key,
            onChanged = {
                if (state.status != ReaderStatus.IDLE) ReaderService.send(context, ReaderService.ACTION_SETTINGS_CHANGED)
            },
            onDismiss = { bookRulesKey = null },
        )
    }

    if (showDownloadDialog) {
        DownloadDialog(
            onDownload = { count ->
                showDownloadDialog = false
                DownloadWorker.download(context, active.url, count)
                nav.section = Section.DOWNLOADS
            },
            onDismiss = { showDownloadDialog = false },
        )
    }

}

/** Shows the active tab's WebView, moving it into place when the tab changes. */
@Composable
private fun ActiveWebView(tab: BrowserTab) {
    AndroidView(
        factory = { FrameLayout(it) },
        modifier = Modifier.fillMaxSize(),
        update = { container ->
            val webView = tab.webView
            if (container.getChildAt(0) !== webView) {
                container.removeAllViews()
                (webView.parent as? ViewGroup)?.removeView(webView)
                container.addView(
                    webView,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            }
        },
        onRelease = { container -> container.removeAllViews() },
    )
}

/** Reads the page as the browser shows it (works on sites with bot checks and scripts). */
private fun readCurrentPage(context: Context, webView: WebView) {
    val url = webView.url ?: return
    webView.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { result ->
        val html = runCatching { JSONTokener(result).nextValue() as? String }.getOrNull()
        if (html.isNullOrBlank()) {
            ReaderService.send(context, ReaderService.ACTION_PLAY_URL, url)
        } else {
            ReaderState.pendingPage = url to html
            ReaderService.send(context, ReaderService.ACTION_PLAY_PENDING_PAGE)
        }
    }
}

private fun normalizeAddress(input: String): String {
    val text = input.trim()
    return when {
        text.startsWith("http://") || text.startsWith("https://") -> text
        text.contains('.') && !text.contains(' ') -> "https://$text"
        else -> "https://www.google.com/search?q=" + java.net.URLEncoder.encode(text, "UTF-8")
    }
}
