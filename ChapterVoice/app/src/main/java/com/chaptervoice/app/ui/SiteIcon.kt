package com.chaptervoice.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads site icons once and keeps them on disk and in memory. */
private object FaviconCache {
    private val memory = LruCache<String, ImageBitmap>(200)
    private val failed = HashSet<String>()

    fun cached(host: String): ImageBitmap? = memory.get(host)

    suspend fun load(context: Context, host: String): ImageBitmap? = withContext(Dispatchers.IO) {
        memory.get(host)?.let { return@withContext it }
        if (synchronized(failed) { host in failed }) return@withContext null
        val dir = File(context.cacheDir, "favicons").apply { mkdirs() }
        val file = File(dir, host.replace(Regex("[^a-z0-9.-]"), "_") + ".png")
        val bytes = if (file.exists()) {
            file.readBytes()
        } else {
            download(host)?.also { file.writeBytes(it) }
        }
        val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (bitmap == null || bitmap.width < 8) {
            synchronized(failed) { failed += host }
            return@withContext null
        }
        bitmap.asImageBitmap().also { memory.put(host, it) }
    }

    private fun download(host: String): ByteArray? = runCatching {
        val connection = URL("https://www.google.com/s2/favicons?sz=64&domain=$host").openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            if (connection.responseCode != 200) null else connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

/** The site's icon, or a coloured letter while it loads or if it has none. */
@Composable
internal fun SiteIcon(host: String, size: Dp = 40.dp) {
    val context = LocalContext.current
    var icon by remember(host) { mutableStateOf(FaviconCache.cached(host)) }
    LaunchedEffect(host) {
        if (icon == null && host.isNotEmpty()) icon = FaviconCache.load(context, host)
    }
    val shape = RoundedCornerShape(size / 4)
    val loaded = icon
    if (loaded != null) {
        Box(
            Modifier
                .size(size)
                .clip(shape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Image(loaded, contentDescription = null, modifier = Modifier.padding(size / 8).size(size * 3 / 4))
        }
    } else {
        Box(
            Modifier
                .size(size)
                .clip(shape)
                .background(letterColor(host)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = host.removePrefix("www.").firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45f).sp,
            )
        }
    }
}

private val LETTER_COLORS = listOf(
    Color(0xFF5B6EE1), Color(0xFF7C4DFF), Color(0xFF00897B), Color(0xFF2E7D32),
    Color(0xFFC62828), Color(0xFFEF6C00), Color(0xFF0277BD), Color(0xFF6D4C41),
)

private fun letterColor(host: String): Color = LETTER_COLORS[(host.hashCode() and 0x7fffffff) % LETTER_COLORS.size]
