package com.chaptervoice.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chaptervoice.app.R
import com.chaptervoice.app.core.DiscoverItem
import com.chaptervoice.app.core.SiteSearch
import com.chaptervoice.app.data.MultiSearch
import com.chaptervoice.app.data.SiteResults
import kotlinx.coroutines.launch

/** Search engines and other sites that are no novel sites, left out of "search all". */
private val NOT_NOVEL_SITES = Regex("(^|\\.)(google|bing|duckduckgo|yahoo|youtube|reddit|facebook|twitter|x|wikipedia)\\.", RegexOption.IGNORE_CASE)

/** The sites to search: pinned and visited sites, the sites of your books, and known ones. */
internal fun searchableSites(vararg lists: List<String>, limit: Int = 10): List<String> =
    lists.toList().flatten().map { it.removePrefix("www.") }.filter { it.contains('.') && !NOT_NOVEL_SITES.containsMatchIn(it) }
        .distinct().take(limit)

/**
 * Sites in the order to show them: those with the best matches first, then those still
 * searching, then those without matches, and last those that could not be searched.
 */
internal fun orderedResults(results: List<SiteResults>, query: String): List<SiteResults> =
    results.sortedWith(
        compareBy<SiteResults> {
            when {
                it.items.isNotEmpty() -> 0
                it.searching -> 1
                it.failed -> 3
                else -> 2
            }
        }.thenByDescending { r -> r.items.maxOfOrNull { SiteSearch.score(it.title, query) } ?: 0.0 }
            .thenByDescending { it.items.size },
    )

/** Searches all [sites] at once for [query] and shows the books found, best sites first. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchAllScreen(
    query: String,
    sites: List<String>,
    onDismiss: () -> Unit,
    onPick: (DiscoverItem) -> Unit,
    onOpen: (String) -> Unit,
) {
    val context = LocalContext.current
    val results = remember(query, sites) { mutableStateMapOf<String, SiteResults>().apply { sites.forEach { put(it, SiteResults(it)) } } }
    LaunchedEffect(query, sites) {
        sites.forEach { site -> launch { results[site] = MultiSearch.search(context, site, query) } }
    }
    val ordered = orderedResults(sites.mapNotNull { results[it] }, query)
    val found = ordered.filter { it.items.isNotEmpty() }
    val empty = ordered.filter { !it.searching && it.items.isEmpty() && !it.failed }
    val failed = ordered.filter { it.failed }
    val searching = ordered.count { it.searching }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close)) }
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text("”$query”", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (searching > 0) {
                                stringResource(R.string.search_all_progress, sites.size - searching, sites.size)
                            } else {
                                stringResource(R.string.search_all_done, found.sumOf { it.items.size }, found.size)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (searching > 0) {
                    LinearProgressIndicator(
                        progress = { (sites.size - searching).toFloat() / sites.size.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(found, key = { it.site }) { result ->
                        Column(Modifier.animateItem()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SiteIcon(result.site, size = 20.dp)
                                Text(
                                    result.site,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                                Text(
                                    " · ${result.items.size}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { onOpen(SiteSearch.browserUrl(result.site, query)) }) {
                                    Text(stringResource(R.string.search_all_open_site))
                                }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 6.dp, end = 8.dp)) {
                                items(result.items, key = { it.url }) { item -> DiscoverCard(item, onClick = { onPick(item) }) }
                            }
                        }
                    }
                    if (searching > 0) {
                        item(key = "searching") {
                            Row(Modifier.animateItem(), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    ordered.filter { it.searching }.joinToString(", ") { it.site },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 10.dp),
                                )
                            }
                        }
                    }
                    if (empty.isNotEmpty() || failed.isNotEmpty()) {
                        item(key = "rest") {
                            Column(Modifier.animateItem().animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (searching == 0 && found.isEmpty()) {
                                    Text(stringResource(R.string.search_all_nothing), style = MaterialTheme.typography.bodyLarge)
                                }
                                SiteChips(stringResource(R.string.search_all_no_matches), empty, query, onOpen)
                                SiteChips(stringResource(R.string.search_all_failed), failed, query, onOpen)
                                Text(
                                    stringResource(R.string.search_all_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Sites without results, each a chip that opens the site's own search. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SiteChips(label: String, sites: List<SiteResults>, query: String, onOpen: (String) -> Unit) {
    if (sites.isEmpty()) return
    Text(label.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sites.forEach { r ->
            AssistChip(
                onClick = { onOpen(SiteSearch.browserUrl(r.site, query)) },
                label = { Text(r.site) },
                leadingIcon = { SiteIcon(r.site, size = 16.dp) },
            )
        }
    }
}
