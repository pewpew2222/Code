package com.chaptervoice.app.core

import java.text.Normalizer

/**
 * Makes text easier for TTS to read.
 *
 * Some sites "censor" words by putting dots or "+" inside them ("sh.i.p.s", "succ.u.m.b",
 * "w.a.n.g", "pa.s.sed", "shi.+vered"), which TTS reads as pauses or single letters. Those dots are
 * removed, while abbreviations ("a.m.", "e.g.", "U.S.A."), initials ("J.K."),
 * web addresses and decimals are left alone. Words censored in capitals ("S.H.I.T")
 * are joined only when the [dictionary] knows them as a word and not as an acronym.
 */
object TextCleaner {

    /**
     * English word list used for words censored in capitals ("S.H.I.T" -> "SHIT"),
     * which otherwise look exactly like acronyms ("F.B.I"). Loaded by the app at start.
     */
    class Dictionary(private val words: Set<String>, private val acronyms: Set<String>) {
        /** A real word that is not also a known acronym ("pow" vs "POW" stays an acronym). */
        fun isPlainWord(word: String): Boolean {
            val lower = word.lowercase()
            return lower in words && lower !in acronyms
        }

        companion object {
            fun load(words: Sequence<String>, acronyms: Sequence<String>) = Dictionary(
                words.filterNot { it.startsWith("#") || it.isBlank() }.map { it.trim() }.toHashSet(),
                acronyms.filterNot { it.startsWith("#") || it.isBlank() }.map { it.trim() }.toHashSet(),
            )
        }
    }

    @Volatile
    var dictionary: Dictionary? = null

    /**
     * Letters and dots with no spaces, optionally ending in a dot. Hyphens may sit on
     * either side, so each part of "high-cla.s.s" is checked on its own.
     */
    private val DOTTED = Regex("(?<![\\p{L}\\p{N}.@/_+])(\\p{L}+(?:(?:\\.\\+?|\\+)\\p{L}+)+)(\\.?)(?![\\p{L}\\p{N}@/_+])")

    private val ABBREVIATIONS = setOf(
        "a.m", "p.m", "e.g", "i.e", "a.k.a", "r.i.p", "u.s", "u.k", "u.s.a", "n.b", "p.s", "q.e.d",
        "d.c", "l.a", "ph.d", "b.c", "a.d", "m.d", "b.a", "m.a", "b.sc", "m.sc", "o.k", "t.v", "v.s",
        "et.al",
    )

    /** Endings that make a dotted word a web address ("site.com", "novel.co.uk"). */
    private val DOMAIN_ENDINGS = setOf(
        "com", "net", "org", "io", "me", "co", "uk", "info", "site", "app", "dev", "xyz", "tv", "cc", "moe",
    )

    /**
     * Cleans one paragraph. [language] is the chapter's language ("en", ...); accents are
     * only removed from English text.
     */
    fun clean(text: String, language: String? = null): String {
        var result = fixSymbols(text)
        result = fixLookAlikeLetters(result)
        if (language == "en") result = stripAccents(result)
        return removeCensorDots(result)
    }

    private fun removeCensorDots(text: String): String = DOTTED.replace(text) { match ->
        val word = match.groupValues[1]
        val trailingDot = match.groupValues[2]
        if (isCensored(word)) word.replace(".", "").replace("+", "") + trailingDot else match.value
    }

    // ---------------------------------------------------------------- substituted characters

    /** "h£llo", "h€llo" -> "hello", but not prices like "£5". */
    private val CURRENCY_FOR_E = Regex("(?<=\\p{L})[£€](?!\\p{N})|(?<![\\p{N}\\p{Sc}])[£€](?=\\p{L})")

    /** "sh!t", "k!ll" -> "shit", "kill"; "Wow!Amazing" is left alone. */
    private val EXCLAMATION_FOR_I = Regex("(?<=\\p{Ll})!(?=\\p{Ll})")

