package com.chaptervoice.app.ui

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
internal fun DownloadsScreen(onPlay: (String) -> Unit, onClose: (() -> Unit)? = null) {
    val context = LocalContext.current
    val store = remember { OfflineStore.get(context) }
    val entries by store.entries.collectAsStateWithLifecycle()
    val work by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(DownloadWorker.WORK_DOWNLOAD) }
        .collectAsStateWithLifecycle(emptyList())
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val groups = remember(entries) { entries.groupBy { it.host }.toList().sortedBy { it.first } }
    val totalBytes = entries.sumOf { it.bytes }

    Surface(Modifier.fillMaxSize()) {
        Column {
            Header(stringResource(R.string.downloads)) {
                if (entries.isNotEmpty()) {
                    TextButton(onClick = { confirmDeleteAll = true }) { Text(stringResource(R.string.downloads_delete_all)) }
                }
                onClose?.let { CloseButton(it) }
            }
            Text(
                stringResource(R.string.downloads_summary, entries.size, Formatter.formatShortFileSize(context, totalBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
            DownloadStatus(work)
            if (entries.isEmpty()) {
                EmptyText(stringResource(R.string.downloads_empty))
                return@Column
            }
            LazyColumn {
                groups.forEach { (host, list) ->
                    item(key = "h-$host") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SiteIcon(host, size = 28.dp)
                            Text(
                                "$host (${list.size})",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp),
                            )
                            IconButton(onClick = { store.removeHost(host) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.downloads_delete_site))
                            }
                        }
                    }
                    items(list, key = { "c-" + it.url }) { entry -> ChapterRow(entry, onPlay, onDelete = { store.remove(entry.url) }) }
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

@Composable
private fun ChapterRow(entry: OfflineEntry, onPlay: (String) -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onPlay(entry.url) }
            .padding(start = 54.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(entry.title.ifBlank { entry.url }, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete)) }
    }
    HorizontalDivider(Modifier.padding(start = 54.dp))
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
