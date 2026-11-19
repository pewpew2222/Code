package com.chaptervoice.app.ui

import androidx.compose.animation.core.animateFloatAsState
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.Icons
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .pressable(onClick = onOpen),
    ) {
        Box {
            Row(
                Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BookCover(state.bookName ?: state.chapterTitle.orEmpty(), state.coverUrl, 34.dp, 46.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        state.chapterTitle ?: stringResource(R.string.status_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        miniStatus(state),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.status == ReaderStatus.ERROR) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = { ReaderService.send(context, ReaderService.ACTION_TOGGLE) },
                    enabled = state.status != ReaderStatus.LOADING,
                    modifier = Modifier.size(42.dp),
                ) {
                    PlayPauseIcon(playing, stringResource(if (playing) R.string.action_pause else R.string.action_play), 26.dp)
                }
                IconButton(
                    onClick = { ReaderService.send(context, ReaderService.ACTION_NEXT_CHAPTER) },
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_skip_next), contentDescription = stringResource(R.string.action_next_chapter), modifier = Modifier.size(22.dp))
                }
            }
            // Thin progress line along the bottom edge.
            val progress by animateFloatAsState(
                if (state.total > 0) state.position / state.total.toFloat() else 0f,
                label = "miniProgress",
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
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

/** How long the text stays where the user scrolled it before following the reader again. */
private const val FOLLOW_AFTER_IDLE_MS = 5_000L

/** The full-screen player: the book, the whole chapter text following the reader, and all controls. */
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
    onBookRules: () -> Unit,
) {
    val context = LocalContext.current
    val playing = state.status == ReaderStatus.PLAYING
    fun command(action: String) = ReaderService.send(context, action)
    fun settingsChanged() = command(ReaderService.ACTION_SETTINGS_CHANGED)
    var speed by remember { mutableFloatStateOf(settings.speechRate) }
    var sleepLimit by remember { mutableIntStateOf(settings.chapterLimit) }
    var sheet by remember { mutableStateOf<String?>(null) }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                Box {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.go_to_reading_page)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_open_page), contentDescription = null) },
                            enabled = state.chapterUrl != null,
                            onClick = { menu = false; onGoToPage() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.book_rules_title)) },
                            enabled = state.chapterUrl != null,
                            onClick = { menu = false; onBookRules() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.player_stop)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_stop), contentDescription = null) },
                            onClick = { menu = false; command(ReaderService.ACTION_STOP); onClose() },
                        )
                    }
                }
            }

            // ---------------------------------------------------------------- book
            // Tapping the book and chapter opens the chapter's web page.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .then(if (state.chapterUrl != null) Modifier.pressable(onClick = onGoToPage) else Modifier)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookCover(state.bookName ?: state.chapterTitle.orEmpty(), state.coverUrl, 52.dp, 72.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        state.chapterTitle ?: stringResource(R.string.status_loading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val host = hostOf(state.chapterUrl.orEmpty())
                    Text(
                        listOfNotNull(state.bookName, host.ifEmpty { null }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.chapterUrl != null) {
                    Icon(
                        painterResource(R.drawable.ic_open_page),
                        contentDescription = stringResource(R.string.go_to_reading_page),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // ---------------------------------------------------------------- text
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                when (state.status) {
                    ReaderStatus.LOADING -> Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    ReaderStatus.ERROR, ReaderStatus.FINISHED -> Box(Modifier.padding(20.dp)) {
                        Text(
                            state.message.orEmpty(),
                            color = if (state.status == ReaderStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    else -> ChapterText(state, onReadFrom = { ReaderService.seek(context, it) })
                }
            }

            // ---------------------------------------------------------------- progress
            Column {
                var dragging by remember { mutableStateOf<Float?>(null) }
                val last = (state.total - 1).coerceAtLeast(1)
                Slider(
                    colors = neutralSliderColors(),
                    value = dragging ?: (state.position - 1).coerceAtLeast(0).toFloat(),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { ReaderService.seek(context, it.roundToInt()) }
                        dragging = null
                    },
                    valueRange = 0f..last.toFloat(),
                    enabled = state.total > 1,
                    modifier = Modifier.height(32.dp),
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
                    modifier = Modifier.size(64.dp),
                ) {
                    PlayPauseIcon(playing, stringResource(if (playing) R.string.action_pause else R.string.action_play), 30.dp)
                }
                BigControl(R.drawable.ic_fast_forward, R.string.action_next_paragraph) { command(ReaderService.ACTION_NEXT_PARAGRAPH) }
                BigControl(R.drawable.ic_skip_next, R.string.action_next_chapter) { command(ReaderService.ACTION_NEXT_CHAPTER) }
            }

            // ---------------------------------------------------------------- quick settings (one swipeable row)
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = false,
                    onClick = { sheet = "speed" },
                    label = { Text(stringResource(R.string.chip_speed, formatSpeed(speed))) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_fast_forward), contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                FilterChip(
                    selected = sleepLimit > 0,
                    onClick = { sheet = "sleep" },
                    label = {
                        Text(if (sleepLimit == 0) stringResource(R.string.chip_sleep_off) else stringResource(R.string.chip_sleep, sleepLimit))
                    },
                )
                FilterChip(
                    selected = rulesGlobalOn && rulesBookOn,
                    onClick = { sheet = "rules" },
                    label = { Text(stringResource(if (rulesGlobalOn && rulesBookOn) R.string.chip_rules_on else R.string.chip_rules_off)) },
                )
                FilterChip(
                    selected = false,
                    onClick = onOpenVoiceSettings,
                    label = { Text(stringResource(R.string.chip_voice)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_mic), contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }

    when (sheet) {
        "speed" -> Sheet(stringResource(R.string.label_speed), onDismiss = { sheet = null }) {
            Text(
                formatSpeed(speed) + "×",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Slider(
                colors = neutralSliderColors(),
                value = speed,
                onValueChange = { speed = (it * 10).roundToInt() / 10f },
                onValueChangeFinished = { settings.speechRate = speed; settingsChanged() },
                valueRange = 0.5f..3f,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SPEEDS.forEach { option ->
                    FilterChip(
                        selected = speed == option,
                        onClick = { speed = option; settings.speechRate = option; settingsChanged() },
                        label = { Text(formatSpeed(option) + "×") },
                    )
                }
            }
        }
        "sleep" -> ChoiceSheet(
            title = stringResource(R.string.label_sleep),
            options = SLEEP_CHAPTERS.map { it to if (it == 0) stringResource(R.string.value_off) else stringResource(R.string.setting_limit, it) },
            selected = sleepLimit,
            onSelect = { sleepLimit = it; settings.chapterLimit = it },
            onDismiss = { sheet = null },
        )
        "rules" -> Sheet(stringResource(R.string.group_text_rules), onDismiss = { sheet = null }) {
            Text(
                stringResource(R.string.rules_toggle_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(18.dp)) {
                Column {
                    SwitchRow(stringResource(R.string.rules_global), checked = rulesGlobalOn) { onRulesGlobalChange(it) }
                    RowDivider()
                    SwitchRow(stringResource(R.string.rules_book), checked = rulesBookOn, enabled = rulesGlobalOn) { onRulesBookChange(it) }
                }
            }
        }
    }
}

/**
 * The whole chapter, scrollable. It follows the paragraph being read, except while the
 * user scrolls: then it stays put until the user has left it alone for
 * [FOLLOW_AFTER_IDLE_MS], and a button offers to jump back at once. Tapping a paragraph
 * reads from there.
 */
@Composable
private fun ChapterText(state: ReaderUiState, onReadFrom: (Int) -> Unit) {
    val listState = rememberLazyListState()
    val current = (state.position - 1).coerceAtLeast(0)
    /** When the user last touched or flung the text; 0 = following the reader. */
    var userScrolledAt by remember { mutableLongStateOf(0L) }
    var userDragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val topMargin = with(density) { 48.dp.roundToPx() }

    // Notice the user's own scrolling (drags, and the fling after a drag).
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> userDragging = true
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    userDragging = false
                    userScrolledAt = System.currentTimeMillis()
                }
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            // A fling keeps going after the finger lifts: count its end as the last touch.
            if (!scrolling && userScrolledAt != 0L && !userDragging) userScrolledAt = System.currentTimeMillis()
        }
    }

    // Follow the reader, but only after the user has left the text alone for a while.
    LaunchedEffect(current, userScrolledAt, userDragging, state.pieces.size) {
        if (state.pieces.isEmpty() || userDragging) return@LaunchedEffect
        if (userScrolledAt != 0L) {
            val wait = FOLLOW_AFTER_IDLE_MS - (System.currentTimeMillis() - userScrolledAt)
            if (wait > 0) delay(wait)
            if (listState.isScrollInProgress) return@LaunchedEffect
            userScrolledAt = 0L
        }
        listState.animateScrollToItem(current.coerceIn(0, state.pieces.lastIndex), -topMargin)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(state.pieces) { index, text ->
                val isCurrent = index == current
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable(onClickLabel = stringResource(R.string.read_from_here)) { onReadFrom(index) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        text,
                        fontFamily = ReadingFont,
                        fontSize = 17.sp,
                        lineHeight = 27.sp,
                        color = when {
                            isCurrent -> MaterialTheme.colorScheme.onSurface
                            index < current -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
        if (userScrolledAt != 0L || userDragging) {
            FilledTonalButton(
                onClick = { userDragging = false; userScrolledAt = 0L },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
            ) { Text(stringResource(R.string.back_to_reading)) }
        }
    }
}

@Composable
private fun BigControl(@DrawableRes icon: Int, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(icon), contentDescription = stringResource(label), modifier = Modifier.size(26.dp))
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.roundToInt().toFloat()) "${speed.roundToInt()},0" else "%.1f".format(speed).replace('.', ',')
