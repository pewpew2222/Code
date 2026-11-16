package com.chaptervoice.app.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chaptervoice.app.R
import com.chaptervoice.app.browser.BrowserTab
import com.chaptervoice.app.data.Bookmark

@Composable
internal fun TabsOverview(
    tabs: List<BrowserTab>,
    activeId: Long,
    readerTabId: Long,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNewTab: () -> Unit,
    onCloseAll: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.tabs_title, tabs.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCloseAll) { Text(stringResource(R.string.tabs_close_all)) }
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
                    Card(
                        onClick = { onSelect(tab.id) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = tab.title.ifBlank { hostOf(tab.url).ifBlank { stringResource(R.string.start_page) } },
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (tab.id == readerTabId) stringResource(R.string.tab_reading, hostOf(tab.url))
                                    else hostOf(tab.url),
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.bookmarks), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                }
            }
            BookmarkRows(bookmarks, onOpen, onDelete, onOpenInNewTab)
        }
    }
}

/** Shown in a new, empty tab. */
@Composable
internal fun StartPage(bookmarks: List<Bookmark>, onOpen: (String) -> Unit, onDelete: (String) -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column {
            Text(
                stringResource(R.string.bookmarks),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            BookmarkRows(bookmarks, onOpen, onDelete, onOpenInNewTab = null)
        }
    }
}

@Composable
private fun BookmarkRows(
    bookmarks: List<Bookmark>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenInNewTab: ((String) -> Unit)?,
) {
    if (bookmarks.isEmpty()) {
        Text(
            stringResource(R.string.bookmarks_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    LazyColumn {
        items(bookmarks, key = { it.url }) { bookmark ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(bookmark.url) }
                    .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_star),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(bookmark.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        hostOf(bookmark.url),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (onOpenInNewTab != null) {
                    IconButton(onClick = { onOpenInNewTab(bookmark.url) }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.open_in_new_tab))
                    }
                }
                IconButton(onClick = { onDelete(bookmark.url) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.bookmark_remove))
                }
            }
            HorizontalDivider(Modifier.padding(start = 52.dp))
        }
    }
}

private fun hostOf(url: String): String =
    runCatching { Uri.parse(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
