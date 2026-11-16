package com.chaptervoice.app.core

/**
 * Splits paragraphs into pieces the TTS engine accepts. Each paragraph stays one
 * piece unless it is longer than [maxLength]; then it is split at sentence ends,
 * and as a last resort at spaces.
 */
object TextChunker {
    private val SENTENCE_END = Regex("(?<=[.!?。！？…\"”])\\s+")

    fun chunk(paragraphs: List<String>, maxLength: Int): List<String> =
        paragraphs.flatMap { split(it, maxLength) }

    private fun split(text: String, maxLength: Int): List<String> {
        if (text.length <= maxLength) return listOf(text)

        val pieces = mutableListOf<String>()
        val current = StringBuilder()
        for (sentence in text.split(SENTENCE_END)) {
            if (current.isNotEmpty() && current.length + 1 + sentence.length > maxLength) {
                pieces += current.toString()
                current.setLength(0)
            }
            if (sentence.length > maxLength) {
                pieces += hardSplit(sentence, maxLength)
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(sentence)
            }
        }
        if (current.isNotEmpty()) pieces += current.toString()
        return pieces
    }

    private fun hardSplit(text: String, maxLength: Int): List<String> {
        val pieces = mutableListOf<String>()
        var rest = text
        while (rest.length > maxLength) {
            val cut = rest.lastIndexOf(' ', maxLength).takeIf { it > 0 } ?: maxLength
            pieces += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) pieces += rest
        return pieces
    }
}
