package com.chaptervoice.app.ui

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassifier
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chaptervoice.app.AppSettings
import com.chaptervoice.app.R
import com.chaptervoice.app.data.Book
import com.chaptervoice.app.data.BookUpdate
import com.chaptervoice.app.data.HistoryEntry
import com.chaptervoice.app.data.QuickSites
import com.chaptervoice.app.data.UpdateChecker
import java.net.URLEncoder

/** Search pages of sites where the format is known; others are searched through Google. */
private val SITE_SEARCH = mapOf(
    "royalroad.com" to "https://www.royalroad.com/fictions/search?title={q}",
    "scribblehub.com" to "https://www.scribblehub.com/?s={q}&post_type=fictionposts",
    "webnovel.com" to "https://www.webnovel.com/search?keywords={q}",
    "archiveofourown.org" to "https://archiveofourown.org/works/search?work_search%5Bquery%5D={q}",
    "fanfiction.net" to "https://www.fanfiction.net/search/?keywords={q}&type=story",
)

private fun siteSearchUrl(host: String, query: String): String {
    val q = URLEncoder.encode(query, "UTF-8")
    return SITE_SEARCH[host]?.replace("{q}", q)
        ?: "https://www.google.com/search?q=" + URLEncoder.encode("site:$host $query", "UTF-8")
}

/**
 * The page of a new tab, for finding something new or getting somewhere fast:
 * search (also on each novel site), a copied link, new chapters of books you have caught
 * up with, your most visited sites, recently closed tabs, and more sites to discover.
 */
