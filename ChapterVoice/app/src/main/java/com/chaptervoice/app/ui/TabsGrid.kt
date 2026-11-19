package com.chaptervoice.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.chaptervoice.app.R
import com.chaptervoice.app.browser.BrowserTab
import com.chaptervoice.app.browser.TabManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/** A picture of a page flying between full size and its card. */
private class Zoom(val tab: BrowserTab, val toCard: Boolean)

/**
 * The tab overview: a grid of cards with a picture of each page.
 * - Opening it, the current page shrinks into its card; picking a card grows it back.
 * - Swipe a card sideways to close it (with Undo); the others close the gap.
 * - Hold a card, then drag it to move it.
 */
@Composable
internal fun TabsGrid(
    tabs: TabManager,
    readerTabId: Long,
    reading: Boolean,
    onOpen: (Long) -> Unit,
    onNewTab: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val snackbar = remember { SnackbarHostState() }
    val closedText = stringResource(R.string.tab_closed)
    val undoText = stringResource(R.string.undo)

    var gridOrigin by remember { mutableStateOf(Offset.Zero) }
    var gridSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    /** Where each card's picture is, in the grid's own coordinates. */
    val cardBounds = remember { mutableStateMapOf<Long, Rect>() }

    // ---------------------------------------------------------------- zoom
    var zoom by remember { mutableStateOf<Zoom?>(tabs.find(tabs.activeId)?.takeIf { it.thumbnail != null }?.let { Zoom(it, toCard = true) }) }
    val zoomProgress = remember { Animatable(0f) }
    val gridAlpha = remember { Animatable(if (zoom != null) 0f else 1f) }

    LaunchedEffect(Unit) {
        val index = tabs.tabs.indexOfFirst { it.id == tabs.activeId }
        // Show the active card with the row above it, so it is never cut at the top.
        if (index >= 0) gridState.scrollToItem((index - index % 2 - 2).coerceAtLeast(0))
        val current = zoom ?: run { gridAlpha.animateTo(1f, tween(180)); return@LaunchedEffect }
        // Wait until the card has been placed, then shrink the page into it.
        withTimeoutOrNull(400) { snapshotFlow { cardBounds[current.tab.id] }.first { it != null } }
        launch { gridAlpha.animateTo(1f, tween(260)) }
        zoomProgress.snapTo(0f)
        zoomProgress.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
        zoom = null
    }

    fun open(tab: BrowserTab) {
        if (tab.thumbnail == null || cardBounds[tab.id] == null) {
            onOpen(tab.id)
            return
        }
        scope.launch {
            zoom = Zoom(tab, toCard = false)
            zoomProgress.snapTo(1f)
            launch { gridAlpha.animateTo(0f, tween(220)) }
            zoomProgress.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
            onOpen(tab.id)
        }
    }

    // ---------------------------------------------------------------- reorder
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var dragStartOffset by remember { mutableStateOf(IntOffset.Zero) }

    fun onReorderDrag(delta: Offset) {
        val id = draggingId ?: return
        dragDelta += delta
        val info = gridState.layoutInfo.visibleItemsInfo
        val dragged = info.firstOrNull { it.key == id } ?: return
        val center = Offset(
            dragStartOffset.x + dragDelta.x + dragged.size.width / 2f,
            dragStartOffset.y + dragDelta.y + dragged.size.height / 2f,
        )
        val target = info.firstOrNull { item ->
            item.key != id &&
                center.x in item.offset.x.toFloat()..(item.offset.x + item.size.width).toFloat() &&
                center.y in item.offset.y.toFloat()..(item.offset.y + item.size.height).toFloat()
        } ?: return
        tabs.move(dragged.index, target.index)
    }

    // ---------------------------------------------------------------- layout
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                gridOrigin = it.positionInRoot()
                gridSize = androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat())
            },
    ) {
        Column(Modifier.fillMaxSize().graphicsLayer { alpha = gridAlpha.value }) {
            Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.tabs_title, tabs.tabs.size),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(onClick = onNewTab) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tab_new))
                }
            }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(tabs.tabs, key = { _, tab -> tab.id }) { _, tab ->
                    val dragging = draggingId == tab.id
                    // While dragged, the card follows the finger even as the grid reorders under it.
                    val currentOffset = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == tab.id }?.offset ?: IntOffset.Zero
                    val translation = if (dragging) {
                        Offset(
                            dragStartOffset.x + dragDelta.x - currentOffset.x,
                            dragStartOffset.y + dragDelta.y - currentOffset.y,
                        )
                    } else {
                        Offset.Zero
                    }
                    val lift by animateFloatAsState(if (dragging) 1.06f else 1f, Motion.press, label = "lift")
                    Box(
                        Modifier
                            .zIndex(if (dragging) 1f else 0f)
                            .then(if (dragging) Modifier else Modifier.animateItem(fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                            .graphicsLayer {
                                translationX = translation.x
                                translationY = translation.y
                                scaleX = lift
                                scaleY = lift
                            },
                    ) {
                        TabCard(
                            tab = tab,
                            active = tab.id == tabs.activeId,
                            reading = reading && tab.id == readerTabId,
                            hidden = zoom?.tab?.id == tab.id,
                            elevated = dragging,
                            onBounds = { cardBounds[tab.id] = it.translate(-gridOrigin) },
                            onTap = { open(tab) },
                            onClose = {
                                tabs.close(tab.id)
                                scope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    val result = snackbar.showSnackbar(closedText, actionLabel = undoText, duration = SnackbarDuration.Short)
                                    if (result == SnackbarResult.ActionPerformed) tabs.reopenLastClosed()
                                }
                            },
                            onReorderStart = {
                                draggingId = tab.id
                                dragDelta = Offset.Zero
                                dragStartOffset = currentOffset
                            },
                            onReorderDrag = { onReorderDrag(it) },
                            onReorderEnd = { draggingId = null },
                        )
                    }
                }
            }
        }

        // The page picture flying between full size and its card.
        zoom?.let { z ->
            val card = cardBounds[z.tab.id]
            val image = z.tab.thumbnail
            if (card != null && image != null) {
                val full = Rect(Offset.Zero, gridSize)
                val rect = lerp(full, card, zoomProgress.value)
                val density = LocalDensity.current
                Image(
                    image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier
                        .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                        .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                        .clip(RoundedCornerShape((14 * zoomProgress.value).dp)),
                )
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }
}

