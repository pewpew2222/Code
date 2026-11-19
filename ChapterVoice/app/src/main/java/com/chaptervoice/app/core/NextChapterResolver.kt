package com.chaptervoice.app.core

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** How the next chapter's address is found. */
enum class NextMode {
    /** Use the page's "next chapter" link, and fall back to changing the number in the URL. */
    AUTO,

    /** Only follow the page's "next chapter" link. */
    LINK,

    /** Only increase the last number in the URL (chapter-12 -> chapter-13). */
    URL_INCREMENT,
}

object NextChapterResolver {

    private val NEXT_TEXT = Regex(
        "^(next|next chapter|next chap|next page|nästa|nästa kapitel|nästa sida|" +
            "下一章|下一页|下一頁|下一章节|다음 화|다음화|次へ|次の話|siguiente|capítulo siguiente|" +
            "suivant|chapitre suivant|weiter|nächstes kapitel|próximo|seguinte|" +
            "»|>|>>|→|next »|next >|next >>|next →|nästa »|nästa >)$",
        RegexOption.IGNORE_CASE,
    )

    private val NEXT_ATTR = Regex(
        "(^|[-_ ])(next|nextchap|next_chap|next-chap|nextchapter|next_chapter|next-chapter|btn-next|chapter-next)([-_ ]|$)",
        RegexOption.IGNORE_CASE,
    )

    fun resolve(doc: Document, currentUrl: String, mode: NextMode): String? {
        val fromLink = if (mode != NextMode.URL_INCREMENT) findNextLink(doc, currentUrl) else null
        if (fromLink != null) return fromLink
        return if (mode != NextMode.LINK) incrementUrl(currentUrl) else null
    }

    fun findNextLink(doc: Document, currentUrl: String): String? {
        // 1. Explicit rel="next".
        doc.select("link[rel=next][href], a[rel~=(?i)next][href]").forEach { element ->
            usable(element, currentUrl)?.let { return it }
        }

        val anchors = doc.select("a[href]")

        // 2. Link text that says "next".
        anchors.forEach { a ->
            val text = a.text().trim().ifEmpty { a.attr("title").trim() }
            if (NEXT_TEXT.matches(text) || NEXT_TEXT.matches(a.attr("aria-label").trim())) {
                usable(a, currentUrl)?.let { return it }
            }
        }

        // 3. id/class names such as "next_chap" or "btn-next".
        anchors.forEach { a ->
            if (NEXT_ATTR.containsMatchIn(a.id()) || NEXT_ATTR.containsMatchIn(a.className())) {
                usable(a, currentUrl)?.let { return it }
            }
        }
        return null
    }

    private val PREV_TEXT = Regex(
        "^(prev|previous|previous chapter|prev chapter|prev chap|previous page|föregående|föregående kapitel|" +
            "上一章|上一页|上一頁|이전 화|이전화|前へ|前の話|anterior|capítulo anterior|précédent|chapitre précédent|" +
            "zurück|vorheriges kapitel|«|<|<<|←|‹|« prev|< prev|<< prev|« previous|‹ prev|← prev)$",
        RegexOption.IGNORE_CASE,
    )
    private val PREV_ATTR = Regex(
        "(^|[-_ ])(prev|prevchap|prev_chap|prev-chap|previous|prevchapter|prev_chapter|prev-chapter|btn-prev|chapter-prev)([-_ ]|$)",
        RegexOption.IGNORE_CASE,
    )
    private val INDEX_TEXT = Regex(
        "^(index|table of contents|contents|toc|chapter list|chapters|all chapters|chapter index|novel info|" +
            "kapitellista|innehåll|目录|目錄|章节目录|목차|目次|índice|sommaire)$",
        RegexOption.IGNORE_CASE,
    )
    private val INDEX_ATTR = Regex(
        "(^|[-_ ])(toc|chapter-list|chapterlist|chapter_list|list-chapter|catalog|btn-list|btn-index|index)([-_ ]|$)",
        RegexOption.IGNORE_CASE,
    )

    /** Previous chapter, next chapter and the chapter list, as found on a chapter page. */
    data class ChapterLinks(val previous: String?, val next: String?, val index: String?) {
        /** The page has the links of a chapter page (so chapter buttons make sense). */
        val isChapterPage: Boolean get() = previous != null || next != null
    }

    fun findChapterLinks(doc: Document, currentUrl: String): ChapterLinks = ChapterLinks(
        previous = findLink(doc, currentUrl, "prev", PREV_TEXT, PREV_ATTR),
        next = findNextLink(doc, currentUrl),
        index = findLink(doc, currentUrl, null, INDEX_TEXT, INDEX_ATTR),
    )

    private fun findLink(doc: Document, currentUrl: String, rel: String?, text: Regex, attr: Regex): String? {
        if (rel != null) {
            doc.select("link[rel=$rel][href], a[rel~=(?i)$rel][href]").forEach { element ->
                usable(element, currentUrl)?.let { return it }
            }
        }
        val anchors = doc.select("a[href]")
        anchors.forEach { a ->
            val label = a.text().trim().ifEmpty { a.attr("title").trim() }
            if (text.matches(label) || text.matches(a.attr("aria-label").trim())) usable(a, currentUrl)?.let { return it }
        }
        anchors.forEach { a ->
            if (attr.containsMatchIn(a.id()) || attr.containsMatchIn(a.className())) usable(a, currentUrl)?.let { return it }
        }
        return null
    }

    private fun usable(element: Element, currentUrl: String): String? {
        val raw = element.attr("href").trim()
        if (raw.isEmpty() || raw.startsWith("#") || raw.startsWith("javascript:", ignoreCase = true)) return null
        val absolute = element.absUrl("href").ifEmpty { return null }
        if (!absolute.startsWith("http")) return null
        if (sameUrl(absolute, currentUrl)) return null
        if (element.hasClass("disabled") || element.hasAttr("disabled")) return null
        return absolute
    }

    private fun sameUrl(a: String, b: String): Boolean =
        a.substringBefore('#').trimEnd('/') == b.substringBefore('#').trimEnd('/')

    /**
     * Increases the last number in the URL after the host, keeping zero padding.
     * `https://site.com/novel/chapter-009.html` -> `https://site.com/novel/chapter-010.html`.
     */
    fun incrementUrl(url: String): String? {
        val schemeEnd = url.indexOf("://")
        val pathStart = if (schemeEnd >= 0) url.indexOf('/', schemeEnd + 3) else 0
        if (pathStart < 0) return null

        val fragmentStart = url.indexOf('#').let { if (it < 0) url.length else it }
        val searchArea = url.substring(pathStart, fragmentStart)
        val match = Regex("\\d+").findAll(searchArea).lastOrNull() ?: return null

        val digits = match.value
        val next = (digits.toBigInteger() + java.math.BigInteger.ONE).toString()
        val padded = if (digits.startsWith("0") && next.length < digits.length) {
            next.padStart(digits.length, '0')
        } else {
            next
        }
        val start = pathStart + match.range.first
        val end = pathStart + match.range.last + 1
        return url.substring(0, start) + padded + url.substring(end)
    }
}
