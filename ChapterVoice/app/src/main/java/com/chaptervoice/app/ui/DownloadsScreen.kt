package com.chaptervoice.app.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import com.chaptervoice.app.data.BookStore
import com.chaptervoice.app.core.BookNames
import com.chaptervoice.app.core.BookKey
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.chaptervoice.app.R
import com.chaptervoice.app.data.DownloadWorker
import com.chaptervoice.app.data.OfflineEntry
import com.chaptervoice.app.data.OfflineStore
import kotlin.math.roundToInt

/** Saved chapters, grouped by site, plus the state of the current download. */
@Composable
internal fun DownloadsScreen(onPlay: (String) -> Unit, onClose: (() -> Unit)? = null, scrollToTop: Int = 0) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(scrollToTop) { if (scrollToTop > 0) listState.animateScrollToItem(0) }
    val context = LocalContext.current
    val store = remember { OfflineStore.get(context) }
    val entries by store.entries.collectAsStateWithLifecycle()
    val work by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(DownloadWorker.WORK_DOWNLOAD) }
        .collectAsStateWithLifecycle(emptyList())
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val books by remember { BookStore.get(context) }.items.collectAsStateWithLifecycle()
    // One group per book, chapters in reading order (by chapter number).
    val groups = remember(entries, books) {
        entries.groupBy { BookKey.of(it.url) ?: it.host }
            .map { (key, list) ->
                val book = books.firstOrNull { it.key == key }
                DownloadGroup(
                    key = key,
                    name = book?.name ?: BookNames.guess(key, list.first().title),
                    coverUrl = book?.coverUrl,
                    host = list.first().host,
                    chapters = list.sortedWith(compareBy({ chapterNumber(it.title) ?: Int.MAX_VALUE }, { it.savedAt })),
                )
            }
            .sortedBy { it.name.lowercase() }
    }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val totalBytes = entries.sumOf { it.bytes }

    Surface(Modifier.fillMaxSize()) {
        Column {
            Row(
                Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.downloads),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (entries.isNotEmpty()) {
                    TextButton(onClick = { confirmDeleteAll = true }) { Text(stringResource(R.string.downloads_delete_all)) }
                }
                onClose?.let { CloseButton(it) }
            }
            Text(
                stringResource(R.string.downloads_summary, entries.size, Formatter.formatShortFileSize(context, totalBytes)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
            )
            DownloadStatus(work)
            if (entries.isEmpty()) {
                EmptyText(stringResource(R.string.downloads_empty))
                return@Column
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                groups.forEach { group ->
                    val open = group.key in expanded
                    item(key = "b-" + group.key) {
                        Surface(
                            modifier = Modifier.animateItem(),color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
                            Column {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { expanded = if (open) expanded - group.key else expanded + group.key }
                                        .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    BookCover(group.name, group.coverUrl, 48.dp, 66.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            stringResource(
                                                R.string.download_group_info,
                                                group.host,
                                                group.chapters.size,
                                                Formatter.formatShortFileSize(context, group.chapters.sumOf { it.bytes }),
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            chapterRange(group.chapters),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    IconButton(onClick = { group.chapters.forEach { store.remove(it.url) } }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.downloads_delete_book))
                                    }
                                    val rotation by animateFloatAsState(if (open) 180f else 0f, Motion.snappy, label = "chevron")
                                    Icon(
                                        painterResource(R.drawable.ic_chevron_down),
                                        contentDescription = null,
                                        modifier = Modifier.rotate(rotation),
                                    )
                                }
                                AnimatedVisibility(
                                    visible = open,
                                    enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                                    exit = shrinkVertically(tween(200)) + fadeOut(tween(150)),
                                ) {
                                    Column {
                                        group.chapters.forEach { entry ->
                                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                            ChapterRow(entry, onPlay, onDelete = { store.remove(entry.url) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.downloads_delete_all_title)) },
            confirmButton = {
                TextButton(onClick = { confirmDeleteAll = false; store.clear() }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private data class DownloadGroup(
    val key: String,
    val name: String,
    val coverUrl: String?,
    val host: String,
    val chapters: List<OfflineEntry>,
)

private val NUMBER = Regex("\\d+")

/** "Chapter 44: Cut It Into Half" -> 44. */
private fun chapterNumber(title: String): Int? = NUMBER.find(title)?.value?.toIntOrNull()

/** "Kap. 2–5, 44, 93–103" style summary of which chapters are saved. */
@Composable
private fun chapterRange(chapters: List<OfflineEntry>): String {
    val numbers = chapters.mapNotNull { chapterNumber(it.title) }.distinct().sorted()
    if (numbers.isEmpty()) return ""
    val ranges = mutableListOf<String>()
    var start = numbers.first()
    var prev = start
    for (n in numbers.drop(1) + Int.MIN_VALUE) {
        if (n == prev + 1) { prev = n; continue }
        ranges += if (start == prev) "$start" else "$start–$prev"
        start = n
        prev = n
    }
    return stringResource(R.string.download_chapter_range, ranges.joinToString(", "))
}

@Composable
private fun ChapterRow(entry: OfflineEntry, onPlay: (String) -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onPlay(entry.url) }
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_play), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(entry.title.ifBlank { entry.url }, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
        IconButton(onClick = onDelete) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete)) }
    }
}

@Composable
private fun DownloadStatus(work: List<WorkInfo>) {
    val context = LocalContext.current
    val running = work.firstOrNull { it.state == WorkInfo.State.RUNNING }
    val waiting = work.firstOrNull { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
    val finished = work.lastOrNull { it.state == WorkInfo.State.SUCCEEDED }

    val text: String
    var progress: Float? = null
    when {
        running != null -> {
            val done = running.progress.getInt(DownloadWorker.KEY_DONE, 0)
            val total = running.progress.getInt(DownloadWorker.KEY_COUNT, 0)
            text = stringResource(R.string.downloads_running, done, total, running.progress.getString(DownloadWorker.KEY_TITLE).orEmpty())
            progress = if (total > 0) done / total.toFloat() else null
        }
        waiting != null -> text = stringResource(R.string.downloads_waiting)
        finished != null -> {
            val done = finished.outputData.getInt(DownloadWorker.KEY_DONE, 0)
            val total = finished.outputData.getInt(DownloadWorker.KEY_COUNT, 0)
            val error = finished.outputData.getString(DownloadWorker.KEY_ERROR)
            text = if (error != null) stringResource(R.string.downloads_finished_error, done, error)
            else stringResource(R.string.downloads_finished, done, total)
        }
        else -> return
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_download), contentDescription = null)
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                if (running != null || waiting != null) {
                    TextButton(onClick = { DownloadWorker.cancelDownloads(context) }) { Text(stringResource(R.string.cancel)) }
                }
            }
            if (running != null) {
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                }
            }
        }
    }
}

/** Asks how many chapters to download from the current page. */
@Composable
internal fun DownloadDialog(onDownload: (Int) -> Unit, onDismiss: () -> Unit) {
    var count by remember { mutableFloatStateOf(10f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.download_dialog_text, count.roundToInt()))
                Text(
                    count.roundToInt().toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Slider(value = count, onValueChange = { count = it }, valueRange = 1f..50f, steps = 48)
            }
        },
        confirmButton = { TextButton(onClick = { onDownload(count.roundToInt()) }) { Text(stringResource(R.string.download_start)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
