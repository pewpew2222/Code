package com.chaptervoice.app.data

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/** Covers from picky image servers: redirects, hotlink protection, pages that are not images. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ImageCacheTest {
    private lateinit var server: ServerSocket
    private val png: ByteArray by lazy {
        val out = ByteArrayOutputStream()
        Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }
    private val base get() = "http://127.0.0.1:${server.localPort}"
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** A tiny HTTP server: one request per connection. */
    @Before fun start() {
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use {
                    it.soTimeout = 2000
                    val reader = it.getInputStream().bufferedReader()
                    val path = reader.readLine().orEmpty().split(' ').getOrElse(1) { "/" }
                    var referer: String? = null
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Referer:", ignoreCase = true)) referer = line.substringAfter(':').trim()
                    }
                    var location: String? = null
                    val (code, body) = when (path) {
                        "/plain.png" -> 200 to png
                        "/moved.png" -> { location = "/plain.png"; 302 to ByteArray(0) }
                        "/hotlink.png" -> if (referer?.contains("novel") == true) 200 to png else 403 to ByteArray(0)
                        "/html.png" -> 200 to "<html>challenge</html>".toByteArray()
                        else -> 404 to ByteArray(0)
                    }
                    val head = buildString {
                        append("HTTP/1.1 $code X\r\nContent-Length: ${body.size}\r\nConnection: close\r\n")
                        location?.let { l -> append("Location: $l\r\n") }
                        append("\r\n")
                    }
                    it.getOutputStream().apply { write(head.toByteArray()); write(body); flush() }
                }
            }
        }
    }

    @After fun stop() = server.close()

    @Test fun loadsPlainAndRedirected() = runBlocking {
        assertNotNull(ImageCache.download(context, "$base/plain.png", null))
        assertEquals(20, ImageCache.load(context, "$base/moved.png")?.width)
    }

    @Test fun sendsThePageAsReferrerWhenRefused() = runBlocking {
        assertNull(ImageCache.download(context, "$base/hotlink.png", null))
        assertNotNull(ImageCache.download(context, "$base/hotlink.png", "$base/novel/abc/12"))
    }

    @Test fun ignoresPagesThatAreNotImages() = runBlocking {
        assertNull(ImageCache.load(context, "$base/html.png"))
    }
}
