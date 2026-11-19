package com.chaptervoice.app.core

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** A book suggested on the start page, found on a site's popular or front page. */
data class DiscoverItem(
    val title: String,
    val url: String,
    val coverUrl: String?,
    val site: String,
    val summary: String = "",
    val tags: List<String> = emptyList(),
)

/** Where a site lists its popular books, and how to read that list. */
data class DiscoverSource(val site: String, val url: String, val label: String)

/**
 * Finds books on a site's popular list. Sites with a known layout are read exactly;
 * any other page is read by looking for repeated links with a cover image.
 */
object DiscoverParser {

    /** Sites with a popular list of their own. Others use their front page. */
    val KNOWN = listOf(
        DiscoverSource("royalroad.com", "https://www.royalroad.com/fictions/trending", "Trending"),
        DiscoverSource("scribblehub.com", "https://www.scribblehub.com/series-ranking/?sort=1&order=1", "Ranking"),
    )

    fun sourceFor(site: String): DiscoverSource =
        KNOWN.firstOrNull { it.site == site } ?: DiscoverSource(site, "https://$site/", "")

    fun parse(doc: Document, site: String, limit: Int = 24): List<DiscoverItem> {
        val exact = when (site) {
            "royalroad.com" -> royalRoad(doc, site)
            "scribblehub.com" -> scribbleHub(doc, site)
            else -> emptyList()
        }
        val items = exact.ifEmpty { generic(doc, site) }
        return items.distinctBy { it.url.trimEnd('/') }.take(limit)
    }

    private fun royalRoad(doc: Document, site: String) = doc.select(".fiction-list-item").mapNotNull { item ->
        val link = item.selectFirst(".fiction-title a[href]") ?: return@mapNotNull null
        DiscoverItem(
            title = link.text().trim(),
            url = link.absUrl("href"),
            coverUrl = item.selectFirst("img")?.let(::imageUrl),
            site = site,
            summary = item.selectFirst("[id^=description]")?.text()?.trim().orEmpty(),
            tags = item.select(".tags a, a.fiction-tag").map { it.text().trim() }.filter { it.isNotEmpty() }.take(6),
        )
    }

    private fun scribbleHub(doc: Document, site: String) = doc.select(".search_main_box").mapNotNull { item ->
        val link = item.selectFirst(".search_title a[href]") ?: return@mapNotNull null
        val body = item.selectFirst(".search_body")?.clone()
        body?.select(".search_title, .search_stats, .search_genre, .nl_stat, .search_ratings")?.remove()
        DiscoverItem(
            title = link.text().trim(),
            url = link.absUrl("href"),
            coverUrl = item.selectFirst(".search_img img, img")?.let(::imageUrl),
            site = site,
            summary = body?.text()?.trim()?.removeSuffix("...more>>")?.trim().orEmpty(),
            tags = item.select(".search_genre a, a.fic_genre").map { it.text().trim() }.filter { it.isNotEmpty() }.take(6),
        )
    }

    private val SKIP_PATH = Regex(
        "(^|/)(login|register|signup|account|user|profile|author|tag|tags|genre|genres|category|search|forum|" +
            "privacy|terms|contact|about|faq|help|news|blog|ranking|latest|completed)(/|$)",
        RegexOption.IGNORE_CASE,
    )
    private val CHAPTER_PATH = Regex("(chapter|chap|ch|episode|ep)[-_/]?\\d+", RegexOption.IGNORE_CASE)

