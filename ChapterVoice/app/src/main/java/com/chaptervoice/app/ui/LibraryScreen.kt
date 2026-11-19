package com.chaptervoice.app.ui

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chaptervoice.app.R
import com.chaptervoice.app.data.Book
import com.chaptervoice.app.data.Bookmark
import com.chaptervoice.app.data.BookmarkStore
import com.chaptervoice.app.data.OfflineEntry
import com.chaptervoice.app.data.QuickSites
import com.chaptervoice.app.data.ReadingPosition

/** The start of the app: books to continue, bookmarks, downloads and sites to find new books. */
@Composable
internal fun LibraryScreen(
    books: List<Book>,
    positions: List<ReadingPosition>,
    bookmarks: List<Bookmark>,
    downloads: List<OfflineEntry>,
    onContinue: (Book) -> Unit,
    onRemoveBook: (Book) -> Unit,
    onOpenUrl: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val context = LocalContext.current
    fun positionOf(book: Book) = positions.firstOrNull { BookmarkStore.sameUrl(it.url, book.lastChapterUrl) }

    Surface(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.library), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    FilledTonalIconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.address_hint))
                    }
                }
            }

            if (books.isEmpty()) {
                item {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(22.dp)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.library_welcome_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.library_welcome_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = onSearch) { Text(stringResource(R.string.library_find_book)) }
                        }
                    }
                }
            } else {
                item { SectionLabel(stringResource(R.string.continue_listening)) }
                item(key = "first-" + books.first().key) {
                    val first = books.first()
                    Box(Modifier.animateItem()) {
                        ContinueCard(first, positionOf(first), onContinue = { onContinue(first) }, onRemove = { onRemoveBook(first) })
                    }
                }
                items(books.drop(1), key = { it.key }) { book ->
                    Box(Modifier.animateItem()) {
                        BookRow(book, positionOf(book), onContinue = { onContinue(book) }, onRemove = { onRemoveBook(book) })
                    }
                }
            }

            if (bookmarks.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.bookmarks), Modifier.padding(top = 8.dp)) }
                items(bookmarks.take(5), key = { "bm-" + it.url }) { bookmark ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenUrl(bookmark.url) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SiteIcon(hostOf(bookmark.url), size = 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(bookmark.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(hostOf(bookmark.url), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (downloads.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.downloads_offline), Modifier.padding(top = 8.dp)) }
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(18.dp),
                        onClick = onOpenDownloads,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(painterResource(R.drawable.ic_download), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                stringResource(
                                    R.string.downloads_card,
                                    downloads.size,
                                    downloads.map { it.host }.distinct().size,
                                    Formatter.formatShortFileSize(context, downloads.sumOf { it.bytes }),
                                ),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item { SectionLabel(stringResource(R.string.find_new_books), Modifier.padding(top = 8.dp)) }
            item {
                val sites = QuickSites.groups.first().sites.take(7)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    (sites.map { it as String? } + listOf(null)).chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { site ->
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { if (site != null) onOpenUrl("https://$site") else onSearch() }
                                        .padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (site != null) {
                                        SiteIcon(site, size = 52.dp)
                                    } else {
                                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(52.dp)) {
                                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, contentDescription = null) }
                                        }
                                    }
                                    Text(
                                        site?.substringBefore('.') ?: stringResource(R.string.more),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun ContinueCard(book: Book, position: ReadingPosition?, onContinue: () -> Unit, onRemove: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        onClick = onContinue,
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book.name, book.coverUrl, 76.dp, 104.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(book.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.lastChapterTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                ProgressLine(position)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(onClick = onContinue, modifier = Modifier.size(52.dp)) {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = stringResource(R.string.continue_listening))
                }
                BookMenu(onRemove)
            }
        }
    }
}

@Composable
private fun BookRow(book: Book, position: ReadingPosition?, onContinue: () -> Unit, onRemove: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        onClick = onContinue,
    ) {
        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book.name, book.coverUrl, 44.dp, 60.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(book.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(book.lastChapterTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ProgressLine(position)
            }
            BookMenu(onRemove)
        }
    }
}

@Composable
private fun ProgressLine(position: ReadingPosition?) {
    if (position == null || position.total <= 0) return
    LinearProgressIndicator(
        progress = { (position.index + 1) / position.total.toFloat() },
        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
        drawStopIndicator = {},
        gapSize = 0.dp,
    )
    Text(
        stringResource(R.string.status_position, position.index + 1, position.total),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BookMenu(onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.library_remove)) }, onClick = { open = false; onRemove() })
        }
    }
}