@Composable
internal fun NewTabPage(
    settings: AppSettings,
    history: List<HistoryEntry>,
    books: List<Book>,
    updates: Map<String, BookUpdate>,
    recentlyClosed: List<Pair<String, String>>,
    onOpen: (String) -> Unit,
    onReadNewChapter: (String) -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var pinned by remember { mutableStateOf(settings.pinnedSites) }
    var hidden by remember { mutableStateOf(settings.hiddenSites) }
    var discoverOpen by rememberSaveable { mutableStateOf(false) }
    val clipboardHasLink = remember { clipboardLooksLikeLink(context) }

    LaunchedEffect(Unit) { UpdateChecker.checkIfDue(context) }

    // Most visited sites: the hosts with the most visited pages, pinned ones first.
    val visited = remember(history, pinned, hidden) {
        val counts = history.groupingBy { hostOf(it.url) }.eachCount().filterKeys { it.isNotEmpty() && it !in hidden }
        val ranked = counts.entries.sortedByDescending { it.value }.map { it.key }
        (pinned.filter { it !in hidden } + ranked).distinct().take(8)
            .ifEmpty { QuickSites.groups.first().sites.take(8) }
    }
    val searchSites = remember(visited) { (visited + SITE_SEARCH.keys).distinct().take(8) }

    Surface(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------------------------------------------------------------- search
            item {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.newtab_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = null) } }
                    } else {
                        null
                    },
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (query.isNotBlank()) onOpen(normalizeAddress(query)) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                AnimatedVisibility(visible = query.isNotBlank(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = { onOpen(normalizeAddress(query)) },
                                label = { Text(stringResource(R.string.newtab_search_web)) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                        items(searchSites) { host ->
                            FilterChip(
                                selected = false,
                                onClick = { onOpen(siteSearchUrl(host, query.trim())) },
                                label = { Text(host.substringBeforeLast('.')) },
                                leadingIcon = { SiteIcon(host, size = 18.dp) },
                            )
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- copied link
            if (clipboardHasLink) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).pressable(onClick = {
                            readClipboardLink(context)?.let(onOpen)
                        }),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_open_page), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.newtab_open_copied), fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- new chapters
            val withUpdates = books.mapNotNull { book -> updates[book.key]?.let { book to it } }
            if (withUpdates.isNotEmpty()) {
                item { NewTabLabel(stringResource(R.string.newtab_new_chapters)) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(withUpdates, key = { it.first.key }) { (book, update) ->
                            Column(
                                Modifier
                                    .width(108.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .pressable(onClick = { onReadNewChapter(update.firstNewUrl) }),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box {
                                    BookCover(book.name, book.coverUrl, 108.dp, 150.dp)
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                                    ) {
                                        Text(
                                            if (update.newChapters >= UpdateChecker.MAX_COUNT) "${update.newChapters}+" else "+${update.newChapters}",
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        )
                                    }
                                }
                                Text(book.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(update.firstNewTitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- most visited
            item { NewTabLabel(stringResource(R.string.newtab_most_visited)) }
            item {
                SiteGrid(
                    sites = visited,
                    pinned = pinned,
                    onOpen = { onOpen("https://$it") },
                    onTogglePin = { host ->
                        pinned = if (host in pinned) pinned - host else pinned + host
                        settings.pinnedSites = pinned
                    },
                    onHide = { host ->
                        hidden = hidden + host
                        pinned = pinned - host
                        settings.hiddenSites = hidden
                        settings.pinnedSites = pinned
                    },
                )
            }

            // ---------------------------------------------------------------- recently closed
            if (recentlyClosed.isNotEmpty()) {
                item { NewTabLabel(stringResource(R.string.newtab_recently_closed)) }
                items(recentlyClosed.take(5), key = { "closed-" + it.second }) { (title, url) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .pressable(onClick = { onOpen(url) })
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SiteIcon(hostOf(url), size = 28.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(title.ifBlank { url }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(hostOf(url), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- discover
            item {
                val rotation by animateFloatAsState(if (discoverOpen) 180f else 0f, Motion.snappy, label = "discover")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .pressable(onClick = { discoverOpen = !discoverOpen })
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NewTabLabel(stringResource(R.string.newtab_discover), Modifier.weight(1f))
                    Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, modifier = Modifier.rotate(rotation))
                }
            }
            item {
                AnimatedVisibility(visible = discoverOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    var group by rememberSaveable { mutableStateOf(QuickSites.groups.first().id) }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(QuickSites.groups, key = { it.id }) { g ->
                                FilterChip(selected = group == g.id, onClick = { group = g.id }, label = { Text("${g.label} ${g.sites.size}") })
                            }
                        }
                        SiteGrid(
                            sites = QuickSites.groups.first { it.id == group }.sites.sortedBy { it.lowercase() },
                            pinned = pinned,
                            onOpen = { onOpen("https://$it") },
                            onTogglePin = { host ->
                                pinned = if (host in pinned) pinned - host else pinned + host
                                settings.pinnedSites = pinned
                            },
                            onHide = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewTabLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 4.dp),
    )
}

/** Site icons in rows of four. Hold one to pin, unpin or hide it. */
@Composable
private fun SiteGrid(
    sites: List<String>,
    pinned: Set<String>,
    onOpen: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onHide: ((String) -> Unit)?,
) {
    BoxWithConstraints {
        val cell = (maxWidth - 24.dp) / 4
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            sites.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { host -> SiteTile(host, cell, host in pinned, onOpen, onTogglePin, onHide) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteTile(
    host: String,
    width: Dp,
    isPinned: Boolean,
    onOpen: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onHide: ((String) -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .width(width)
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(onClick = { onOpen(host) }, onLongClick = { menu = true })
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box {
                SiteIcon(host, size = 48.dp)
                if (isPinned) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(6.dp), modifier = Modifier.align(Alignment.TopEnd).size(10.dp)) {}
                }
            }
            Text(host.substringBeforeLast('.'), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (isPinned) R.string.site_unpin else R.string.site_pin)) },
                onClick = { menu = false; onTogglePin(host) },
            )
            if (onHide != null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.site_hide)) }, onClick = { menu = false; onHide(host) })
            }
        }
    }
}

/**
 * Whether the clipboard seems to hold a link, without reading it (reading shows a
 * "pasted from clipboard" notice on new Android versions).
 */
private fun clipboardLooksLikeLink(context: Context): Boolean = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    val description = clipboard.primaryClipDescription ?: return false
    if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) && !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        description.classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE
    ) {
        description.getConfidenceScore(TextClassifier.TYPE_URL) > 0.5f
    } else {
        description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)
    }
}.getOrDefault(false)

private fun readClipboardLink(context: Context): String? = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val text = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    android.util.Patterns.WEB_URL.matcher(text).takeIf { it.find() }?.group()?.let { normalizeAddress(it) }
}.getOrNull()
