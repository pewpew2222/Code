package com.chaptervoice.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Springs used across the app, so everything moves the same way. */
internal object Motion {
    /** Quick with a slight overshoot: highlights, selections. */
    val snappy = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
    val snappyDp = spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

    /** A small bounce for presses. */
    val press = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
}

/**
 * Clickable that shrinks slightly while pressed and springs back, so taps feel physical.
 * [ripple] keeps the usual ripple; without it only the scale shows.
 */
internal fun Modifier.pressable(
    role: Role? = Role.Button,
    ripple: Boolean = true,
    onClick: () -> Unit,
): Modifier = this.then(
    Modifier.composedPressable(role, ripple, onClick),
)

@Composable
private fun pressScale(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, Motion.press, label = "press")
    return scale
}

private fun Modifier.composedPressable(role: Role?, ripple: Boolean, onClick: () -> Unit): Modifier =
    composed {
        val source = remember { MutableInteractionSource() }
        val scale = pressScale(source)
        this
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = source,
                indication = if (ripple) LocalIndication.current else null,
                role = role,
                onClick = onClick,
            )
    }

/** Play/pause icon that morphs (scales and fades) when the state changes. */
@Composable
internal fun PlayPauseIcon(playing: Boolean, contentDescription: String, size: androidx.compose.ui.unit.Dp) {
    AnimatedContent(
        targetState = playing,
        transitionSpec = {
            (scaleIn(initialScale = 0.6f, animationSpec = Motion.snappy) + fadeIn()) togetherWith
                (scaleOut(targetScale = 0.6f) + fadeOut())
        },
        label = "playPause",
    ) { isPlaying ->
        Icon(
            painterResource(if (isPlaying) com.chaptervoice.app.R.drawable.ic_pause else com.chaptervoice.app.R.drawable.ic_play),
            contentDescription = contentDescription,
            modifier = Modifier.size(size),
        )
    }
}

internal data class NavEntry(@DrawableRes val icon: Int, val label: String)

/**
 * A floating bottom bar. The highlight slides to the chosen tab with a spring,
 * the chosen icon grows a little and its label turns bold.
 */
@Composable
internal fun FloatingNavBar(entries: List<NavEntry>, selected: Int, onSelect: (Int) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(26.dp),
        shadowElevation = 8.dp,
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)
            .fillMaxWidth()
            .height(62.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp)) {
            val itemWidth = maxWidth / entries.size
            val offset by animateDpAsState(itemWidth * selected, Motion.snappyDp, label = "navHighlight")
            Box(
                Modifier
                    .offset(x = offset)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(21.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            )
            Row(Modifier.fillMaxSize()) {
                entries.forEachIndexed { index, entry ->
                    val isSelected = index == selected
                    val tint by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "navTint",
                    )
                    val iconScale by animateFloatAsState(if (isSelected) 1.08f else 1f, Motion.press, label = "navIcon")
                    val source = remember { MutableInteractionSource() }
                    val press = pressScale(source)
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(21.dp))
                            .selectable(
                                selected = isSelected,
                                interactionSource = source,
                                indication = null,
                                role = Role.Tab,
                                onClick = { onSelect(index) },
                            )
                            .graphicsLayer { scaleX = press; scaleY = press },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painterResource(entry.icon),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                        )
                        Text(
                            entry.label,
                            color = tint,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
