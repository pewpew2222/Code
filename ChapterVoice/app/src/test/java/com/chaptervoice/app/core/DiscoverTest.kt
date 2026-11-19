package com.chaptervoice.app.core

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverTest {

    @Test fun royalRoadTrending() {
        val html = """
            <div class="fiction-list-item row">
              <figure><a href="/fiction/111/the-wandering-inn"><img data-type="cover" src="https://www.royalroadcdn.com/public/covers-large/111.jpg" alt="The Wandering Inn"></a></figure>
              <div><h2 class="fiction-title"><a href="/fiction/111/the-wandering-inn">The Wandering Inn</a></h2>
                <span class="tags"><a class="fiction-tag">Fantasy</a><a class="fiction-tag">Adventure</a></span>
                <div id="description-111" class="hidden-content"><p>An inn in another world.</p></div></div>
            </div>
            <div class="fiction-list-item row">
              <figure><a href="/fiction/222/beware"><img src="/covers/222.jpg"></a></figure>
              <h2 class="fiction-title"><a href="/fiction/222/beware">Beware Of Chicken</a></h2>
            </div>""".trimIndent()
        val items = DiscoverParser.parse(Jsoup.parse(html, "https://www.royalroad.com/fictions/trending"), "royalroad.com")
        assertEquals(2, items.size)
        assertEquals("The Wandering Inn", items[0].title)
        assertEquals("https://www.royalroad.com/fiction/111/the-wandering-inn", items[0].url)
        assertEquals("https://www.royalroadcdn.com/public/covers-large/111.jpg", items[0].coverUrl)
        assertEquals("An inn in another world.", items[0].summary)
        assertEquals(listOf("Fantasy", "Adventure"), items[0].tags)
        assertEquals("https://www.royalroad.com/covers/222.jpg", items[1].coverUrl)
    }

    @Test fun scribbleHubRanking() {
        val html = """
            <div class="search_main_box">
              <div class="search_img"><img src="https://cdn.scribblehub.com/images/1/x.jpg"></div>
              <div class="search_body">
                <div class="search_title"><a href="https://www.scribblehub.com/series/123/my-story/">My Story</a></div>
                <div class="search_stats">1.2M views</div>
                <span class="search_genre"><a class="fic_genre">Fantasy</a><a class="fic_genre">Comedy</a></span>
                A boy finds a sword...more>>
              </div>
            </div>""".trimIndent()
        val items = DiscoverParser.parse(Jsoup.parse(html, "https://www.scribblehub.com/series-ranking/"), "scribblehub.com")
        assertEquals(1, items.size)
        assertEquals("My Story", items[0].title)
        assertEquals(listOf("Fantasy", "Comedy"), items[0].tags)
        assertEquals("A boy finds a sword", items[0].summary)
    }

    @Test fun anySiteFindsBookLinksWithCovers() {
        val html = """
            <a href="/"><img src="/logo.png" alt="Site"></a>
            <a href="/login"><img src="/user.png" alt="Login"></a>
            <div class="item"><a href="/novel/martial-peak"><img data-src="/img/mp.jpg" alt="Martial Peak"></a></div>
            <div class="item"><a href="/novel/shadow-slave" title="Shadow Slave"><img src="/img/ss.jpg"></a></div>
            <div class="item"><img src="/img/lotm.jpg"><h3><a href="/novel/lord-of-mysteries">Lord of Mysteries</a></h3></div>
            <div class="item"><a href="/novel/martial-peak/chapter-5"><img src="/img/mp.jpg" alt="Chapter 5"></a></div>
            <a href="https://ads.example.net/x"><img src="https://ads.example.net/banner.jpg" alt="Ad"></a>
        """.trimIndent()
        val items = DiscoverParser.parse(Jsoup.parse(html, "https://novelsite.com/"), "novelsite.com")
        assertEquals(listOf("Martial Peak", "Shadow Slave", "Lord of Mysteries"), items.map { it.title })
        assertEquals("https://novelsite.com/img/mp.jpg", items[0].coverUrl)
    }

    @Test fun fewLinksAreNoList() {
        val html = """<a href="/novel/a"><img src="/a.jpg" alt="A book"></a>"""
        assertTrue(DiscoverParser.parse(Jsoup.parse(html, "https://site.com/"), "site.com").isEmpty())
    }

    @Test fun bookPageSummaryAndFirstChapter() {
        val html = """
            <head><meta property="og:description" content="A young man wakes up in a world of cultivation and must survive."></head>
            <a href="/novel/x">Home</a>
            <ul><li><a href="/novel/x/chapter-1">Chapter 1: The Beginning</a></li><li><a href="/novel/x/chapter-2">Chapter 2</a></li></ul>
        """.trimIndent()
        val page = DiscoverParser.parseBookPage(Jsoup.parse(html, "https://site.com/novel/x"), "https://site.com/novel/x")
        assertEquals("A young man wakes up in a world of cultivation and must survive.", page.summary)
        assertEquals("https://site.com/novel/x/chapter-1", page.firstChapterUrl)

        val start = """<a class="btn" href="/fiction/1/x/chapter/99/prologue">Start Reading</a>"""
        assertEquals(
            "https://www.royalroad.com/fiction/1/x/chapter/99/prologue",
            DiscoverParser.parseBookPage(Jsoup.parse(start, "https://www.royalroad.com/fiction/1/x"), "https://www.royalroad.com/fiction/1/x").firstChapterUrl,
        )
        assertNull(DiscoverParser.parseBookPage(Jsoup.parse("<p>x</p>", "https://a.com/b"), "https://a.com/b").firstChapterUrl)
    }
}
