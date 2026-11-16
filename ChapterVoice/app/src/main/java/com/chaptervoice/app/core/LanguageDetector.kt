package com.chaptervoice.app.core

/**
 * Guesses the language of a chapter so TTS can use a matching voice.
 * The text decides when it is clear; otherwise the page's own `lang` attribute is used.
 */
object LanguageDetector {

    /** Common words that are typical for English and rare in other Latin-script languages. */
    private val ENGLISH_WORDS = setOf(
        "the", "and", "of", "to", "was", "is", "he", "she", "it", "you", "that", "his", "her", "with",
        "for", "they", "said", "had", "were", "this", "what", "but",
    )

    /** Returns a language tag like "en", "zh", "ko", or null if unsure. */
    fun detect(langAttribute: String?, paragraphs: List<String>): String? {
        val declared = langAttribute?.trim()?.substringBefore('-')?.substringBefore('_')?.lowercase()
        val fromText = fromText(paragraphs)
        // Sites often keep a template's lang attribute whatever the text is, so clear
        // evidence from the text wins; the attribute decides when the text is unclear.
        return fromText ?: declared?.takeIf { it.length in 2..3 }
    }

    fun fromText(paragraphs: List<String>): String? {
        val sample = paragraphs.joinToString(" ").take(4000)
        if (sample.isBlank()) return null

        var hangul = 0
        var kana = 0
        var han = 0
        var cyrillic = 0
        var latin = 0
        for (ch in sample) {
            when (Character.UnicodeScript.of(ch.code)) {
                Character.UnicodeScript.HANGUL -> hangul++
                Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> kana++
                Character.UnicodeScript.HAN -> han++
                Character.UnicodeScript.CYRILLIC -> cyrillic++
                Character.UnicodeScript.LATIN -> latin++
                else -> Unit
            }
        }
        val letters = hangul + kana + han + cyrillic + latin
        if (letters == 0) return null
        return when {
            hangul > letters * 0.3 -> "ko"
            kana > letters * 0.1 -> "ja"
            han > letters * 0.3 -> "zh"
            cyrillic > letters * 0.3 -> "ru"
            latin > letters * 0.5 -> if (looksEnglish(sample)) "en" else null
            else -> null
        }
    }

    private fun looksEnglish(sample: String): Boolean {
        val words = sample.lowercase().split(Regex("[^\\p{L}']+")).filter { it.isNotEmpty() }
        if (words.size < 5) return false
        val common = words.count { it in ENGLISH_WORDS }
        return common >= words.size * 0.08
    }
}
