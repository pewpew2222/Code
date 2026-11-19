package com.chaptervoice.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaptervoice.app.data.ImageCache

/**
 * A book cover: the image the site uses, or a coloured card with the book's name
 * while it loads or when the site has none.
 */
@Composable
internal fun BookCover(name: String, coverUrl: String?, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(coverUrl) { mutableStateOf(coverUrl?.let { ImageCache.cached(it) }) }
    LaunchedEffect(coverUrl) {
        if (coverUrl != null && bitmap == null) bitmap = ImageCache.load(context, coverUrl)
    }
    val shape = RoundedCornerShape(width / 7)
    val image = bitmap
    if (image != null) {
        Image(
            image.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(width, height).clip(shape),
        )
    } else {
        Box(
            modifier
                .size(width, height)
                .clip(shape)
                .background(coverColor(name)),
            contentAlignment = if (width < 64.dp) Alignment.Center else Alignment.BottomStart,
        ) {
            if (width < 64.dp) {
                // Too small for the name: its initials instead ("CMA").
                Text(
                    initials(name),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (width.value / 3.2f).sp,
                    maxLines = 1,
                )
                return@Box
            }
            Text(
                name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (width.value / 7.5f).coerceIn(9f, 18f).sp,
                lineHeight = (width.value / 6.2f).coerceIn(11f, 22f).sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(width / 10),
            )
        }
    }
}

private val COVER_COLORS = listOf(
    Color(0xFF3B4A7A), Color(0xFF6B4A3A), Color(0xFF3A6B5B), Color(0xFF5A3B6E),
    Color(0xFF7A3B4E), Color(0xFF2F5D6E), Color(0xFF5E5A2E), Color(0xFF444C5C),
)

private fun initials(name: String): String =
    name.split(' ', '-', '_').filter { it.firstOrNull()?.isLetterOrDigit() == true }
        .take(3).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

private fun coverColor(name: String) = COVER_COLORS[(name.hashCode() and 0x7fffffff) % COVER_COLORS.size]
