package com.chaptervoice.app.core

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MediaFinderTest {
    @Test fun findsVideosGifsLinksAndRequests() {
        val html = """
            <head><meta property="og:video" content="https://cdn.site.com/og.mp4"></head>
            <video src="/v/clip.mp4" poster="/v/clip.jpg" width="640" height="360"></video>
            <video><source src="/live/index.m3u8" type="application/x-mpegURL"></video>
            <img src="/img/funny.gif" width="200"><img src="/img/photo.jpg">
            <img data-src="/img/lazy.gif" src="data:image/gif;base64,AAAA">
            <a href="/files/movie.webm"><img src="/files/movie.jpg"></a>
            <a href="https://i.imgur.com/abc.gifv">imgur</a>
            <a href="/page.html">page</a>
        """.trimIndent()
        val items = MediaFinder.find(
            Jsoup.parse(html, "https://site.com/post/1"),
            playing = listOf("https://site.com/v/clip.mp4"),
            loaded = listOf("https://cdn.site.com/net.mp4?token=1", "https://site.com/img/funny.gif"),
        )
        assertEquals(
            listOf(
                "https://site.com/v/clip.mp4",
                "https://site.com/live/index.m3u8",
                "https://site.com/img/funny.gif",
                "https://site.com/img/lazy.gif",
                "https://site.com/files/movie.webm",
                "https://i.imgur.com/abc.mp4",
                "https://cdn.site.com/og.mp4",
                "https://cdn.site.com/net.mp4?token=1",
            ),
            items.map { it.url },
        )
        assertEquals("https://site.com/v/clip.jpg", items[0].preview)
        assertEquals(640, items[0].width)
        assertFalse(items[1].downloadable)
        assertEquals(MediaItem.Kind.GIF, items[2].kind)
        assertEquals("https://site.com/files/movie.jpg", items[4].preview)
        assertEquals("net.mp4", items[7].fileName)
    }

    @Test fun fileNames() {
        assertEquals("clip.mp4", MediaItem("https://a.com/x/clip.mp4?s=1", MediaItem.Kind.VIDEO).fileName)
        assertEquals("video.mp4", MediaItem("https://a.com/stream/video", MediaItem.Kind.VIDEO).fileName)
        assertEquals("my_cat.gif", MediaItem("https://a.com/my%20cat.gif", MediaItem.Kind.GIF).fileName)
    }
}
