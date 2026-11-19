package com.chaptervoice.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chaptervoice.app.R
import com.chaptervoice.app.core.DiscoverItem
import com.chaptervoice.app.core.DiscoverParser
import com.chaptervoice.app.data.DiscoverFeed
import com.chaptervoice.app.data.DiscoverState

/** Books from all sites, taken in turn from each so one site does not fill the row. */
internal fun interleaved(state: DiscoverState, limit: Int = 20): List<DiscoverItem> {
    val lists = state.sites.mapNotNull { state.items[it] }.filter { it.isNotEmpty() }
    val result = mutableListOf<DiscoverItem>()
    var index = 0
    while (result.size < limit && lists.any { index < it.size }) {
        lists.forEach { if (index < it.size && result.size < limit) result += it[index] }
        index++
    }
    return result
}

/**
 * "Discover more": a row of popular books from your sites that slides sideways,
 * with "Show all" for a larger view.
 */
@Composable
internal fun DiscoverRow(
    state: DiscoverState,
    onPick: (DiscoverItem) -> Unit,
    onShowAll: () -> Unit,
    onRetry: () -> Unit,
) {
    val shown = remember(state) { interleaved(state) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.discover_title).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(top = 4.dp),
            )
            if (shown.isNotEmpty()) {
                TextButton(onClick = onShowAll) { Text(stringResource(R.string.discover_show_all)) }
            }
        }
        AnimatedContent(
            targetState = when {
                shown.isNotEmpty() -> 0
                state.loading || state.sites.isEmpty() -> 1
                else -> 2
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "discover",
        ) { mode ->
            when (mode) {
                0 -> LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 8.dp),
                ) {
                    items(shown, key = { it.url }) { item -> DiscoverCard(item, onClick = { onPick(item) }) }
                }
                1 -> LoadingCovers()
                else -> Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.discover_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.discover_retry)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverCard(item: DiscoverItem, onClick: () -> Unit, modifier: Modifier = Modifier.width(104.dp)) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .pressable(onClick = onClick),
    ) {
        BookCover(item.title, item.coverUrl, 104.dp, 148.dp)
        Text(
            item.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)) {
            SiteIcon(item.site, size = 14.dp)
            Text(
                item.site,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** Grey covers that breathe while the lists load. */
@Composable
private fun LoadingCovers() {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.alpha(pulse)) {
        repeat(4) {
            Column {
                Box(Modifier.size(104.dp, 148.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                Box(Modifier.padding(top = 8.dp).size(80.dp, 10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
            }
        }
    }
}

/** A book's preview: cover, description and tags, with buttons to open it or listen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiscoverPreview(
    item: DiscoverItem,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onReadFirst: (String) -> Unit,
) {
    val context = LocalContext.current
    var page by remember(item.url) { mutableStateOf<DiscoverParser.BookPage?>(null) }
    var loading by remember(item.url) { mutableStateOf(true) }
    LaunchedEffect(item.url) {
        page = DiscoverFeed.bookPage(context, item.url)
        loading = false
    }
    val summary = item.summary.ifEmpty { page?.summary.orEmpty() }
    val firstChapter = page?.firstChapterUrl

    Sheet(item.title, onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BookCover(item.title, item.coverUrl, 96.dp, 136.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SiteIcon(item.site, size = 16.dp)
                    Text(item.site, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
                }
                if (item.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.tags.forEach { tag ->
                            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(8.dp)) {
                                Text(tag, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        when {
            summary.isNotEmpty() -> Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
            )
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.discover_loading_info), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
            }
            else -> Text(stringResource(R.string.discover_no_summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onDismiss(); onOpen(item.url) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.discover_open_page), maxLines = 1)
            }
            Button(
                onClick = { firstChapter?.let { onDismiss(); onReadFirst(it) } },
                enabled = firstChapter != null,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.discover_read_first), maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** The larger view: every suggested book, filtered by site. */
@Composable
internal fun DiscoverAll(
    state: DiscoverState,
    onDismiss: () -> Unit,
    onPick: (DiscoverItem) -> Unit,
    onRefresh: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            var site by rememberSaveable { mutableStateOf<String?>(null) }
            val sites = state.sites.filter { !state.items[it].isNullOrEmpty() }
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close)) }
                    Text(stringResource(R.string.discover_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    if (state.loading) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.discover_refresh)) }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    item { FilterChip(selected = site == null, onClick = { site = null }, label = { Text(stringResource(R.string.discover_all)) }) }
                    items(sites, key = { it }) { s ->
                        FilterChip(
                            selected = site == s,
                            onClick = { site = s },
                            leadingIcon = { SiteIcon(s, size = 16.dp) },
                            label = { Text(s) },
                        )
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    (if (site == null) sites else listOf(site!!)).forEach { s ->
                        if (site == null) {
                            item(key = "header-$s", span = { GridItemSpan(maxLineSpan) }) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                    SiteIcon(s, size = 18.dp)
                                    val label = DiscoverParser.sourceFor(s).label
                                    Text(
                                        if (label.isEmpty()) s else "$s · $label",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                        items(state.items[s].orEmpty(), key = { "$s|${it.url}" }) { item ->
                            DiscoverCard(item, onClick = { onPick(item) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    val failed = state.failed.filter { state.items[it].isNullOrEmpty() }
                    if (failed.isNotEmpty() && site == null) {
                        item(key = "failed", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(R.string.discover_couldnt_read, failed.joinToString(", ")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