@Composable
private fun TabCard(
    tab: BrowserTab,
    active: Boolean,
    reading: Boolean,
    hidden: Boolean,
    elevated: Boolean,
    onBounds: (Rect) -> Unit,
    onTap: () -> Unit,
    onClose: () -> Unit,
    onReorderStart: () -> Unit,
    onReorderDrag: (Offset) -> Unit,
    onReorderEnd: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val swipe = remember(tab.id) { Animatable(0f) }
    var width by remember { mutableStateOf(1f) }
    val host = hostOf(tab.url)
    val openLabel = stringResource(R.string.tab_open)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { width = it.size.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                translationX = swipe.value
                alpha = if (hidden) 0f else 1f - (abs(swipe.value) / width).coerceIn(0f, 1f) * 0.8f
                rotationZ = swipe.value / width * 6f
            }
            .then(if (elevated) Modifier.shadow(12.dp, RoundedCornerShape(18.dp)) else Modifier)
            .then(if (active) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)) else Modifier)
            .semantics {
                role = Role.Button
                onClick(label = openLabel) { onTap(); true }
            }
            // One gesture handler: tap opens, a sideways swipe closes, holding then dragging moves.
            .pointerInput(tab.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var slop: androidx.compose.ui.input.pointer.PointerInputChange? = null
                    val heldStill = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        true
                    } == null
                    when {
                        heldStill -> {
                            onReorderStart()
                            drag(down.id) { change ->
                                onReorderDrag(change.positionChange())
                                change.consume()
                            }
                            onReorderEnd()
                        }
                        slop != null -> {
                            horizontalDrag(slop!!.id) { change ->
                                val dx = change.positionChange().x
                                scope.launch { swipe.snapTo(swipe.value + dx) }
                                change.consume()
                            }
                            scope.launch {
                                if (abs(swipe.value) > width * 0.35f) {
                                    swipe.animateTo(if (swipe.value > 0) width * 1.4f else -width * 1.4f, tween(160))
                                    onClose()
                                } else {
                                    swipe.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
                                }
                            }
                        }
                        else -> {
                            val up = currentEvent.changes.firstOrNull()
                            if (up != null && !up.pressed && !up.isConsumed) onTap()
                        }
                    }
                }
            },
    ) {
        Column {
            Row(Modifier.padding(start = 10.dp, end = 2.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (host.isEmpty()) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                } else {
                    SiteIcon(host, size = 18.dp)
                }
                Text(
                    tab.title.ifBlank { host.ifBlank { stringResource(R.string.start_page) } },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.tab_close), modifier = Modifier.size(18.dp))
                }
            }
            Box(
                Modifier
                    .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                    .fillMaxWidth()
                    .aspectRatio(0.78f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .onGloballyPositioned { onBounds(it.boundsInRoot()) },
            ) {
                val image = tab.thumbnail
                if (image != null) {
                    Image(image, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (host.isEmpty()) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                        } else {
                            SiteIcon(host, size = 44.dp)
                        }
                    }
                }
                if (reading) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            SoundBars(MaterialTheme.colorScheme.onPrimary)
                            Text(
                                stringResource(R.string.tab_reading_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Three small bars bouncing like a sound meter: "this tab is being read". */
@Composable
internal fun SoundBars(color: androidx.compose.ui.graphics.Color) {
    val transition = rememberInfiniteTransition(label = "soundBars")
    val heights = listOf(0, 180, 360).map { delay ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420, delayMillis = delay, easing = LinearEasing), RepeatMode.Reverse),
            label = "bar",
        )
    }
    Row(Modifier.height(12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        heights.forEach { h ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(h.value)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}
