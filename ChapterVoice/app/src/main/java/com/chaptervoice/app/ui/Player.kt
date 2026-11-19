package com.chaptervoice.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.sp
import com.chaptervoice.app.AppSettings
import com.chaptervoice.app.R
import com.chaptervoice.app.service.ReaderService
import com.chaptervoice.app.service.ReaderStatus
import com.chaptervoice.app.service.ReaderUiState
import kotlin.math.roundToInt

// ------------------------------------------------------------------------ mini player

/** The compact player above the bottom bar. Tapping it opens the full-screen player. */
@Composable
internal fun MiniPlayer(state: ReaderUiState, onOpen: () -> Unit) {
    val context = LocalContext.current
    val playing = state.status == ReaderStatus.PLAYING
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClickLabel = stringResource(R.string.player_open), onClick = onOpen),
    ) {
        Column {
            val progress = if (state.total > 0) state.position / state.total.toFloat() else 0f
            if (state.status == ReaderStatus.LOADING) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
            }
            Row(
                Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BookCover(state.bookName ?: state.chapterTitle.orEmpty(), state.coverUrl, 40.dp, 54.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        state.chapterTitle ?: stringResource(R.string.status_loading),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        miniStatus(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.status == ReaderStatus.ERROR) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledIconButton(
                    onClick = { ReaderService.send(context, ReaderService.ACTION_TOGGLE) },
                    enabled = state.status != ReaderStatus.LOADING,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = stringResource(if (playing) R.string.action_pause else R.string.action_play),
                    )
                }
                IconButton(onClick = { ReaderService.send(context, ReaderService.ACTION_NEXT_CHAPTER) }) {
                    Icon(painterResource(R.drawable.ic_skip_next), contentDescription = stringResource(R.string.action_next_chapter))
                }
            }
        }
    }
}

@Composable
private fun miniStatus(state: ReaderUiState): String = when (state.status) {
    ReaderStatus.LOADING -> stringResource(R.string.status_loading)
    ReaderStatus.ERROR, ReaderStatus.FINISHED -> state.message.orEmpty()
    else -> listOfNotNull(
        state.bookName,
        stringResource(R.string.status_position, state.position, state.total),
    ).joinToString(" · ")
}

// ------------------------------------------------------------------------ full-screen player

private val SPEEDS = listOf(0.8f, 1.0f, 1.2f, 1.4f, 1.6f, 1.8f, 2.0f, 2.5f, 3.0f)
private val SLEEP_CHAPTERS = listOf(0, 1, 2, 3, 5, 10)

