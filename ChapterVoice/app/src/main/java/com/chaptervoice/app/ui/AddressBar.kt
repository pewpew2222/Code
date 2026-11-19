package com.chaptervoice.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaptervoice.app.R

/**
 * The address bar. At rest it is a calm pill with the site and its domain; swiping it
 * sideways moves to the neighbouring tab. Tapping it turns it into a text field with
 * the whole address selected.
 */
@Composable
internal fun AddressBar(
    url: String,
    bookmarked: Boolean,
    onGo: (String) -> Unit,
    onToggleBookmark: () -> Unit,
    onSwipe: (Float) -> Unit,
    onSwipeEnd: (velocityHint: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    val host = hostOf(url)

    if (editing) {
        var value by remember(url) { mutableStateOf(TextFieldValue(url, TextRange(0, url.length))) }
        val focus = remember { FocusRequester() }
        var hadFocus by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { focus.requestFocus() }
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            placeholder = { Text(stringResource(R.string.address_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                editing = false
                onGo(value.text)
            }),
            modifier = modifier
                .focusRequester(focus)
                .onFocusChanged {
                    if (it.isFocused) hadFocus = true
                    else if (hadFocus) editing = false
                },
        )
        return
    }

    var lastDx by remember { mutableStateOf(0f) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .height(44.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { onSwipeEnd(lastDx) },
                    onDragCancel = { onSwipeEnd(0f) },
                ) { change, dx ->
                    change.consume()
                    lastDx = dx
                    onSwipe(dx)
                }
            }
            .pressable(onClick = { editing = true }),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (host.isNotEmpty()) {
                SiteIcon(host, size = 18.dp)
            } else {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            // The domain slides when it changes, e.g. when swiping to another tab.
            AnimatedContent(
                targetState = host,
                transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                label = "domain",
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            ) { shown ->
                Text(
                    shown.ifEmpty { stringResource(R.string.address_hint) },
                    color = if (shown.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = if (shown.isEmpty()) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (url.isNotEmpty()) {
                IconButton(onClick = onToggleBookmark, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painterResource(if (bookmarked) R.drawable.ic_star else R.drawable.ic_star_border),
                        contentDescription = stringResource(if (bookmarked) R.string.bookmark_remove else R.string.bookmark_add),
                        tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** The tab counter; the number rolls up or down when tabs are opened or closed. */
@Composable
internal fun TabCounter(count: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(
            Modifier
                .size(24.dp)
                .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically { if (up) -it else it } + fadeOut())
                },
                label = "tabCount",
            ) { shown ->
                Text(if (shown > 99) "∞" else shown.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
