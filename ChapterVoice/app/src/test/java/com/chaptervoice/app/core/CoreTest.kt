package com.chaptervoice.app.core

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextChapterResolverTest {

    @Test
    fun incrementsLastNumberInPath() {
        assertEquals(
            "https://site.com/novel/book-12/chapter-46",
            NextChapterResolver.incrementUrl("https://site.com/novel/book-12/chapter-45"),
        )
    }

    @Test
    fun keepsZeroPaddingAndExtension() {
        assertEquals(
            "https://site.com/n/chapter-010.html",
            NextChapterResolver.incrementUrl("https://site.com/n/chapter-009.html"),
        )
    }

    @Test
    fun incrementsQueryParameterAndIgnoresFragment() {
        assertEquals(
            "https://site.com/read?book=7&ch=100#top",
            NextChapterResolver.incrementUrl("https://site.com/read?book=7&ch=99#top"),
        )
    }

    @Test
    fun ignoresNumbersInHost() {
        assertNull(NextChapterResolver.incrementUrl("https://site123.com/novel/"))
    }

    @Test
    fun prefersRelNextLink() {
        val doc = Jsoup.parse(
            """<html><head><link rel="next" href="/c/abc-xyz"></head>
               <body><a href="/c/other">Next</a></body></html>""",
            "https://site.com/c/start",
        )
        assertEquals("https://site.com/c/abc-xyz", NextChapterResolver.resolve(doc, "https://site.com/c/start", NextMode.AUTO))
    }

    @Test
    fun findsLinkByTextAndClass() {
        val byText = Jsoup.parse("""<a href="/x/2">Next Chapter</a>""", "https://s.com/x/1")
        assertEquals("https://s.com/x/2", NextChapterResolver.findNextLink(byText, "https://s.com/x/1"))

        val byClass = Jsoup.parse("""<a class="btn btn-next" href="/x/abc"><i></i></a>""", "https://s.com/x/1")
        assertEquals("https://s.com/x/abc", NextChapterResolver.findNextLink(byClass, "https://s.com/x/1"))
    }

    @Test
    fun skipsDeadLinksAndFallsBackToIncrement() {
        val doc = Jsoup.parse(
            """<a href="#">Next</a><a href="javascript:void(0)">Next</a>""",
            "https://s.com/ch-5",
        )
        assertEquals("https://s.com/ch-6", NextChapterResolver.resolve(doc, "https://s.com/ch-5", NextMode.AUTO))
        assertNull(NextChapterResolver.resolve(doc, "https://s.com/ch-5", NextMode.LINK))
    }

    @Test
    fun urlIncrementModeIgnoresLinks() {
        val doc = Jsoup.parse("""<a href="/elsewhere">Next</a>""", "https://s.com/ch-5")
        assertEquals("https://s.com/ch-6", NextChapterResolver.resolve(doc, "https://s.com/ch-5", NextMode.URL_INCREMENT))
    }
}

class ContentExtractorTest {

    private val story = (1..8).joinToString("") {
        "<p>This is sentence number $it of the story, and it is long enough to count as real text.</p>"
    }

    @Test
    fun extractsKnownContainerAndDropsNavigation() {
        val html = """
            <html><head><title>Book - Chapter 3</title><script>var x = 1;</script></head>
            <body>
              <nav><a href="/">Home</a><a href="/list">Chapters</a></nav>
              <h1>Chapter 3: The Gate</h1>
              <div id="chapter-content">
                <p>Chapter 3: The Gate</p>
                $story
                <p>« Previous Chapter | Next Chapter »</p>
                <div class="ads">ADVERT</div><script>track()</script>
              </div>
              <div class="comments">Great chapter!</div>
            </body></html>
        """.trimIndent()

        val result = ContentExtractor.extract(Jsoup.parse(html, "https://s.com/c3"))

        assertEquals("Chapter 3: The Gate", result.title)
        assertEquals("This is sentence number 1 of the story, and it is long enough to count as real text.", result.paragraphs.first())
        assertTrue(result.paragraphs.none { it.contains("Next Chapter") })
        assertTrue(result.paragraphs.none { it.contains("track()") })
        assertTrue(result.paragraphs.none { it.contains("Great chapter") })
    }

    @Test
    fun findsUnknownContainerByScoreAndSplitsOnBr() {
        val html = """
            <body>
              <div class="menu"><a href="/a">A link</a> <a href="/b">Another link</a></div>
              <div class="xyz">First line of the story.<br>Second line of the story.<br><br>Third line.
                Next morning, she woke up early.</div>
              <div class="sidebar">Short</div>
            </body>
        """.trimIndent()

        val result = ContentExtractor.extract(Jsoup.parse(html, "https://s.com/c1"))

        assertEquals(
            listOf(
                "First line of the story.",
                "Second line of the story.",
                "Third line. Next morning, she woke up early.",
            ),
            result.paragraphs,
        )
    }

    @Test
    fun removesShareBoxWithItsHeading() {
        // Share box with neutral class names, as on sites where "[class*=share]" does not match.
        val html = """
            <div id="chapter-content">
              $story
              <p>What in the world...?</p>
              <div class="box-x"><div class="t">Share to your friends</div>
                <div class="row">
                  <a href="https://www.facebook.com/sharer/sharer.php?u=x"><i></i></a>
                  <a href="https://x.com/intent/tweet?url=x"><i></i></a>
                  <a href="fb-messenger://share?link=x"><i></i></a>
                </div>
              </div>
              <div class="banner">ADVERTISEMENT</div>
            </div>
        """.trimIndent()

        val result = ContentExtractor.extract(Jsoup.parse(html, "https://s.com/c2"))

        assertEquals("What in the world...?", result.paragraphs.last())
    }

    @Test
    fun dropsShareHeadingEvenWithoutShareLinks() {
        val html = """<div id="chapter-content">$story<p>The end.</p><h4>Share to your friends</h4><button>f</button></div>"""
        val result = ContentExtractor.extract(Jsoup.parse(html, "https://s.com/c2"))
        assertEquals("The end.", result.paragraphs.last())
    }

    @Test
    fun keepsStoryLinesThatStartWithNavigationWords() {
        val html = """<div id="chapter-content">$story<p>Next, he opened the door.</p><p>Next</p></div>"""
        val result = ContentExtractor.extract(Jsoup.parse(html, "https://s.com/c1"))
        assertTrue(result.paragraphs.contains("Next, he opened the door."))
        assertFalse(result.paragraphs.contains("Next"))
    }
}

class TextChunkerTest {

    @Test
    fun keepsShortParagraphsWhole() {
        assertEquals(listOf("a b", "c"), TextChunker.chunk(listOf("a b", "c"), 100))
    }

    @Test
    fun splitsLongParagraphsAtSentencesAndRespectsLimit() {
        val text = "One two three. Four five six! Seven eight nine? " + "word ".repeat(40).trim()
        val chunks = TextChunker.chunk(listOf(text), 30)
        assertTrue(chunks.all { it.length <= 30 })
        assertEquals("One two three. Four five six!", chunks.first())
        assertEquals(text.split(Regex("\\s+")), chunks.flatMap { it.split(" ") })
    }
}