    /**
     * Book links of any site: links on the same site that hold (or sit next to) a cover
     * image. The largest group of links sharing their first path segment ("/novel/…")
     * is taken as the site's book pages.
     */
    internal fun generic(doc: Document, site: String): List<DiscoverItem> {
        val found = LinkedHashMap<String, DiscoverItem>()
        doc.select("img").forEach { img ->
            val cover = imageUrl(img) ?: return@forEach
            if (cover.endsWith(".svg") || cover.contains("logo", ignoreCase = true) || cover.contains("avatar", ignoreCase = true)) return@forEach
            val link = img.closest("a[href]") ?: nearbyLink(img) ?: return@forEach
            val url = link.absUrl("href").substringBefore('#')
            if (!isBookUrl(url, site)) return@forEach
            val title = titleFor(link, img) ?: return@forEach
            found.putIfAbsent(url.trimEnd('/'), DiscoverItem(title, url, cover, site))
        }
        if (found.isEmpty()) return emptyList()
        // Keep the biggest group: book pages share a path ("/novel/…", "/book/…").
        val groups = found.values.groupBy { firstSegment(it.url) }
        val best = groups.maxByOrNull { it.value.size }?.value.orEmpty()
        return if (best.size >= 3) best else emptyList()
    }

    private fun nearbyLink(img: Element): Element? {
        var parent = img.parent()
        repeat(3) {
            val p = parent ?: return null
            val links = p.select("a[href]")
            if (links.isNotEmpty()) return links.first()
            parent = p.parent()
        }
        return null
    }

    private fun isBookUrl(url: String, site: String): Boolean {
        if (!url.startsWith("http")) return false
        val host = runCatching { java.net.URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
        if (host != site && !host.endsWith(".$site")) return false
        val path = runCatching { java.net.URI(url).path.orEmpty() }.getOrDefault("").trim('/')
        if (path.isEmpty()) return false
        if (SKIP_PATH.containsMatchIn(path) || CHAPTER_PATH.containsMatchIn(path)) return false
        return true
    }

    private fun firstSegment(url: String): String =
        runCatching { java.net.URI(url).path.orEmpty() }.getOrDefault("").trim('/').substringBefore('/')
            .let { if (it.any(Char::isDigit) && it.length > 12) "" else it }

    private fun titleFor(link: Element, img: Element): String? {
        val candidates = listOf(
            link.attr("title"),
            img.attr("alt"),
            img.attr("title"),
            link.text(),
            link.parent()?.selectFirst("h1, h2, h3, h4, h5, [class*=title]")?.text().orEmpty(),
        )
        return candidates.map { it.trim() }
            .firstOrNull { it.length in 2..120 && !it.equals("cover", true) && !it.startsWith("http") }
    }

    private fun imageUrl(img: Element): String? {
        for (attr in listOf("data-src", "data-original", "data-lazy-src", "data-cfsrc", "src")) {
            val value = img.absUrl(attr)
            if (value.startsWith("http") && !value.startsWith("data:")) return value
        }
        return img.attr("srcset").substringBefore(' ').takeIf { it.startsWith("http") }
    }

    private val START_TEXT = Regex(
        "^(start reading|read first( chapter)?|first chapter|read now|begin reading|read chapter 1|läs första kapitlet)$",
        RegexOption.IGNORE_CASE,
    )

    /** What a book page says about itself: its description and the first chapter's address. */
    data class BookPage(val summary: String, val firstChapterUrl: String?)

    fun parseBookPage(doc: Document, url: String): BookPage {
        val summary = listOf(
            doc.selectFirst(".description")?.text(),
            doc.selectFirst("meta[property=og:description]")?.attr("content"),
            doc.selectFirst("meta[name=description]")?.attr("content"),
        ).map { it.orEmpty().trim() }.firstOrNull { it.length > 40 }.orEmpty()

        val links = doc.select("a[href]")
        val first = links.firstOrNull { START_TEXT.matches(it.text().trim()) }
            ?: links.firstOrNull { a ->
                val text = a.text().trim()
                NextChapterResolver.chapterNumberIn(text) == 1 || text.matches(Regex("^(1|01|001)[.:)\\s].*"))
            }
        val firstUrl = first?.absUrl("href")?.takeIf { it.startsWith("http") && it.trimEnd('/') != url.trimEnd('/') }
        return BookPage(summary, firstUrl)
    }
}
