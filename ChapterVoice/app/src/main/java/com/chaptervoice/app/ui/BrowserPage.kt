package com.chaptervoice.app.ui

import com.chaptervoice.app.core.NextChapterResolver
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.chaptervoice.app.R
import com.chaptervoice.app.browser.BrowserTab
import com.chaptervoice.app.core.BookKey
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The web page of a tab, with:
 * - pull down to reload,
 * - swipe in from the left/right edge to go back/forward (when the phone's own edge
 *   gestures are off; with them on, the phone's back gesture already goes back),
 * - floating chapter buttons (previous, chapter list, next) on chapter pages.
 */
@Composable
internal fun BrowserPage(tab: BrowserTab) {
    var chapterSheet by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        RefreshableWebView(tab)

        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val systemEdges = WindowInsets.systemGestures.getLeft(density, direction) > 0
        if (!systemEdges) {
            EdgeSwipe(left = true, enabled = tab.canGoBack, onTrigger = { tab.goBack() })
            EdgeSwipe(left = false, enabled = tab.canGoForward, onTrigger = { tab.goForward() })
        }

        val links = tab.chapterLinks
        AnimatedVisibility(
            visible = links?.isChapterPage == true,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        ) {
            if (links != null) {
                ChapterButtons(
                    onPrevious = links.previous?.let { { tab.load(it) } },
                    onIndex = { chapterSheet = true },
                    onNext = links.next?.let { { tab.load(it) } },
                )
            }
        }
    }
    if (chapterSheet) ChapterSheet(tab, onDismiss = { chapterSheet = false })
}

