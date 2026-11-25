package com.chaptervoice.app.core

import org.jsoup.nodes.Document
import java.net.URLEncoder

/**
 * Searching novel sites for a book: where each site's search page is, and which links
 * on a result page are books matching the words searched for.
 */
object SiteSearch {

    /** Search pages of sites where the format is known. */
    val KNOWN = mapOf(
        "royalroad.com" to "https://www.royalroad.com/fictions/search?title={q}",
        "scribblehub.com" to "https://www.scribblehub.com/?s={q}&post_type=fictionposts",
        "webnovel.com" to "https://www.webnovel.com/search?keywords={q}",
        "archiveofourown.org" to "https://archiveofourown.org/works/search?work_search%5Bquery%5D={q}",
        "fanfiction.net" to "https://www.fanfiction.net/search/?keywords={q}&type=story",
    )

    /** Search addresses most novel sites use; tried in order on sites not in [KNOWN]. */
    private val COMMON = listOf(
        "https://{site}/search?keyword={q}",
        "https://{site}/search?q={q}",
        "https://{site}/?s={q}",
    )

    private fun encode(query: String) = URLEncoder.encode(query.trim(), "UTF-8")

    /** The address to open for searching [site] in the browser. */
    fun browserUrl(site: String, query: String): String =
        KNOWN[site]?.replace("{q}", encode(query))
            ?: "https://www.google.com/search?q=" + URLEncoder.encode("site:$site ${query.trim()}", "UTF-8")

    /** The search pages to try for [site], best guess first. */
    fun candidates(site: String, query: String): List<String> {
        val q = encode(query)
        KNOWN[site]?.let { return listOf(it.replace("{q}", q)) }
        return COMMON.map { it.replace("{site}", site).replace("{q}", q) }
    }

    private val WORD = Regex("[\\p{L}\\p{N}]+")

    internal fun words(text: String): List<String> =
        WORD.findAll(text.lowercase()).map { it.value }.filter { it.length > 1 || it.any(Char::isDigit) }.toList()

    /** How well [title] matches [query]: the share of the query's words found in it (0..1). */
    fun score(title: String, query: String): Double {
        val wanted = words(query).ifEmpty { return 0.0 }
        val have = words(title).toSet()
        val found = wanted.count { w -> have.any { it == w || (w.length >= 4 && it.startsWith(w)) } }
        return found.toDouble() / wanted.size
    }

    /**
     * Books on a search result page that match [query], best first. Pages of known sites
     * list only results, so all of them count; on other sites only titles with a searched
     * word count, which leaves out "popular" and "latest" lists around the results.
     */
    fun results(doc: Document, site: String, query: String, limit: Int = 12): List<DiscoverItem> {
        val exact = site in DiscoverParser.KNOWN.map { it.site }
        val found = (DiscoverParser.parse(doc, site, limit = 60, minGroup = 1) + if (exact) emptyList() else textLinks(doc, site))
            .distinctBy { it.url.trimEnd('/') }
        return found
            .map { it to score(it.title, query) }
            .filter { (_, s) -> s > 0 || exact }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
    }

    /** Result lists without covers: links to book pages with the title as text. */
    private fun textLinks(doc: Document, site: String): List<DiscoverItem> =
        doc.select("h1 a[href], h2 a[href], h3 a[href], h4 a[href], [class*=title] a[href], a[class*=title][href]").mapNotNull { a ->
            val url = a.absUrl("href").substringBefore('#')
            val title = a.text().trim().ifEmpty { a.attr("title").trim() }
            if (title.length !in 2..150 || !DiscoverParser.isBookUrl(url, site)) return@mapNotNull null
            val cover = a.closest("li, article, .item, [class*=item], [class*=novel], [class*=book]")
                ?.selectFirst("img")?.let { DiscoverParser.imageUrl(it) }
            DiscoverItem(title, url, cover, site)
        }
}