/** The full-screen player: the book, the paragraphs around the one being read, and all controls. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NowPlayingScreen(
    state: ReaderUiState,
    settings: AppSettings,
    rulesGlobalOn: Boolean,
    rulesBookOn: Boolean,
    onRulesGlobalChange: (Boolean) -> Unit,
    onRulesBookChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onGoToPage: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
) {
    val context = LocalContext.current
    val playing = state.status == ReaderStatus.PLAYING
    fun command(action: String) = ReaderService.send(context, action)
    fun settingsChanged() = command(ReaderService.ACTION_SETTINGS_CHANGED)
    var speed by remember { mutableFloatStateOf(settings.speechRate) }
    var sleepLimit by remember { mutableIntStateOf(settings.chapterLimit) }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---------------------------------------------------------------- header
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = stringResource(R.string.player_close))
                }
                Text(
                    stringResource(R.string.player_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onGoToPage, enabled = state.chapterUrl != null) {
                    Icon(painterResource(R.drawable.ic_open_page), contentDescription = stringResource(R.string.go_to_reading_page))
                }
            }

            // ---------------------------------------------------------------- book
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                BookCover(state.bookName ?: state.chapterTitle.orEmpty(), state.coverUrl, 96.dp, 132.dp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.bookName?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        state.chapterTitle ?: stringResource(R.string.status_loading),
                        fontSize = 21.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val host = hostOf(state.chapterUrl.orEmpty())
                    if (host.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            SiteIcon(host, size = 16.dp)
                            Text(host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- text
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                when (state.status) {
                    ReaderStatus.LOADING -> Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    ReaderStatus.ERROR, ReaderStatus.FINISHED -> Box(Modifier.padding(20.dp)) {
                        Text(
                            state.message.orEmpty(),
                            color = if (state.status == ReaderStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    else -> Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.previousText.isNotEmpty()) {
                            ReadingText(state.previousText, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 3)
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            ReadingText(state.currentText, MaterialTheme.colorScheme.onSurface, maxLines = 9)
                        }
                        if (state.nextText.isNotEmpty()) {
                            ReadingText(state.nextText, MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4)
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- progress
            Column {
                var dragging by remember { mutableStateOf<Float?>(null) }
                val last = (state.total - 1).coerceAtLeast(1)
                Slider(
                    value = dragging ?: (state.position - 1).coerceAtLeast(0).toFloat(),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { ReaderService.seek(context, it.roundToInt()) }
                        dragging = null
                    },
                    valueRange = 0f..last.toFloat(),
                    enabled = state.total > 1,
                )
                Row {
                    val shown = dragging?.let { it.roundToInt() + 1 } ?: state.position
                    Text(
                        stringResource(R.string.status_position, shown, state.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.minutesLeft > 0) {
                        Text(
                            stringResource(R.string.minutes_left, state.minutesLeft),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---------------------------------------------------------------- controls
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BigControl(R.drawable.ic_skip_previous, R.string.action_restart_chapter) { command(ReaderService.ACTION_RESTART_CHAPTER) }
                BigControl(R.drawable.ic_rewind, R.string.action_prev_paragraph) { command(ReaderService.ACTION_PREV_PARAGRAPH) }
                FilledIconButton(
                    onClick = { command(ReaderService.ACTION_TOGGLE) },
                    enabled = state.status != ReaderStatus.LOADING,
                    modifier = Modifier.size(76.dp),
                ) {
                    Icon(
                        painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = stringResource(if (playing) R.string.action_pause else R.string.action_play),
                        modifier = Modifier.size(34.dp),
                    )
                }
                BigControl(R.drawable.ic_fast_forward, R.string.action_next_paragraph) { command(ReaderService.ACTION_NEXT_PARAGRAPH) }
                BigControl(R.drawable.ic_skip_next, R.string.action_next_chapter) { command(ReaderService.ACTION_NEXT_CHAPTER) }
            }

            // ---------------------------------------------------------------- quick settings
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                MenuChip(label = stringResource(R.string.chip_speed, formatSpeed(speed))) { close ->
                    SPEEDS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(formatSpeed(option) + "×") },
                            onClick = { speed = option; settings.speechRate = option; settingsChanged(); close() },
                        )
                    }
                }
                MenuChip(
                    label = if (sleepLimit == 0) stringResource(R.string.chip_sleep_off)
                    else stringResource(R.string.chip_sleep, sleepLimit),
                ) { close ->
                    SLEEP_CHAPTERS.forEach { count ->
                        DropdownMenuItem(
                            text = {
                                Text(if (count == 0) stringResource(R.string.sleep_off) else stringResource(R.string.setting_limit, count))
                            },
                            onClick = { sleepLimit = count; settings.chapterLimit = count; close() },
                        )
                    }
                }
                FilterChip(
                    selected = false,
                    onClick = onOpenVoiceSettings,
                    label = { Text(stringResource(R.string.chip_voice)) },
                )
                MenuChip(
                    label = stringResource(if (rulesGlobalOn && rulesBookOn) R.string.chip_rules_on else R.string.chip_rules_off),
                    selected = rulesGlobalOn && rulesBookOn,
                ) { _ ->
                    Text(
                        stringResource(R.string.rules_toggle_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).widthIn(max = 260.dp),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rules_global)) },
                        trailingIcon = { Switch(checked = rulesGlobalOn, onCheckedChange = null) },
                        onClick = { onRulesGlobalChange(!rulesGlobalOn) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rules_book)) },
                        trailingIcon = { Switch(checked = rulesBookOn, onCheckedChange = null) },
                        enabled = rulesGlobalOn,
                        onClick = { onRulesBookChange(!rulesBookOn) },
                    )
                }
            }
            TextButton(
                onClick = { command(ReaderService.ACTION_STOP); onClose() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text(stringResource(R.string.player_stop)) }
        }
    }
}

@Composable
private fun ReadingText(text: String, color: androidx.compose.ui.graphics.Color, maxLines: Int) {
    Text(
        text,
        fontFamily = ReadingFont,
        fontSize = 17.sp,
        lineHeight = 27.sp,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun BigControl(@DrawableRes icon: Int, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(52.dp), colors = IconButtonDefaults.iconButtonColors()) {
        Icon(painterResource(icon), contentDescription = stringResource(label), modifier = Modifier.size(28.dp))
    }
}

/** A chip that opens a small menu. */
@Composable
private fun MenuChip(label: String, selected: Boolean = false, content: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = selected, onClick = { open = true }, label = { Text(label) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            content { open = false }
        }
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.roundToInt().toFloat()) "${speed.roundToInt()},0" else "%.1f".format(speed).replace('.', ',')