/** "Chapter list" sheet: open the list, or jump straight to a chapter number. */
@Composable
private fun ChapterSheet(tab: BrowserTab, onDismiss: () -> Unit) {
    val links = tab.chapterLinks
    val current = NextChapterResolver.chapterNumberIn(tab.title)
    var number by remember { mutableStateOf(current?.toString().orEmpty()) }
    var error by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    fun jump() {
        val target = number.toIntOrNull()?.let { NextChapterResolver.urlForChapter(tab.url, it, current) }
        if (target == null) {
            error = true
            return
        }
        focus.clearFocus()
        onDismiss()
        tab.load(target)
    }

    Sheet(stringResource(R.string.chapter_list), onDismiss) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(18.dp)) {
            ValueRow(stringResource(R.string.open_chapter_list), hostOf(tab.url), onClick = {
                // The page's own chapter list link, else the book's page (the address without the chapter).
                val target = links?.index ?: BookKey.of(tab.url)?.let { tab.url.substringBefore("://") + "://" + it }
                onDismiss()
                if (target != null) tab.load(target)
            })
        }
        Text(
            stringResource(R.string.jump_to_chapter),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = number,
                onValueChange = { value -> number = value.filter { it.isDigit() }.take(6); error = false },
                singleLine = true,
                label = { Text(stringResource(R.string.chapter_number)) },
                isError = error,
                supportingText = if (error) { { Text(stringResource(R.string.jump_failed)) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { jump() }),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { jump() }, enabled = number.isNotEmpty(), modifier = Modifier.padding(start = 12.dp)) {
                Text(stringResource(R.string.go))
            }
        }
        Text(
            stringResource(R.string.jump_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** The tab's WebView inside a pull-to-refresh layout, moved into place when the tab changes. */
@Composable
private fun RefreshableWebView(tab: BrowserTab) {
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val background = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
    AndroidView(
        factory = { context ->
            // SwipeRefreshLayout already holds its spinner as child 0, so the page's
            // container is found by type, never by position.
            PatientRefreshLayout(context).apply {
                addView(FrameLayout(context), ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { layout ->
            val container = layout.pageContainer() ?: return@AndroidView
            val webView = tab.webView
            if (container.getChildAt(0) !== webView) {
                container.removeAllViews()
                (webView.parent as? ViewGroup)?.removeView(webView)
                container.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            layout.setColorSchemeColors(accent)
            layout.setProgressBackgroundColorSchemeColor(background)
            // Only pull to refresh at the very top of the page.
            layout.setOnChildScrollUpCallback { _, _ -> webView.canScrollVertically(-1) || webView.scrollY > 0 || tab.touchInScroller }
            // A long, deliberate pull: the default (64 dp) triggered while just scrolling up.
            val density = layout.resources.displayMetrics.density
            layout.setDistanceToTriggerSync((170 * density).toInt())
            layout.setSlingshotDistance((200 * density).toInt())
            layout.setOnRefreshListener {
                tab.reload()
                // Hide the spinner once the page has loaded (or after 10 s at most). Checked
                // here because a quick reload may never show the page as "loading".
                val started = android.os.SystemClock.uptimeMillis()
                val check = object : Runnable {
                    override fun run() {
                        val elapsed = android.os.SystemClock.uptimeMillis() - started
                        if ((elapsed > 600 && tab.progress >= 100) || elapsed > 10_000 || !layout.isAttachedToWindow) {
                            layout.isRefreshing = false
                        } else {
                            layout.postDelayed(this, 250)
                        }
                    }
                }
                layout.postDelayed(check, 250)
            }
        },
        onRelease = { layout -> layout.pageContainer()?.removeAllViews() },
    )
}

/**
 * Pull to refresh that waits a moment at the start of a touch before taking it, so the
 * page has time to say whether the finger is in a box that scrolls by itself.
 */
private class PatientRefreshLayout(context: android.content.Context) : SwipeRefreshLayout(context) {
    private var downAt = 0L

    override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) downAt = ev.eventTime
        val decided = super.onInterceptTouchEvent(ev)
        return decided && ev.eventTime - downAt >= 100
    }
}

private fun SwipeRefreshLayout.pageContainer(): FrameLayout? =
    (0 until childCount).map { getChildAt(it) }.firstOrNull { it is FrameLayout } as FrameLayout?

/** A thin strip at a screen edge: swipe inwards to go back (left) or forward (right). */
@Composable
private fun BoxScope.EdgeSwipe(left: Boolean, enabled: Boolean, onTrigger: () -> Unit) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val threshold = with(density) { 96.dp.toPx() }

    Box(
        Modifier
            .align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
            .fillMaxHeight()
            .width(18.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { fingerY = it.y },
                    onDragEnd = {
                        if (progress.value >= 1f) onTrigger()
                        scope.launch { progress.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                    },
                    onDragCancel = { scope.launch { progress.animateTo(0f) } },
                ) { change, dx ->
                    change.consume()
                    fingerY = change.position.y
                    scope.launch { progress.snapTo((progress.value + (if (left) dx else -dx) / threshold).coerceIn(0f, 1.25f)) }
                }
            },
    )

    if (progress.value > 0f) {
        val reached = progress.value >= 1f
        val size = 44.dp
        val sizePx = with(density) { size.toPx() }
        val travel = with(density) { 28.dp.toPx() }
        Surface(
            color = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (reached) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            shape = CircleShape,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(if (left) Alignment.TopStart else Alignment.TopEnd)
                .offset {
                    val x = (-sizePx + (sizePx + travel) * progress.value.coerceAtMost(1f)).roundToInt()
                    IntOffset(if (left) x else -x, (fingerY - sizePx / 2).roundToInt())
                }
                .size(size)
                .graphicsLayer {
                    val s = 0.7f + 0.3f * progress.value.coerceAtMost(1f)
                    scaleX = s
                    scaleY = s
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (left) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                )
            }
        }
    }
}

/** Floating ‹ Chapter list › buttons for chapter pages. */
@Composable
private fun ChapterButtons(onPrevious: (() -> Unit)?, onIndex: () -> Unit, onNext: (() -> Unit)?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.chapter_previous))
            }
            TextButton(onClick = onIndex) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.chapter_list), modifier = Modifier.padding(start = 6.dp))
            }
            IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.chapter_next))
            }
        }
    }
}
