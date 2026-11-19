package com.chaptervoice.app.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

data class Chapter(
    val url: String,
    val title: String,
    val paragraphs: List<String>,
    val nextUrl: String?,
    /** The page's <title>, which usually names the book as well as the chapter. */
    val pageTitle: String = "",
    /** The chapter heading to read aloud ("Chapter 3: ..."), or empty. */
    val heading: String = title,
    /** Language tag of the text ("en", "zh", ...), or null if unknown. */
    val language: String? = null,
    /** The book's cover image, when the chapter page shows it. */
    val coverUrl: String? = null,
) {
    val textFingerprint: Int get() = paragraphs.joinToString("\n").hashCode()
}

object ChapterParser {
    fun parse(doc: Document, url: String, mode: NextMode): Chapter {
        val nextUrl = NextChapterResolver.resolve(doc, url, mode)
        val extracted = ContentExtractor.extract(doc)
        val pageTitle = doc.title().trim()
        return Chapter(
            url = url,
            title = extracted.title.ifBlank { pageTitle },
            paragraphs = extracted.paragraphs,
            nextUrl = nextUrl,
            pageTitle = pageTitle,
            heading = extracted.title,
            language = LanguageDetector.detect(doc.selectFirst("html")?.attr("lang"), extracted.paragraphs),
            coverUrl = CoverFinder.find(doc),
        )
    }

    fun parseHtml(html: String, url: String, mode: NextMode): Chapter =
        parse(Jsoup.parse(html, url), url, mode)
}

/**
 * Downloads chapters. Sends the same user agent and cookies as the in-app browser so
 * that sites behind bot protection accept the request once the user has opened them.
 */
class ChapterFetcher(
    private val userAgent: String,
    private val cookieProvider: (String) -> String? = { null },
) {
    /** Any page, e.g. a book's own page to find its cover. */
    fun fetchDocument(url: String, referrer: String? = null): Document {
        val connection = Jsoup.connect(url).userAgent(userAgent).timeout(20_000).followRedirects(true)
        referrer?.let { connection.referrer(it) }
        cookieProvider(url)?.takeIf { it.isNotBlank() }?.let { connection.header("Cookie", it) }
        return connection.get()
    }

    fun fetch(url: String, mode: NextMode, referrer: String? = null): Chapter {
        val connection = Jsoup.connect(url)
            .userAgent(userAgent)
            .timeout(30_000)
            .followRedirects(true)
            .maxBodySize(0)
        referrer?.let { connection.referrer(it) }
        cookieProvider(url)?.takeIf { it.isNotBlank() }?.let { connection.header("Cookie", it) }

        val response = connection.execute()
        val finalUrl = response.url().toString()
        return ChapterParser.parse(response.parse(), finalUrl, mode)
    }
}
