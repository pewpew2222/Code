package com.chaptervoice.app.core

import java.net.URI

/**
 * Works out which book a chapter belongs to from its address, by removing the
 * chapter part: "site.com/novel/martial-arts/chapter-3" -> "site.com/novel/martial-arts".
 * All chapters of a book then share the same key (used for per-book settings).
 */
object BookKey {

    /** "-chapter-3", "_ch12", "-3" at the end of a slug like "martial-arts-chapter-3.html". */
    private val CHAPTER_SUFFIX = Regex("[-_ ]*(?:chapter|chap|ch|episode|ep|c)?[-_ ]*\\d+.*$", RegexOption.IGNORE_CASE)

    fun of(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }.toMutableList()

        // The last part names the chapter when it contains a number ("chapter-3", "678").
        if (segments.isNotEmpty() && segments.last().any { it.isDigit() }) {
            val last = segments.removeAt(segments.lastIndex)
            if (segments.isEmpty()) {
                // Book and chapter in one part: "martial-arts-chapter-3.html" -> "martial-arts".
                val book = last.substringBeforeLast('.').replace(CHAPTER_SUFFIX, "")
                if (book.isNotEmpty()) segments += book
            }
        }
        return (listOf(host) + segments).joinToString("/")
    }

    /** A readable name: "site.com/novel/martial-arts" -> "Martial Arts (site.com)". */
    fun displayName(key: String): String {
        val host = key.substringBefore('/')
        val slug = key.substringAfterLast('/', "")
        if (slug.isEmpty() || slug == host || slug.none { it.isLetter() }) return key
        val name = slug.split('-', '_', '+', ' ').filter { it.isNotEmpty() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
        return "$name ($host)"
    }
}
