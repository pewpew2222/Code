package com.chaptervoice.app.core

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class GluedHeadTest {
    private fun strip(title: String, vararg lines: String) = ContentExtractor.stripGluedHead(lines.toList(), title)

    @Test fun titleTwiceAndEditorGluedToFirstSentence() {
        assertEquals(
            listOf("At night, Yang Jian was reading a novel.", "He was lying on the bed."),
            strip(
                "Chapter 1 - 1 Reincarnated as a Dragon",
                "1: Reincarnated as a Dragon 1: Reincarnated as a Dragon Editor: Atlas Studios At night, Yang Jian was reading a novel.",
                "He was lying on the bed.",
            ),
        )
    }

    @Test fun withoutKnownTitle() {
        assertEquals(
            listOf("At night, Yang Jian was reading a novel."),
            strip("", "Chapter 3: The Egg Chapter 3: The Egg Translator: Nyoi_Bo Editor: Atlas Studios At night, Yang Jian was reading a novel."),
        )
    }

    @Test fun onlyTheHeadingAndCreditGoesAway() {
        assertEquals(listOf("Story."), strip("Chapter 2: Dawn", "2: Dawn Editor: Atlas Studios", "Story."))
    }

    @Test fun storyIsKept() {
        // A short chapter name without its number may be how the story starts.
        assertEquals(listOf("The Dragon woke up."), strip("Chapter 4: The Dragon", "The Dragon woke up."))
        // A character called Ed is no credit.
        assertEquals(listOf("Ed Smith said hello."), strip("Chapter 5: X", "Ed Smith said hello."))
        // Lines further down are not touched.
        val lines = arrayOf("a", "b", "c", "Editor: Atlas Studios said nothing.")
        assertEquals(lines.toList(), strip("Chapter 6: Y", *lines))
    }

    @Test fun throughTheExtractor() {
        val html = """
            <h1>Chapter 1 - 1 Reincarnated as a Dragon</h1>
            <div class="chapter-content"><p><span>1: Reincarnated as a Dragon</span> <span>1: Reincarnated as a Dragon</span>
            <span>Editor: Atlas Studios</span> At night, Yang Jian was reading a novel.</p>
            <p>He was lying on the bed, leaning on the pillow with his phone still charging.</p>
            <p>Suddenly, the surroundings dimmed and his vision immediately fell into endless darkness.</p></div>
        """.trimIndent()
        val result = ContentExtractor.extract(Jsoup.parse(html, "https://novelphoenix.com/book/dragon/chapter-1"))
        assertEquals("At night, Yang Jian was reading a novel.", result.paragraphs.first())
        assertEquals(3, result.paragraphs.size)
    }

    @Test fun nameFollowedByTheStory() {
        assertEquals(
            listOf("Hill was only half the size of Garen."),
            strip(
                "Chapter 2 - 2 Discovered Beautiful Eyes",
                "2: Discovered Beautiful Eyes 2: Discovered Beautiful Eyes Editor: Atlas Studios Hill was only half the size of Garen.",
            ),
        )
    }

    @Test fun noBreakSpaces() {
        assertEquals(
            listOf("Hill was only half the size of Garen."),
            strip("", "2:\u00A0Discovered Beautiful Eyes\u00A02: Discovered\u00A0Beautiful Eyes Editor:\u00A0Atlas Studios Hill was only half the size of Garen."),
        )
    }
}
