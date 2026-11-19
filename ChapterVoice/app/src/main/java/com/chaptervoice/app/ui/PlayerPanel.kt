package com.chaptervoice.app.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.Switch
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.chaptervoice.app.service.ReaderService
import com.chaptervoice.app.service.ReaderStatus
import com.chaptervoice.app.service.ReaderUiState

@Composable
internal fun PlayerPanel(
    state: ReaderUiState,
    onReadPage: () -> Unit,
    onGoToPage: () -> Unit,
    rulesGlobalOn: Boolean,
    rulesBookOn: Boolean,
    onRulesGlobalChange: (Boolean) -> Unit,
    onRulesBookChange: (Boolean) -> Unit,
    onCommand: (String) -> Unit,
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (state.status == ReaderStatus.IDLE) {
                Button(onClick = onReadPage, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.read_this_page))
                }
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.chapterTitle ?: state.chapterUrl.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = statusLine(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.status == ReaderStatus.ERROR) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.chapterUrl != null) {
                    RulesToggle(rulesGlobalOn, rulesBookOn, onRulesGlobalChange, onRulesBookChange)
                    IconButton(onClick = onGoToPage) {
                        Icon(painterResource(R.drawable.ic_open_page), contentDescription = stringResource(R.string.go_to_reading_page))
                    }
                }
            }
            if (state.status == ReaderStatus.LOADING) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
            } else if (state.currentText.isNotEmpty()) {
                Text(
                    text = state.currentText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlButton(R.drawable.ic_skip_previous, R.string.action_restart_chapter) {
                    onCommand(ReaderService.ACTION_RESTART_CHAPTER)
                }
                ControlButton(R.drawable.ic_rewind, R.string.action_prev_paragraph) {
                    onCommand(ReaderService.ACTION_PREV_PARAGRAPH)
                }
                val playing = state.status == ReaderStatus.PLAYING
                FilledIconButton(
                    onClick = { onCommand(ReaderService.ACTION_TOGGLE) },
                    modifier = Modifier.size(56.dp),
                    enabled = state.status != ReaderStatus.LOADING,
                ) {
                    Icon(
                        painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = stringResource(if (playing) R.string.action_pause else R.string.action_play),
                    )
                }
                ControlButton(R.drawable.ic_fast_forward, R.string.action_next_paragraph) {
                    onCommand(ReaderService.ACTION_NEXT_PARAGRAPH)
                }
                ControlButton(R.drawable.ic_skip_next, R.string.action_next_chapter) {
                    onCommand(ReaderService.ACTION_NEXT_CHAPTER)
                }
                ControlButton(R.drawable.ic_stop, R.string.action_stop) {
                    onCommand(ReaderService.ACTION_STOP)
                }
            }
            TextButton(onClick = onReadPage, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.read_this_page_instead))
            }
        }
    }
}

@Composable
private fun ControlButton(@DrawableRes icon: Int, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(icon), contentDescription = stringResource(label))
    }
}

@Composable
private fun statusLine(state: ReaderUiState): String = when (state.status) {
    ReaderStatus.LOADING -> stringResource(R.string.status_loading)
    ReaderStatus.ERROR, ReaderStatus.FINISHED -> state.message.orEmpty()
    else -> buildString {
        append(stringResource(R.string.status_position, state.position, state.total))
        if (state.chaptersRead > 0) {
            append(" · ")
            append(stringResource(R.string.status_chapters_read, state.chaptersRead))
        }
        state.warning?.let { append(" · ").append(it) }
        state.message?.let { append(" · ").append(it) }
        if (state.rulesOff) append(" · ").append(stringResource(R.string.rules_off_status))
    }
}

/**
 * Quick switches for the text rules: for the whole app and for this book. Turning them
 * off only pauses the rules; nothing is deleted.
 */
@Composable
private fun RulesToggle(
    globalOn: Boolean,
    bookOn: Boolean,
    onGlobalChange: (Boolean) -> Unit,
    onBookChange: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val active = globalOn && bookOn
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painterResource(R.drawable.ic_text_rules),
                contentDescription = stringResource(R.string.rules_toggle),
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                stringResource(R.string.rules_toggle_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).widthIn(max = 280.dp),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rules_global)) },
                trailingIcon = { Switch(checked = globalOn, onCheckedChange = null) },
                onClick = { onGlobalChange(!globalOn) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rules_book)) },
                trailingIcon = { Switch(checked = bookOn, onCheckedChange = null) },
                enabled = globalOn,
                onClick = { onBookChange(!bookOn) },
            )
        }
    }
}
