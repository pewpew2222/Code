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

    /** Short lines that are site boilerplate (share boxes, ad labels...). */
    private val JUNK_PHRASE = Regex(
        "^(share (this|to|with)( your)? (friends?|chapter|novel|story|book)|share this|" +
            "advertisements?|sponsored( content)?|annons|" +
            "report (this )?(chapter|error|problem)|" +
            "(please )?(rate|review|vote for) (this|the) (novel|story|book|chapter)|" +
            "tap the screen to use advanced tools.*|use arrow keys.*to (go|navigate).*)[.!:]*$",
        RegexOption.IGNORE_CASE,
    )

    /** Links that only exist in "share on social media" boxes. */
    private const val SHARE_LINKS =
        "a[href*=facebook.com/sharer], a[href*=facebook.com/dialog], a[href*=twitter.com/intent], " +
            "a[href*=x.com/intent], a[href*=x.com/share], a[href*=reddit.com/submit], a[href*=wa.me/], " +
            "a[href^=whatsapp:], a[href^=fb-messenger:], a[href*=pinterest.com/pin/create], " +
            "a[href*=t.me/share], a[href*=telegram.me/share], a[href*=tumblr.com/share]"

    private const val SHARE_BOX_MAX_CHARS = 120

    /**
     * Site-specific fixes. On a matching site, the first element whose own text matches
     * [cutFrom] marks the end of the chapter: it and everything after it is dropped.
     */
    private data class SiteRule(val host: String, val cutFrom: Regex)

    private val SITE_RULES = listOf(
        // Ends each chapter with a share box followed by two more paragraphs of site text.
        SiteRule("novelphoenix.com", Regex("^share to your friends$", RegexOption.IGNORE_CASE)),
    )

    /** [title] is the chapter heading ("Chapter 3: ..."), or empty if none was found. */
    data class Extracted(val title: String, val paragraphs: List<String>)

    fun extract(original: Document): Extracted {
        val doc = original.clone()
        val headingOnPage = findHeading(doc)

        val body = doc.body() ?: return Extracted(headingOnPage.orEmpty(), emptyList())
        applySiteRules(body, doc.location())
        removeShareBoxes(body)
        JUNK_SELECTORS.forEach { selector -> runCatching { body.select(selector).remove() } }

        val container = findByKnownSelector(body) ?: findByScore(body) ?: body
        val lines = toParagraphs(container).filterNot { isJunkLine(it) }
        // Many sites put the chapter heading as the first line of the text instead.
        val title = headingOnPage
            ?: lines.take(HEADING_SCAN_LINES).firstOrNull { looksLikeChapterHeading(it) }
            ?: ""
        val paragraphs = dropLeadingTitle(lines, title).let { dropCredits(it) }.let { stripGluedHead(it, title) }

        return Extracted(title, paragraphs)
    }

    private fun applySiteRules(body: Element, url: String) {
        val host = runCatching { java.net.URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        for (rule in SITE_RULES) {
            if (host != rule.host && !host.endsWith("." + rule.host)) continue
            val marker = body.allElements.firstOrNull { rule.cutFrom.matches(it.ownText().trim()) } ?: continue
            removeFrom(marker, body)
        }
    }

    /** Removes [start] and every node after it in document order, up to [root]. */
    private fun removeFrom(start: Element, root: Element) {
        var node: Node = start
        while (node !== root) {
            val parent = node.parent() ?: break
            // Drop everything after this node on its level, then move up one level.
            while (true) {
                val next = node.nextSibling() ?: break
                next.remove()
            }
            node = parent
        }
        start.remove()
    }

    /**
     * Removes "share to your friends" boxes: the largest ancestor of a share link that
     * still holds only a little text (the heading plus button labels).
     */
    private fun removeShareBoxes(body: Element) {
        for (link in body.select(SHARE_LINKS)) {
            if (link.parent() == null) continue // already removed with an earlier box
            var box: Element = link
            var parent = link.parent()
            while (parent != null && parent !== body && parent.text().length <= SHARE_BOX_MAX_CHARS) {
                box = parent
                parent = parent.parent()
            }
            box.remove()
        }
    }

    /**
     * "Chapter 3: ...", "Ch. 12", "Episode 5", "Prologue", "第三章", "12. Title".
     * Used to tell the chapter heading apart from the book's name, which many sites
     * show in the first <h1>.
     */
    private val CHAPTER_HEADING = Regex(
        "^(?:(?:chapter|chap|ch|episode|ep|prologue|epilogue|interlude|side story|afterword|kapitel|" +
            "capítulo|capitulo|chapitre|глава)(?![\\p{L}])" +
            "|第\\s*[0-9一二三四五六七八九十百千零〇两]+\\s*[章话話回节節]" +
            "|\\d{1,5}\\s*[.:：\\-–—]\\s*\\S)",
        RegexOption.IGNORE_CASE,
    )
    private const val HEADING_MAX_LENGTH = 150
    private const val HEADING_SCAN_LINES = 3

    private fun looksLikeChapterHeading(text: String): Boolean =
        text.length <= HEADING_MAX_LENGTH && CHAPTER_HEADING.containsMatchIn(text.trim())

    private const val CHAPTER_TITLE_CLASSES = ".chapter-title, .chr-title, #chapter-title, .chapter__title, .chapter-name"

    /** The chapter heading on the page, or null (then the first lines of the text are checked). */
    private fun findHeading(doc: Document): String? {
        val candidates = doc.select("$CHAPTER_TITLE_CLASSES, h1, h2, h3, h4")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
        candidates.firstOrNull { looksLikeChapterHeading(it) }?.let { return it }
        // An element the site itself calls the chapter title, even without "Chapter N".
        return doc.select(CHAPTER_TITLE_CLASSES).map { it.text().trim() }
            .firstOrNull { it.isNotEmpty() && it.length <= HEADING_MAX_LENGTH }
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
            val text = current.toString().replace(SPACES, " ").trim()
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
        line.length <= 60 && (JUNK_LINE.matches(line) || JUNK_PHRASE.matches(line))

    /**
     * Translator/editor credits ("Translator: X Editor: Y", "TL: X", "Translated by X").
     * Only checked near the start and end of the chapter, where sites put them, so a
     * story line that happens to start with "Editor:" in the middle is kept.
     */
    private val CREDIT_LINE = Regex(
        "^[\\s\\[(【]*(?:" +
            // A role label followed by a colon or dash: "Translator: X", "TL - X", "编辑：X".
            "(?:translators?|translation|editors?|proofreaders?|proofreading|tl|tlc|tl check|ed|pr|qc|" +
            "raw provider|raws|cleaner|typesetter|翻译|翻譯|校对|校對|编辑|編輯)\\s*[:：\\-–—]" +
            // Or "Translated by X", "Edited by X".
            "|(?:translated|edited|proofread|translations?)\\s+by\\s)",
        RegexOption.IGNORE_CASE,
    )
    private const val CREDIT_SCAN_START = 6
    private const val CREDIT_SCAN_END = 4
    private const val CREDIT_MAX_LENGTH = 160

    /** A lone number such as a comment counter next to the title ("6", "12 comments"). */
    private val COUNTER_LINE = Regex("^[\\d.,]{1,7}( (comments?|kommentarer|views?|likes?))?$", RegexOption.IGNORE_CASE)

    private fun dropCredits(paragraphs: List<String>): List<String> =
        paragraphs.filterIndexed { index, line ->
            val nearEdge = index < CREDIT_SCAN_START || index >= paragraphs.size - CREDIT_SCAN_END
            val junk = (line.length <= CREDIT_MAX_LENGTH && CREDIT_LINE.containsMatchIn(line)) ||
                COUNTER_LINE.matches(line.trim())
            !(nearEdge && junk)
        }

    private fun dropLeadingTitle(paragraphs: List<String>, title: String): List<String> {
        if (title.isBlank()) return paragraphs
        val index = paragraphs.take(HEADING_SCAN_LINES).indexOfFirst { it.equals(title, ignoreCase = true) }
        return if (index < 0) paragraphs else paragraphs.filterIndexed { i, _ -> i != index }
    }

    // ------------------------------------------------------------ glued head

    /** Any run of spaces, including no-break and zero-width ones ("\u00A0", "\u200B"). */
    private val SPACES = Regex("[\\s\\p{Z}\\u200B-\\u200D\\uFEFF]+")

    private const val GLUED_SCAN = 3
    private const val SEP = "\\s*[:：.\\-–—|]?\\s*"

    /** "Chapter 12 - 3 " or "12: " in front of a chapter's name. */
    private val NUMBER_PREFIX = Regex("^(?:(?:chapter|chap|ch|episode|ep)\\.?\\s*\\d+$SEP)?(?:\\d+$SEP)?", RegexOption.IGNORE_CASE)

    /** A credit at the start of a line, before the story goes on in the same line. */
    private val CREDIT_LABEL = Regex(
        "^(?:(?:translators?|editors?|proofreaders?|tl|ed|pr|tlc)\\s*[:：\\-–—]|(?:edited|translated) by\\s)\\s*",
        RegexOption.IGNORE_CASE,
    )

    /** Capitalised words that start a sentence rather than continue a name. */
    private val SENTENCE_STARTERS = setOf(
        "a", "an", "the", "at", "in", "on", "as", "after", "before", "when", "while", "suddenly", "meanwhile",
        "he", "she", "it", "they", "we", "i", "you", "his", "her", "their", "this", "that", "these", "those",
        "there", "here", "but", "and", "so", "then", "now", "what", "why", "how", "who", "where", "chapter",
        "translator", "translators", "editor", "editors", "proofreader", "tl", "ed", "pr",
    )

    /**
     * The first lines of some sites hold the chapter's name (sometimes twice) and the
     * credits glued to the first sentence: "1: Reincarnated as a Dragon 1: Reincarnated as
     * a Dragon Editor: Atlas Studios At night, Yang Jian was reading a novel."
     * Strips those from the start of the first lines and keeps the story.
     */
    internal fun stripGluedHead(paragraphs: List<String>, title: String): List<String> {
        val name = NUMBER_PREFIX.replace(title.trim(), "").trim().trimEnd(':', '.', '-', '–', '—').trim()
        val result = paragraphs.toMutableList()
        for (i in 0 until minOf(GLUED_SCAN, result.size)) {
            var line = result[i].replace(SPACES, " ").trim()
            var changed = true
            while (changed) {
                val before = line
                if (name.length >= 3) line = stripName(line, name)
                line = stripRepeatedHeading(line)
                line = stripCredit(line)
                changed = line != before && line.isNotEmpty()
            }
            result[i] = line
        }
        return result.filter { it.isNotBlank() }
    }

    private fun stripName(line: String, name: String): String {
        val pattern = Regex("^(?:(?:chapter|chap|ch|episode|ep)\\.?\\s*\\d+$SEP)?(?:\\d+$SEP)?" + Regex.escape(name) + "(?![\\p{L}\\p{N}])$SEP", RegexOption.IGNORE_CASE)
        val match = pattern.find(line) ?: return line
        // A short name ("The Dragon") may also be how the story starts: only with its number.
        if (name.length < 15 && match.value.none(Char::isDigit)) return line
        return line.substring(match.range.last + 1).trimStart()
    }

    /** The same heading written twice at the start: "Chapter 5: X Chapter 5: X ...". */
    private val REPEATED_HEAD = Regex("^(.{4,90}?)\\s+\\1(?![\\p{L}\\p{N}])\\s*")

    private fun stripRepeatedHeading(line: String): String {
        val match = REPEATED_HEAD.find(line) ?: return line
        val head = match.groupValues[1]
        return if (looksLikeChapterHeading(head) || head.first().isDigit()) line.substring(match.range.last + 1).trimStart() else line
    }

    private fun stripCredit(line: String): String {
        val label = CREDIT_LABEL.find(line) ?: return line
        val words = line.substring(label.range.last + 1).split(' ').filter { it.isNotEmpty() }
        var taken = 0
        for ((i, word) in words.withIndex()) {
            if (taken >= 4) break
            val bare = word.trim(',', '.', ':', ';', '!', '?', '"', '“', '”')
            val nameLike = bare.isNotEmpty() && (bare.first().isUpperCase() || bare.first().isDigit() || bare.contains('_'))
            if (!nameLike || (taken > 0 && bare.lowercase() in SENTENCE_STARTERS)) break
            // "Atlas Studios Hill was …": a capitalised word followed by a small one starts the sentence.
            val nextWord = words.getOrNull(i + 1)
            if (taken > 0 && nextWord != null && nextWord.first().isLowerCase()) break
            taken++
            // A name ends where its word ends a sentence or a clause.
            if (word.endsWith('.') || word.endsWith(',')) break
        }
        if (taken == 0) return line
        val rest = words.drop(taken).joinToString(" ")
        // A line that was only the credit is left empty and dropped.
        return rest
    }
}
