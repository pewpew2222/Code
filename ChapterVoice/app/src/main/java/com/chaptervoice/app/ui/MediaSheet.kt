package com.chaptervoice.app.ui

import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.FilterChip
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chaptervoice.app.R
import com.chaptervoice.app.browser.BrowserTab
import com.chaptervoice.app.core.MediaFinder
import com.chaptervoice.app.core.MediaItem
import com.chaptervoice.app.data.ImageCache
import com.chaptervoice.app.data.MediaDownloads
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup

/** Gives the page's HTML and the addresses its video players are using right now. */
private const val SCAN_JS = """(function(){
  var playing = [];
  document.querySelectorAll('video').forEach(function(v){ if (v.currentSrc) playing.push(v.currentSrc); });
  return JSON.stringify({html: document.documentElement.outerHTML, playing: playing});
})()"""

/** Looks for videos and GIFs on the tab's page; [onResult] gets them on the main thread. */
internal fun scanMedia(tab: BrowserTab, webView: WebView, onResult: (List<MediaItem>) -> Unit) {
    val url = tab.url
    val network = tab.mediaRequests.toList()
    webView.evaluateJavascript(SCAN_JS) { result ->
        val items = runCatching {
            val json = JSONObject(JSONTokener(result).nextValue() as String)
            val playing = json.optJSONArray("playing")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            MediaFinder.find(Jsoup.parse(json.getString("html"), url), playing, network)
        }.getOrElse { MediaFinder.find(Jsoup.parse("", url), emptyList(), network) }
        onResult(items)
    }
}

/** Lists the page's videos and GIFs to tick and download. */
@Composable
internal fun MediaSheet(tab: BrowserTab, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<MediaItem>?>(null) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    // HLS streams: what they hold (null while checking), and the chosen quality.
    val streams = remember { mutableStateMapOf<String, MediaDownloads.StreamInfo?>() }
    val loadedStreams = remember { mutableStateMapOf<String, Boolean>() }
    val quality = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(tab.url) { scanMedia(tab, tab.webView) { items = it } }
    LaunchedEffect(items) {
        items.orEmpty().filter { it.isHls }.forEach { item ->
            launch {
                streams[item.url] = MediaDownloads.streamInfo(context, item.url, tab.url)
                loadedStreams[item.url] = true
            }
        }
    }
    fun canSave(item: MediaItem) = item.downloadable && (!item.isHls || streams[item.url]?.saveable != false || loadedStreams[item.url] != true)

    Sheet(stringResource(R.string.media_title), onDismiss) {
        // A quality playlist that is already listed under its master playlist is not shown twice.
        val inMasters = streams.values.filterNotNull().flatMap { info -> info.variants.map { it.url } }.toSet()
        val list = items?.filter { it.url !in inMasters }
        when {
            list == null -> Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.media_searching), modifier = Modifier.padding(start = 12.dp))
            }
            list.isEmpty() -> Text(
                stringResource(R.string.media_none),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
            else -> {
                val downloadable = list.filter { canSave(it) }
                val count = downloadable.count { selected[it.url] == true }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pluralStringResource(R.plurals.media_found, list.size, list.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    val all = downloadable.isNotEmpty() && count == downloadable.size
                    TextButton(onClick = { downloadable.forEach { selected[it.url] = !all } }) {
                        Text(stringResource(if (all) R.string.media_select_none else R.string.media_select_all))
                    }
                }
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(list, key = { it.url }) { item ->
                        MediaRow(
                            item = item,
                            pageUrl = tab.url,
                            enabled = canSave(item),
                            stream = streams[item.url],
                            checkingStream = item.isHls && loadedStreams[item.url] != true,
                            quality = quality[item.url],
                            onQuality = { quality[item.url] = it; selected[item.url] = true },
                            checked = selected[item.url] == true && canSave(item),
                            onCheckedChange = { selected[item.url] = it },
                        )
                    }
                }
                if (list.any { !canSave(it) }) {
                    Text(
                        stringResource(R.string.media_stream_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val chosen = downloadable.filter { selected[it.url] == true }
                        val name = tab.title.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().take(80).ifEmpty { "video" }
                        val started = MediaDownloads.enqueue(context, chosen, tab.url, quality, name)
                        Toast.makeText(context, context.resources.getQuantityString(R.plurals.media_started, started, started), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    enabled = count > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (count > 0) pluralStringResource(R.plurals.media_download, count, count) else stringResource(R.string.media_choose))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MediaRow(
    item: MediaItem,
    pageUrl: String,
    enabled: Boolean,
    stream: MediaDownloads.StreamInfo?,
    checkingStream: Boolean,
    quality: String?,
    onQuality: (String) -> Unit,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var size by remember(item.url) { mutableStateOf<Long?>(null) }
    LaunchedEffect(item.url) { if (item.downloadable && !item.isHls) size = MediaDownloads.sizeOf(context, item.url, pageUrl) }
    Column {

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.pressable(onClick = { onCheckedChange(!checked) }) else Modifier)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaThumb(item)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            val type = stringResource(
                when {
                    item.kind == MediaItem.Kind.VIDEO -> R.string.media_video
                    item.kind == MediaItem.Kind.GIF -> R.string.media_gif
                    item.isHls -> R.string.media_hls
                    else -> R.string.media_dash
                },
            )
            val details = listOfNotNull(
                type,
                size?.let { formatSize(it) },
                stream?.duration?.takeIf { it > 0 }?.let { formatDuration(it) },
                if (stream?.encrypted == true) stringResource(R.string.stream_encrypted) else null,
                when {
                    stream?.drm == true -> stringResource(R.string.stream_drm)
                    stream?.live == true -> stringResource(R.string.stream_live)
                    checkingStream -> stringResource(R.string.stream_checking)
                    else -> null
                },
                if (item.width > 0 && item.height > 0) "${item.width}×${item.height}" else null,
                hostOf(item.url).takeIf { it.isNotEmpty() },
            )
            Text(
                details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
    // Qualities of an HLS stream; the best is used unless another is picked.
    val variants = stream?.variants.orEmpty()
    if (enabled && variants.size > 1) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(start = 80.dp, end = 8.dp, bottom = 6.dp),
        ) {
            items(variants, key = { it.url }) { v ->
                FilterChip(
                    selected = (quality ?: variants.first().url) == v.url,
                    onClick = { onQuality(v.url) },
                    label = { Text(v.label) },
                )
            }
        }
    }
    }
}

private fun formatDuration(seconds: Double): String {
    val total = seconds.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}

@Composable
private fun MediaThumb(item: MediaItem) {
    val context = LocalContext.current
    val preview = item.preview
    var bitmap by remember(preview) { mutableStateOf(preview?.let { ImageCache.cached(it) }) }
    LaunchedEffect(preview) { if (preview != null && bitmap == null) bitmap = ImageCache.load(context, preview, maxSize = 200) }
    Box(
        Modifier
            .size(64.dp, 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp, 48.dp)) }
        if (bitmap == null || item.kind == MediaItem.Kind.VIDEO) {
            Text(
                when (item.kind) {
                    MediaItem.Kind.GIF -> "GIF"
                    else -> "▶"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (bitmap != null) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1e9)
    bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1e6)
    else -> String.format("%d kB", (bytes / 1000).coerceAtLeast(1))
}
