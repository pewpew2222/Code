package com.chaptervoice.app.ui

import android.net.Uri
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chaptervoice.app.R
import com.chaptervoice.app.browser.BrowserTab
import com.chaptervoice.app.data.Bookmark
import com.chaptervoice.app.data.HistoryEntry
import com.chaptervoice.app.data.QuickSites
import java.util.Calendar

// ------------------------------------------------------------------------ tabs

@Composable
internal fun TabsOverview(
    tabs: List<BrowserTab>,
    activeId: Long,
    readerTabId: Long,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNewTab: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column {
            Header(stringResource(R.string.tabs_title, tabs.size)) {
                IconButton(onClick = onNewTab) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tab_new))
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tabs, key = { it.id }) { tab ->
                    val isActive = tab.id == activeId
                    val host = hostOf(tab.url)
                    Card(
                        onClick = { onSelect(tab.id) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SiteIcon(host, size = 32.dp)
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                            ) {
                                Text(
                                    text = tab.title.ifBlank { host.ifBlank { stringResource(R.string.start_page) } },
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (tab.id == readerTabId) stringResource(R.string.tab_reading, host) else host,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { onClose(tab.id) }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.tab_close))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------ bookmarks

@Composable
internal fun BookmarksList(
    bookmarks: List<Bookmark>,
    onOpen: (String) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column {
            Header(stringResource(R.string.bookmarks)) { CloseButton(onClose) }
            if (bookmarks.isEmpty()) {
                EmptyText(stringResource(R.string.bookmarks_empty))
                return@Column
            }
            LazyColumn {
                items(bookmarks, key = { it.url }) { bookmark ->
                    PageRow(
                        host = hostOf(bookmark.url),
                        title = bookmark.title,
                        subtitle = hostOf(bookmark.url),
                        onClick = { onOpen(bookmark.url) },
                    ) {
                        IconButton(onClick = { onOpenInNewTab(bookmark.url) }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.open_in_new_tab))
                        }
                        IconButton(onClick = { onDelete(bookmark.url) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.bookmark_remove))
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------ history

@Composable
internal fun HistoryList(
    history: List<HistoryEntry>,
    onOpen: (String) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    val filtered = remember(history, query) {
        val q = query.trim()
        if (q.isEmpty()) history
        else history.filter { it.title.contains(q, ignoreCase = true) || it.url.contains(q, ignoreCase = true) }
    }
    val today = stringResource(R.string.history_today)
    val yesterday = stringResource(R.string.history_yesterday)

    Surface(Modifier.fillMaxSize()) {
        Column {
            Header(stringResource(R.string.history)) {
                if (history.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.history_clear)) }
                }
                CloseButton(onClose)
            }
            SearchField(query, { query = it }, stringResource(R.string.history_search))
            if (filtered.isEmpty()) {
                EmptyText(stringResource(if (history.isEmpty()) R.string.history_empty else R.string.no_matches))
                return@Column
            }
            LazyColumn {
                var lastDay = ""
                filtered.forEach { entry ->
                    val day = dayLabel(context, entry.visitedAt, today, yesterday)
                    if (day != lastDay) {
                        lastDay = day
                        item(key = "day-$day-${entry.visitedAt}") {
                            Text(
                                day,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                    }
                    item(key = "${entry.url}-${entry.visitedAt}") {
                        val host = hostOf(entry.url)
                        PageRow(
                            host = host,
                            title = entry.title.ifBlank { entry.url },
                            subtitle = "$host · " + DateFormat.getTimeFormat(context).format(entry.visitedAt),
                            onClick = { onOpen(entry.url) },
                        ) {
                            IconButton(onClick = { onDelete(entry) }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.history_remove))
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_text)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }) { Text(stringResource(R.string.history_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private fun dayLabel(context: android.content.Context, time: Long, today: String, yesterday: String): String = when {
    DateUtils.isToday(time) -> today
    DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS) -> yesterday
    else -> {
        val sameYear = Calendar.getInstance().get(Calendar.YEAR) ==
            Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.YEAR)
        val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or
            (if (sameYear) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_SHOW_YEAR)
        DateUtils.formatDateTime(context, time, flags)
    }
}

// ------------------------------------------------------------------------ start page

private const val BOOKMARKS_CHIP = "bookmarks"

/** Shown in a new, empty tab: quick navigation to bookmarks and web novel sites. */
@Composable
internal fun StartPage(bookmarks: List<Bookmark>, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(if (bookmarks.isNotEmpty()) BOOKMARKS_CHIP else QuickSites.groups.first().id) }

    val q = query.trim()
    val group = QuickSites.groups.firstOrNull { it.id == selected }
    val shownBookmarks = when {
        q.isNotEmpty() -> bookmarks.filter { it.title.contains(q, true) || it.url.contains(q, true) }
        selected == BOOKMARKS_CHIP -> bookmarks
        else -> emptyList()
    }
    val shownSites = when {
        q.isNotEmpty() -> QuickSites.groups.flatMap { it.sites }.distinct().filter { it.contains(q, true) }
        else -> group?.sites.orEmpty()
    }.sortedBy { it.lowercase() }

    Surface(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                SearchField(query, { query = it }, stringResource(R.string.search_sites))
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = selected == BOOKMARKS_CHIP && q.isEmpty(),
                            onClick = { selected = BOOKMARKS_CHIP; query = "" },
                            label = { Text("★ " + stringResource(R.string.bookmarks) + " ${bookmarks.size}") },
                        )
                    }
                    items(QuickSites.groups, key = { it.id }) { g ->
                        FilterChip(
                            selected = selected == g.id && q.isEmpty(),
                            onClick = { selected = g.id; query = "" },
                            label = { Text("${g.label} ${g.sites.size}") },
                        )
                    }
                }
            }
            item {
                val count = shownBookmarks.size + shownSites.size
                Text(
                    stringResource(R.string.sites_count, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp),
                )
            }
            if (shownBookmarks.isEmpty() && shownSites.isEmpty()) {
                item {
                    EmptyText(
                        stringResource(
                            if (q.isEmpty() && selected == BOOKMARKS_CHIP) R.string.bookmarks_empty else R.string.no_matches,
                        ),
                    )
                }
            }
            groupedCard(shownBookmarks, key = { "b-" + it.url }) { bookmark ->
                PageRow(
                    host = hostOf(bookmark.url),
                    title = bookmark.title,
                    subtitle = hostOf(bookmark.url),
                    onClick = { onOpen(bookmark.url) },
                ) { Chevron() }
            }
            if (shownBookmarks.isNotEmpty() && shownSites.isNotEmpty()) {
                item { Spacer(Modifier.height(12.dp)) }
            }
            groupedCard(shownSites, key = { "s-$it" }) { site ->
                PageRow(host = site, title = site, subtitle = null, onClick = { onOpen("https://$site") }) { Chevron() }
            }
        }
    }
}

/** Items drawn as one rounded card with dividers, like a settings list. */
private fun <T> LazyListScope.groupedCard(list: List<T>, key: (T) -> Any, row: @Composable (T) -> Unit) {
    itemsIndexed(list, key = { _, item -> key(item) }) { index, item ->
        val top = if (index == 0) 16.dp else 0.dp
        val bottom = if (index == list.lastIndex) 16.dp else 0.dp
        Card(
            shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            row(item)
            if (index != list.lastIndex) HorizontalDivider(Modifier.padding(start = 68.dp))
        }
    }
}

// ------------------------------------------------------------------------ shared pieces

@Composable
private fun PageRow(
    host: String,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteIcon(host, size = 40.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 10.dp),
    )
}

@Composable
private fun Header(title: String, actions: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    IconButton(onClick = onClose) {
        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, contentDescription = null, Modifier.size(20.dp)) } }
        } else {
            null
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

internal fun hostOf(url: String): String =
    runCatching { Uri.parse(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
