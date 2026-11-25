package com.chaptervoice.app.core

import org.jsoup.nodes.Document

/** Finds a book's cover image on a web page. */
object CoverFinder {

    /** Image elements sites use for a book cover, tried in order. */
    private val COVER_IMAGES = listOf(
        "img[class*=cover]", "img[id*=cover]", ".book-cover img", ".novel-cover img", ".cover img",
        "[class*=cover] img", ".book img", ".novel img", "img[alt*=cover]", "img[itemprop=image]",
    )
    private val META_IMAGES = listOf(
        "meta[property=og:image]", "meta[name=og:image]", "meta[name=twitter:image]",
        "meta[property=twitter:image]", "link[rel=image_src]",
    )

    /** Images that are logos or decoration rather than a cover. */
    private val NOT_A_COVER = Regex(
        "logo|favicon|icon|avatar|banner|placeholder|default|sprite|loading|blank|ads?[/_-]",
        RegexOption.IGNORE_CASE,
    )

    fun find(doc: Document): String? {
        for (selector in COVER_IMAGES) {
            for (img in doc.select(selector)) {
                val src = listOf("data-src", "data-original", "data-lazy-src", "src").map { img.absUrl(it) }.firstOrNull { it.startsWith("http") }.orEmpty()
                usable(src)?.let { return it }
            }
        }
        for (selector in META_IMAGES) {
            val element = doc.selectFirst(selector) ?: continue
            val src = if (element.normalName() == "link") element.absUrl("href") else element.absUrl("content")
            usable(src)?.let { return it }
        }
        return null
    }

    private fun usable(src: String): String? {
        if (!src.startsWith("http")) return null
        val path = src.substringBefore('?').substringAfterLast('/')
        if (NOT_A_COVER.containsMatchIn(path)) return null
        return src
    }
}

/** Guesses a book's name for the library. */
object BookNames {

    private val CHAPTER_PART = Regex(
        "\\s*(?:[-–—|:•·]\\s*)?\\b(?:chapter|chap|ch|episode|ep)\\b\\.?\\s*\\d+.*$",
        RegexOption.IGNORE_CASE,
    )

    /**
     * The name from the address when it has one ("…/complete-martial-arts-attributes/34"),
     * otherwise the page title without its chapter and site parts.
     */
    fun guess(bookKey: String, pageTitle: String): String {
        val slug = bookKey.substringAfterLast('/', "")
        if (slug.isNotEmpty() && slug != bookKey.substringBefore('/') && slug.count { it.isLetter() } >= 3) {
            return slug.split('-', '_', '+', ' ').filter { it.isNotEmpty() }
                .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
        }
        val parts = pageTitle.split(" - ", " | ", " – ", " — ").map { it.trim() }.filter { it.isNotEmpty() }
        val name = parts.map { it.replace(CHAPTER_PART, "").trim() }.firstOrNull { it.isNotEmpty() }
        return name ?: bookKey.substringBefore('/')
    }
}
