package com.chaptervoice.app.core

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/**
 * Pulls the readable chapter text out of a web page.
 *
 * Strategy:
 * 1. Strip obvious non-content (scripts, navigation, ads, comments...).
 * 2. Try selectors that common web novel sites use for the chapter body.
 * 3. Otherwise score every element by how much paragraph text it directly holds
 *    and pick the best one.
 */
object ContentExtractor {

    /** Selectors used by many web novel sites, tried in order. */
    private val KNOWN_SELECTORS = listOf(
        "#chapter-content", "#chr-content", "#chapter-container", "#chaptercontent",
        "#chapterContent", ".chapter-content", ".chapter-c", ".chr-c", ".chapter__content",
        ".reading-content", ".text-left", "#novel-content", ".novel-content",
        ".entry-content", "#content", ".content", "article",
    )

    private const val MIN_KNOWN_SELECTOR_CHARS = 400

    private val JUNK_SELECTORS = listOf(
        "script", "style", "noscript", "iframe", "svg", "canvas", "form", "button", "select",
        "input", "textarea", "nav", "header", "footer", "aside", "ins", "template",
        "[class*=comment]", "[id*=comment]", "[class*=advert]", "[id*=advert]",
        ".ad", ".ads", ".adsbygoogle", "[id^=ad-]", "[class^=ad-]",
        "[class*=share]", "[class*=social]", "[class*=breadcrumb]", "[style*=display:none]",
        "[style*=display: none]", "[hidden]", "[aria-hidden=true]",
    )

    private val BLOCK_TAGS = setOf(
        "p", "div", "br", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote",
        "section", "article", "tr", "pre", "hr", "dd", "dt",
    )

    /** Short lines that are site chrome rather than story text. */
    private const val NAV_SEPARATORS = "[\\s«»<>←→|/]*"
    private val JUNK_LINE = Regex(
        "^(?:$NAV_SEPARATORS(?:previous|prev|next|nästa|föregående|index|table of contents|" +
            "chapter list|report|bookmark|上一章|下一章|目录|返回)(?: chapter| page| kapitel| sida)?)+" +
            "$NAV_SEPARATORS$",
        RegexOption.IGNORE_CASE,
    )

    data class Extracted(val title: String, val paragraphs: List<String>)

    fun extract(original: Document): Extracted {
        val doc = original.clone()
        val title = findTitle(doc)

        val body = doc.body() ?: return Extracted(title, emptyList())
        JUNK_SELECTORS.forEach { selector -> runCatching { body.select(selector).remove() } }

        val container = findByKnownSelector(body) ?: findByScore(body) ?: body
        val paragraphs = toParagraphs(container)
            .filterNot { isJunkLine(it) }
            .let { dropLeadingTitle(it, title) }

        return Extracted(title, paragraphs)
    }

    private fun findTitle(doc: Document): String {
        val candidates = listOf(
            ".chapter-title", ".chr-title", "#chapter-title", ".chapter__title",
            "h1", "h2", ".title",
        )
        for (selector in candidates) {
            val text = doc.selectFirst(selector)?.text()?.trim().orEmpty()
            if (text.isNotEmpty() && text.length < 200) return text
        }
        return doc.title().trim()
    }

    private fun findByKnownSelector(body: Element): Element? {
        for (selector in KNOWN_SELECTORS) {
            val match = body.select(selector)
                .maxByOrNull { it.text().length }
                ?: continue
            if (match.text().length >= MIN_KNOWN_SELECTOR_CHARS && linkDensity(match) < 0.3) {
                return match
            }
        }
        return null
    }

    /**
     * Scores each element by the text held directly in it (its own text nodes and
     * direct `<p>` children). Story text usually sits in one container as a long run
     * of siblings, so the element with the most such text wins.
     */
    private fun findByScore(body: Element): Element? {
        var best: Element? = null
        var bestScore = 0.0
        for (element in body.allElements) {
            var chars = 0
            for (child in element.childNodes()) {
                when {
                    child is TextNode -> chars += child.text().trim().length
                    child is Element && child.normalName() == "p" -> chars += child.text().length
                }
            }
            if (chars == 0) continue
            val score = chars * (1.0 - linkDensity(element))
            if (score > bestScore) {
                bestScore = score
                best = element
            }
        }
        return best
    }

    private fun linkDensity(element: Element): Double {
        val total = element.text().length
        if (total == 0) return 1.0
        val linkChars = element.select("a").sumOf { it.text().length }
        return linkChars.toDouble() / total
    }

    /** Walks the container and splits its text on block elements and `<br>`. */
    private fun toParagraphs(container: Element): List<String> {
        val lines = mutableListOf<String>()
        val current = StringBuilder()

        fun flush() {
            val text = current.toString().replace(Regex("\\s+"), " ").trim()
            if (text.isNotEmpty()) lines += text
            current.setLength(0)
        }

        NodeTraversor.traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                when {
                    node is TextNode -> if (!insideLink(node)) current.append(node.wholeText)
                    node is Element && node.normalName() in BLOCK_TAGS -> flush()
                }
            }

            override fun tail(node: Node, depth: Int) {
                if (node is Element && node.normalName() in BLOCK_TAGS) flush()
            }
        }, container)
        flush()
        return lines
    }

    private fun insideLink(node: Node): Boolean {
        var parent = node.parent()
        while (parent != null) {
            if (parent is Element && parent.normalName() == "a") return true
            parent = parent.parent()
        }
        return false
    }

    private fun isJunkLine(line: String): Boolean =
        line.length <= 60 && JUNK_LINE.matches(line)

    private fun dropLeadingTitle(paragraphs: List<String>, title: String): List<String> {
        if (paragraphs.isEmpty() || title.isBlank()) return paragraphs
        return if (paragraphs.first().equals(title, ignoreCase = true)) paragraphs.drop(1) else paragraphs
    }
}
