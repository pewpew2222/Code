package com.chaptervoice.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chaptervoice.app.AdBlocker
import com.chaptervoice.app.AdBlocker.HostStatus
import com.chaptervoice.app.R
import com.chaptervoice.app.browser.BrowserTab

/** What the current page loads from other sites, with block/allow switches. */
@Composable
internal fun BlockerScreen(
    tab: BrowserTab,
    adBlockEnabled: Boolean,
    onHideElement: () -> Unit,
    onRulesChanged: () -> Unit,
    onReload: () -> Unit,
    onClose: () -> Unit,
) {
    val rulesVersion by AdBlocker.rulesVersion
    var refresh by remember { mutableIntStateOf(0) }
    val pageHost = AdBlocker.hostOf(tab.url)
    val hiddenCount = remember(rulesVersion, pageHost) { AdBlocker.hiddenFor(pageHost).size }

    val rows = remember(rulesVersion, refresh, tab.url) {
        tab.requests.entries
            .filter { !sameSite(it.key, pageHost) }
            .map { Triple(it.key, it.value, AdBlocker.status(it.key)) }
            .sortedWith(compareBy({ !isBlocked(it.third) }, { -it.second }, { it.first }))
    }
    val blockedCount = rows.count { isBlocked(it.third) }

    Surface(Modifier.fillMaxSize()) {
        Column {
            Header(stringResource(R.string.blocker_title)) {
                TextButton(onClick = { refresh++ }) { Text(stringResource(R.string.reload_list)) }
                CloseButton(onClose)
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (!adBlockEnabled) {
                    Text(
                        stringResource(R.string.blocker_disabled),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Text(
                    stringResource(R.string.blocker_summary, pageHost, rows.size, blockedCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onHideElement, enabled = adBlockEnabled) {
                        Text(stringResource(R.string.hide_element))
                    }
                    OutlinedButton(onClick = onReload) { Text(stringResource(R.string.reload)) }
                }
                if (hiddenCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            stringResource(R.string.hidden_elements, hiddenCount),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { AdBlocker.clearHidden(tab.url); onRulesChanged() }) {
                            Text(stringResource(R.string.hidden_elements_reset))
                        }
                    }
                }
                Text(
                    stringResource(R.string.blocker_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            HorizontalDivider()
            if (rows.isEmpty()) {
                EmptyText(stringResource(R.string.blocker_empty))
                return@Column
            }
            LazyColumn {
                items(rows, key = { it.first }) { (host, count, status) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(host, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(statusText(status)) + " · " + stringResource(R.string.requests_count, count),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isBlocked(status)) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = isBlocked(status),
                            onCheckedChange = { block ->
                                if (block) AdBlocker.block(host) else AdBlocker.allow(host)
                                onRulesChanged()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Bar shown over the page while the user picks an element to hide. */
@Composable
internal fun PickBar(selector: String?, onLarger: () -> Unit, onHide: () -> Unit, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.inverseSurface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                if (selector == null) stringResource(R.string.pick_instruction) else stringResource(R.string.pick_selected),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (selector != null) {
                Text(
                    selector,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.inverseOnSurface)
                }
                if (selector != null) {
                    TextButton(onClick = onLarger) {
                        Text(stringResource(R.string.pick_larger), color = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                    FilledTonalButton(onClick = onHide) { Text(stringResource(R.string.pick_hide)) }
                }
            }
        }
    }
}

private fun isBlocked(status: HostStatus) =
    status == HostStatus.BLOCKED_BY_LIST || status == HostStatus.BLOCKED_BY_YOU

private fun statusText(status: HostStatus): Int = when (status) {
    HostStatus.BLOCKED_BY_LIST -> R.string.status_blocked_list
    HostStatus.BLOCKED_BY_YOU -> R.string.status_blocked_you
    HostStatus.ALLOWED_BY_YOU -> R.string.status_allowed_you
    HostStatus.ALLOWED -> R.string.status_allowed
}

/** True for the page's own domain and its subdomains (e.g. cdn.site.com on site.com). */
private fun sameSite(host: String, pageHost: String): Boolean {
    val h = host.removePrefix("www.")
    return pageHost.isNotEmpty() && (h == pageHost || h.endsWith(".$pageHost") || pageHost.endsWith(".$h"))
}
