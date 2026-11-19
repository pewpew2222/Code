package com.chaptervoice.app.ui

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
import com.chaptervoice.app.browser.TabManager
import com.chaptervoice.app.data.BookmarkStore
import com.chaptervoice.app.data.DownloadWorker
import com.chaptervoice.app.data.HistoryStore
import com.chaptervoice.app.data.ReadingPositions
import com.chaptervoice.app.service.ReaderService
import com.chaptervoice.app.service.ReaderState
import com.chaptervoice.app.service.ReaderStatus
import org.json.JSONTokener

private enum class Overlay { NONE, TABS, BOOKMARKS, HISTORY, BLOCKER, DOWNLOADS }

@Composable
fun ReaderScreen(
    settings: AppSettings,
    tabs: TabManager,
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
    var showSettings by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    /** The tab whose page is being read aloud; it follows the reader to new chapters. */
    var readerTabId by remember { mutableLongStateOf(-1L) }

    var addressText by remember(active.id, active.url) { mutableStateOf(active.url) }
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
        val tab = tabs.find(readerTabId) ?: return@LaunchedEffect
        if (state.status != ReaderStatus.IDLE && !BookmarkStore.sameUrl(url, tab.url)) tab.load(url)
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding(),
    ) {
        // ------------------------------------------------------------ toolbar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = addressText,
                onValueChange = { addressText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.address_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    open(normalizeAddress(addressText))
                    focusManager.clearFocus()
                }),
                trailingIcon = if (active.url.isNotEmpty()) {
                    {
                        IconButton(onClick = { bookmarkStore.toggle(active.url, active.title) }) {
                            Icon(
                                painterResource(if (isBookmarked) R.drawable.ic_star else R.drawable.ic_star_border),
                                contentDescription = stringResource(
                                    if (isBookmarked) R.string.bookmark_remove else R.string.bookmark_add,
                                ),
                                tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    null
                },
            )
            TabCountButton(count = tabs.tabs.size) {
                overlay = if (overlay == Overlay.TABS) Overlay.NONE else Overlay.TABS
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
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
                        onClick = { showMenu = false; overlay = Overlay.DOWNLOADS },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.blocker_title)) },
                        enabled = active.url.isNotEmpty(),
                        onClick = { showMenu = false; overlay = Overlay.BLOCKER },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings)) },
                        onClick = { showMenu = false; showSettings = true },
                    )
                }
            }
        }
        if (overlay == Overlay.NONE && active.url.isNotEmpty() && active.progress < 100) {
            LinearProgressIndicator(progress = { active.progress / 100f }, modifier = Modifier.fillMaxWidth())
        }

        // ------------------------------------------------------------ content
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                overlay == Overlay.TABS -> TabsOverview(
                    tabs = tabs.tabs,
                    activeId = tabs.activeId,
                    readerTabId = readerTabId,
                    onSelect = { tabs.select(it); overlay = Overlay.NONE },
                    onClose = { tabs.close(it) },
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
                overlay == Overlay.DOWNLOADS -> DownloadsScreen(
                    onPlay = { url ->
                        overlay = Overlay.NONE
                        readerTabId = -1L // played from storage, not from a tab
                        ReaderService.send(context, ReaderService.ACTION_PLAY_URL, url)
                    },
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
                        readerTabId = active.id
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

        PlayerPanel(
            state = state,
            onReadPage = {
                if (active.url.isNotEmpty()) {
                    readerTabId = active.id
                    readCurrentPage(context, active.webView)
                }
            },
            onGoToPage = {
                val url = state.chapterUrl
                if (url != null) {
                    overlay = Overlay.NONE
                    val tab = tabs.find(readerTabId)
                    if (tab != null) {
                        tabs.select(tab.id)
                        if (!BookmarkStore.sameUrl(tab.url, url)) tab.load(url)
                    } else {
                        // Started from Downloads, or the tab was closed: open the chapter.
                        val target = if (active.url.isEmpty()) active.also { it.load(url) } else tabs.newTab(url)
                        readerTabId = target.id
                    }
                }
            },
            onCommand = { action -> ReaderService.send(context, action) },
        )
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

    if (showDownloadDialog) {
        DownloadDialog(
            onDownload = { count ->
                showDownloadDialog = false
                DownloadWorker.download(context, active.url, count)
                overlay = Overlay.DOWNLOADS
            },
            onDismiss = { showDownloadDialog = false },
        )
    }

    if (showSettings) {
        SettingsDialog(
            settings = settings,
            onChanged = {
                if (state.status != ReaderStatus.IDLE) {
                    ReaderService.send(context, ReaderService.ACTION_SETTINGS_CHANGED)
                }
            },
            onDismiss = { showSettings = false },
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

@Composable
private fun TabCountButton(count: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(
            Modifier
                .size(24.dp)
                .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (count > 99) "∞" else count.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
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
