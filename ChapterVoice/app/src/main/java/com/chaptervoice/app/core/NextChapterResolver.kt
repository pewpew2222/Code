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