    private fun fixSymbols(text: String): String =
        EXCLAMATION_FOR_I.replace(CURRENCY_FOR_E.replace(text, "e"), "i")

    /** Greek and Cyrillic letters that look like Latin ones. */
    private val LOOK_ALIKES: Map<Char, Char> = mapOf(
        // Greek
        'α' to 'a', 'β' to 'b', 'ε' to 'e', 'η' to 'n', 'ι' to 'i', 'κ' to 'k', 'ν' to 'v', 'ο' to 'o',
        'ρ' to 'p', 'τ' to 't', 'υ' to 'u', 'χ' to 'x', 'ω' to 'w', 'ϲ' to 'c',
        'Α' to 'A', 'Β' to 'B', 'Ε' to 'E', 'Η' to 'H', 'Ι' to 'I', 'Κ' to 'K', 'Μ' to 'M', 'Ν' to 'N',
        'Ο' to 'O', 'Ρ' to 'P', 'Τ' to 'T', 'Υ' to 'Y', 'Χ' to 'X', 'Ζ' to 'Z',
        // Cyrillic
        'а' to 'a', 'в' to 'b', 'е' to 'e', 'ё' to 'e', 'к' to 'k', 'ќ' to 'k', 'м' to 'm', 'н' to 'h',
        'о' to 'o', 'р' to 'p', 'с' to 'c', 'т' to 't', 'у' to 'y', 'х' to 'x', 'і' to 'i', 'ї' to 'i',
        'ј' to 'j', 'ѕ' to 's', 'ԁ' to 'd', 'ԛ' to 'q', 'ԝ' to 'w', 'һ' to 'h',
        'А' to 'A', 'В' to 'B', 'Е' to 'E', 'К' to 'K', 'Ќ' to 'K', 'М' to 'M', 'Н' to 'H', 'О' to 'O',
        'Р' to 'P', 'С' to 'C', 'Т' to 'T', 'У' to 'Y', 'Х' to 'X', 'І' to 'I', 'Ј' to 'J', 'Ѕ' to 'S',
    )
    private val WORD = Regex("\\p{L}+")

    /**
     * "κill" or "sεcret" (Latin mixed with Greek/Cyrillic) -> "kill", "secret".
     * Words written only in Greek or Cyrillic are real words and are left alone.
     */
    private fun fixLookAlikeLetters(text: String): String = WORD.replace(text) { match ->
        val word = match.value
        val hasLatin = word.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.LATIN }
        val hasOther = word.any { it in LOOK_ALIKES }
        if (hasLatin && hasOther) word.map { LOOK_ALIKES[it] ?: it }.joinToString("") else word
    }

    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    /** "ǩill", "ķill", "Xiǎo" -> "kill", "kill", "Xiao" (English text only). */
    private fun stripAccents(text: String): String {
        if (text.all { it.code < 128 }) return text
        return COMBINING_MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
            .let { Normalizer.normalize(it, Normalizer.Form.NFC) }
    }

    private fun isCensored(word: String): Boolean {
        // A "+" between letters ("shi.+vered", "sh+it") is never normal spelling.
        if ('+' in word) return true
        val parts = word.split('.')
        if (word.lowercase() in ABBREVIATIONS) return false
        // Censoring always leaves at least one single letter between dots.
        if (parts.none { it.length == 1 }) return false
        // Capitals only: "S.H.I.T" is a censored word, but "J.K", "U.S.A" and "F.B.I" are
        // initials and acronyms. Only join when the result is a real, non-acronym word.
        if (parts.all { it.length == 1 && it[0].isUpperCase() }) {
            val joined = parts.joinToString("")
            return joined.length >= 3 && dictionary?.isPlainWord(joined) == true
        }
        // Web addresses: "a.site.com".
        if (parts.last().lowercase() in DOMAIN_ENDINGS && parts.dropLast(1).any { it.length >= 3 }) return false
        return true
    }
}
