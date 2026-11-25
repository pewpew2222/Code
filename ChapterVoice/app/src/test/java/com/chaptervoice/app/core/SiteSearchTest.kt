package com.chaptervoice.app.core

import com.chaptervoice.app.data.SiteResults
import com.chaptervoice.app.ui.orderedResults
import com.chaptervoice.app.ui.searchableSites
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSearchTest {
    @Test fun searchAddresses() {
        assertEquals(listOf("https://www.royalroad.com/fictions/search?title=shadow+slave"), SiteSearch.candidates("royalroad.com", " shadow slave "))
        assertEquals("https://novelfire.net/search?keyword=shadow+slave", SiteSearch.candidates("novelfire.net", "shadow slave").first())
        assertTrue(SiteSearch.browserUrl("novelfire.net", "x").startsWith("https://www.google.com/search?q=site%3Anovelfire.net"))
    }

    @Test fun scoresTitles() {
        assertEquals(1.0, SiteSearch.score("Shadow Slave", "shadow slave"), 0.0)
        assertEquals(0.5, SiteSearch.score("Slave Hunter", "shadow slave"), 0.0)
        assertEquals(1.0, SiteSearch.score("Martial Peak", "mart peak"), 0.0) // a word may be the start ("mart" -> "martial")
        assertEquals(0.0, SiteSearch.score("Lord of Mysteries", "shadow slave"), 0.0)
    }

    @Test fun genericResultsKeepOnlyMatches() {
        val html = """
            <div class="list">
              <div class="item"><a href="/book/shadow-slave"><img src="/c/ss.jpg" alt="Shadow Slave"></a><h3><a href="/book/shadow-slave">Shadow Slave</a></h3></div>
              <div class="item"><h3 class="title"><a href="/book/slave-of-the-shadows">Slave of the Shadows</a></h3></div>
            </div>
            <aside><h4>Popular</h4>
              <div class="item"><a href="/book/lord-of-mysteries"><img src="/c/lotm.jpg" alt="Lord of Mysteries"></a></div>
            </aside>
            <a href="/book/shadow-slave/chapter-1">Shadow Slave Chapter 1</a>
        """.trimIndent()
        val items = SiteSearch.results(Jsoup.parse(html, "https://novelfire.net/search?keyword=shadow+slave"), "novelfire.net", "shadow slave")
        assertEquals(listOf("Shadow Slave", "Slave of the Shadows"), items.map { it.title })
        assertEquals("https://novelfire.net/c/ss.jpg", items[0].coverUrl)
    }

    @Test fun knownSiteKeepsAllResults() {
        val html = """
            <div class="fiction-list-item"><h2 class="fiction-title"><a href="/fiction/1/the-primal-hunter">The Primal Hunter</a></h2></div>
            <div class="fiction-list-item"><h2 class="fiction-title"><a href="/fiction/2/hunter-x">Hunter X</a></h2></div>
        """.trimIndent()
        val items = SiteSearch.results(Jsoup.parse(html, "https://www.royalroad.com/fictions/search?title=primal"), "royalroad.com", "primal")
        assertEquals(listOf("The Primal Hunter", "Hunter X"), items.map { it.title })
    }

    @Test fun sitesWithMatchesFirst() {
        fun item(title: String, site: String) = DiscoverItem(title, "https://$site/b/${title.length}", null, site)
        val ordered = orderedResults(
            listOf(
                SiteResults("a.com", failed = true, searching = false),
                SiteResults("b.com", searching = false),
                SiteResults("c.com", listOf(item("Slave Hunter", "c.com")), searching = false),
                SiteResults("d.com"),
                SiteResults("e.com", listOf(item("Shadow Slave", "e.com")), searching = false),
            ),
            "shadow slave",
        )
        assertEquals(listOf("e.com", "c.com", "d.com", "b.com", "a.com"), ordered.map { it.site })
    }

    @Test fun searchesNovelSitesOnly() {
        assertEquals(
            listOf("novelfire.net", "royalroad.com", "scribblehub.com"),
            searchableSites(listOf("google.com", "www.novelfire.net", "youtube.com"), listOf("royalroad.com", "novelfire.net"), listOf("scribblehub.com")),
        )
    }
}
