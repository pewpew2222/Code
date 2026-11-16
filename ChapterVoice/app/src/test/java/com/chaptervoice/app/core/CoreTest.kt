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
    fun novelPhoenixCutsEverythingFromShareHeading() {
        val html = """
            <div class="wrap"><div id="chapter-content">
              $story
              <p>What in the world...?</p>
              <div class="box"><span>Share to your friends</span><button>f</button></div>
              <p>Read the latest chapters first at our site, updated every single day for you!</p>
              <p>Use the navigation buttons below to continue reading the next chapter now.</p>
            </div><p>Outside text after the container that should not be read either.</p></div>
        """.trimIndent()

        val phoenix = ContentExtractor.extract(Jsoup.parse(html, "https://novelphoenix.com/novel/x/chapter-2"))
        assertEquals("What in the world...?", phoenix.paragraphs.last())

        // Other sites are not cut at the share heading.
        val other = ContentExtractor.extract(Jsoup.parse(html, "https://other.com/novel/x/chapter-2"))
        assertTrue(other.paragraphs.last().startsWith("Use the navigation buttons"))
    }

    @Test
    fun dropsTranslatorAndEditorCredits() {
        val html = """
            <div id="chapter-content">
              <p>6</p>
              <p>12 Comments</p>
              <p>Translator: Henyee Translations Editor: Henyee Translations</p>
              <p>TL: Someone</p>
              <p>Translated by Atlas Studios</p>
              <p>【翻译：某人】</p>
              <p>7 pm.</p>
              $story
              <p>Editor: he said the word slowly, tasting it, and the room went quiet.</p>
              ${"<p>More of the story continues here with plenty of words to read.</p>".repeat(6)}
              <p>Proofreader: Jane</p>
            </div>
        """.trimIndent()

        val result = ContentExtractor.extract(Jsoup.parse(html, "https://empirenovel.com/c3"))

        assertEquals("7 pm.", result.paragraphs.first())
        assertTrue(result.paragraphs.none { it.startsWith("Translator") || it.startsWith("TL:") || it.startsWith("Translated by") })
        assertTrue(result.paragraphs.none { it.contains("翻译") || it.startsWith("Proofreader") })
        assertTrue(result.paragraphs.none { it == "6" || it == "12 Comments" })
        // The same words in the middle of the story are kept.
        assertTrue(result.paragraphs.any { it.startsWith("Editor: he said") })
    }

    @Test
    fun keepsStoryLinesThatStartWithStoryWordsLikeEditing() {
        val html = """<div id="chapter-content"><p>Ed was late again.</p><p>Translation magic glowed.</p>$story</div>"""
        val result = ContentExtractor.extract(Jsoup.parse(html, "https://s.com/c1"))
        assertEquals("Ed was late again.", result.paragraphs[0])
        assertEquals("Translation magic glowed.", result.paragraphs[1])
    }

    @Test
    fun usesChapterHeadingNotBookNameAsTitle() {
        // empirenovel: <h1> is the book's name, the chapter heading is the first line of the text.
        val html = """
            <h1>COMPLETE MARTIAL ARTS ATTRIBUTES</h1>
            <div id="chapter-content">
              <p>Chapter 3: Starting My Bugged Life Now!</p>
              <p>6</p>
              <p>Translator: Henyee Translations Editor: Henyee Translations</p>
              <p>7 pm.</p>
              $story
            </div>
        """.trimIndent()

        val result = ContentExtractor.extract(Jsoup.parse(html, "https://empirenovel.com/c3"))

        assertEquals("Chapter 3: Starting My Bugged Life Now!", result.title)
        assertEquals("7 pm.", result.paragraphs.first())
        assertTrue(result.paragraphs.none { it.contains("MARTIAL ARTS") })
    }

    @Test
    fun prefersChapterHeadingElementAfterBookNameHeading() {
        val html = """<h1>My Novel</h1><h2>Ch. 12 - The Duel</h2><div id="chapter-content">$story</div>"""
        val chapter = ChapterParser.parseHtml(html, "https://s.com/c12", NextMode.AUTO)
        assertEquals("Ch. 12 - The Duel", chapter.heading)
        assertEquals("Ch. 12 - The Duel", chapter.title)
    }

    @Test
    fun noHeadingMeansNothingExtraIsReadButPageTitleIsShown() {
        val html = """<html><head><title>My Novel - Read Online</title></head><body><h1>My Novel</h1>
            <div id="chapter-content">$story</div></body></html>"""
        val chapter = ChapterParser.parseHtml(html, "https://s.com/x", NextMode.AUTO)
        assertEquals("", chapter.heading)
        assertEquals("My Novel - Read Online", chapter.title)
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

class HostBlocklistTest {

    private val list = HostBlocklist.parse(
        sequenceOf(
            "# comment",
            "127.0.0.1 localhost",
            "0.0.0.0 0.0.0.0",
            "0.0.0.0 doubleclick.net",
            "0.0.0.0 ads.example.com # trailing comment",
            "tracker.io",
            "",
        ),
    )

    @Test
    fun parsesHostsFileAndPlainLines() {
        assertEquals(3, list.size)
    }

    @Test
    fun blocksDomainAndSubdomains() {
        assertTrue(list.isBlocked("doubleclick.net"))
        assertTrue(list.isBlocked("securepubads.g.doubleclick.net"))
        assertTrue(list.isBlocked("ADS.Example.com."))
        assertTrue(list.isBlocked("x.tracker.io"))
    }

    @Test
    fun allowsEverythingElse() {
        assertFalse(list.isBlocked("example.com"))
        assertFalse(list.isBlocked("novelphoenix.com"))
        assertFalse(list.isBlocked("notdoubleclick.net"))
        assertFalse(list.isBlocked(null))
        assertFalse(list.isBlocked("localhost"))
    }
}

class TextCleanerTest {

    @org.junit.Before
    fun setUp() {
        TextCleaner.dictionary = TextCleaner.Dictionary(
            words = setOf("shit", "damn", "ass", "pow"),
            acronyms = setOf("fbi", "pow", "usa", "cia"),
        )
    }

    @org.junit.After
    fun tearDown() {
        TextCleaner.dictionary = null
    }

    @Test
    fun joinsCapitalCensoredWordsOnlyWhenTheyAreRealWords() {
        assertEquals("What the SHIT!", TextCleaner.clean("What the S.H.I.T!"))
        assertEquals("DAMN.", TextCleaner.clean("D.A.M.N."))
        assertEquals("The F.B.I came.", TextCleaner.clean("The F.B.I came."))
        assertEquals("A P.O.W camp.", TextCleaner.clean("A P.O.W camp."))
        assertEquals("The C.I.A. knew.", TextCleaner.clean("The C.I.A. knew."))
        // Unknown words in capitals stay as they are.
        assertEquals("X.Y.Z", TextCleaner.clean("X.Y.Z"))
    }

    @Test
    fun withoutDictionaryCapitalsAreLeftAlone() {
        TextCleaner.dictionary = null
        assertEquals("S.H.I.T", TextCleaner.clean("S.H.I.T"))
    }

    @Test
    fun removesCensorDotsInsideWords() {
        assertEquals("The ships could succumb.", TextCleaner.clean("The sh.i.p.s could succ.u.m.b."))
        assertEquals("Wang Teng's family", TextCleaner.clean("w.a.n.g Teng's family").replaceFirst("wang", "Wang"))
        assertEquals("time passed very quickly.", TextCleaner.clean("time pa.s.sed very quickly."))
        assertEquals("W.a.n.g Teng".let { TextCleaner.clean(it) }, "Wang Teng")
        assertEquals("“Wang’s house,” she said.", TextCleaner.clean("“W.a.n.g’s house,” she said."))
    }

    @Test
    fun keepsAbbreviationsInitialsAddressesAndNumbers() {
        val untouched = listOf(
            "It was 7 a.m. and he left.",
            "Fruit, e.g. apples, i.e. red ones.",
            "He flew to the U.S.A. last year.",
            "J.K. Rowling wrote it.",
            "Read it on empirenovel.com today.",
            "Visit www.novel.co.uk now.",
            "It cost 3.5 coins.",
            "He paused... then left.",
            "Mr. Smith and Dr.Who met.",
            "Wait.What?",
        )
        untouched.forEach { assertEquals(it, TextCleaner.clean(it)) }
    }
}

class LanguageDetectorTest {

    @Test
    fun usesPageLanguageAndText() {
        val english = listOf("He walked into the room and looked at her. It was late, and the city was quiet.")
        assertEquals("en", LanguageDetector.detect("en-US", english))
        assertEquals("en", LanguageDetector.detect(null, english))
        assertEquals("zh", LanguageDetector.detect("en", listOf("他走进房间，看着她。天色已晚，城市一片寂静。")))
        assertEquals("ko", LanguageDetector.detect(null, listOf("그는 방으로 걸어 들어가 그녀를 바라보았다.")))
        assertEquals("ja", LanguageDetector.detect(null, listOf("彼は部屋に入って、彼女を見た。もう遅かった。")))
        assertEquals("ru", LanguageDetector.detect(null, listOf("Он вошёл в комнату и посмотрел на неё.")))
        assertEquals("sv", LanguageDetector.detect("sv", listOf("Han gick in i rummet och tittade på henne.")))
        assertEquals("en", LanguageDetector.detect("zh-CN", english))
        assertNull(LanguageDetector.detect(null, listOf("Han gick in i rummet och tittade på henne.")))
    }
}
