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
        fun isWord(word: String): Boolean = word.lowercase() in words

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
    fun clean(
        text: String,
        language: String? = null,
        pronunciation: Pronunciation = Pronunciation.NONE,
    ): String {
        var result = removeEmoticons(text)
        result = fixSymbols(result)
        result = fixLookAlikeLetters(result)
        if (language == "en") result = toPlainLatin(result)
        result = removeCensorDots(result)
        result = removeWatermarks(result)
        return pronunciation.apply(result)
    }

    // ---------------------------------------------------------------- pronunciation

    /** A word to say differently: "exp" -> "experience". Matches whole words, any case. */
    data class Replacement(val from: String, val to: String)

    /**
     * The user's pronunciation rules, plus "No. 1" -> "number 1" (a number after
     * "No." means "number"; without one, "No." is left to the voice, as it usually means "no").
     */
    class Pronunciation(
        replacements: List<Replacement>,
        private val numberRule: Boolean,
        private val levelRule: Boolean = false,
    ) {
        private val rules = replacements
            .filter { it.from.isNotBlank() }
            .map { Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(it.from.trim()) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE) to it.to }

        fun apply(text: String): String {
            var result = if (numberRule) NUMBER_AFTER_NO.replace(text, "number ") else text
            if (levelRule) result = LEVEL.replace(result, "level ")
            for ((regex, to) in rules) result = regex.replace(result, Regex.escapeReplacement(to))
            return result
        }

        companion object {
            val NONE = Pronunciation(emptyList(), numberRule = false)
            private val NUMBER_AFTER_NO = Regex("(?<![\\p{L}])[Nn][Oo]\\.\\s*(?=\\d)")

            /** "LV.5", "Lv. 5", "Lvl.5" before a number. */
            private val LEVEL = Regex("(?<![\\p{L}])[Ll][Vv][Ll]?\\.\\s*(?=\\d)")
        }
    }

    // ---------------------------------------------------------------- emoticons

    /** A short bracketed group: "(〒﹏〒)", "（╯°□°）", "(^_^)". */
    private val BRACKETED = Regex("[(（]([^()（）]{1,25})[)）]")

    /** Western and Asian text emoticons written on their own: "^_^", "T_T", ">_<", ":D". */
    private val EMOTICON = Regex(
        "(?<!\\S)(?:\\^_*\\^|[Tt]_+[Tt]|[Tt]\\.[Tt]|>_+<|-_+-|[oO0]_+[oO0]|[Qq][Aa][Qq]|[Qq][wW][Qq]|" +
            "[UuOo][wW][UuOo]|:-?[)(DPp]|;-?\\)|xD|XD|<3)(?!\\S)",
    )
    private val TILDE = Regex("[~～〜]+")
    private val LETTER_OR_DIGIT = Regex("[\\p{L}\\p{N}]")

    /**
     * Kaomoji such as "(〒﹏〒)" and "(╯°□°)╯", which TTS would read symbol by symbol.
     * A bracketed group is a kaomoji when it is mostly symbols (at most two letters),
     * so normal brackets like "(he said quietly)" stay.
     */
    private fun removeEmoticons(text: String): String {
        var result = BRACKETED.replace(text) { match ->
            val inner = match.groupValues[1]
            val letters = inner.count { it.isLetterOrDigit() }
            val symbols = inner.count { !it.isLetterOrDigit() && !it.isWhitespace() }
            if (letters <= 2 && symbols >= 1) "" else match.value
        }
        result = EMOTICON.replace(result, "")
        result = TILDE.replace(result, "")
        // Leftover arm/table pieces around a removed kaomoji, e.g. "╯︵ ┻━┻".
        if (!LETTER_OR_DIGIT.containsMatchIn(result)) return ""
        return result.replace(Regex("\\s{2,}"), " ").trim()
    }

    // ---------------------------------------------------------------- watermarks

    /** Novel sites that stamp their name into copied chapters ("ɼａＮóＢÈs" -> "raNoBEs"). */
    private val WATERMARK_SITES = listOf(
        "ranobes", "novelbin", "novelfull", "readnovelfull", "lightnovelpub", "lightnovelworld",
        "lightnovelcave", "lightnovelspot", "novelfire", "freewebnovel", "novelbuddy", "mvlempyr",
        "empirenovel", "boxnovel", "novelhall", "pandanovel", "readlightnovel", "novelnext", "noveltrust",
        "novelusb", "novelfever", "allnovelfull", "novelbjn", "lnreader",
    )
    private val WATERMARK = Regex(
        "\\b(?:" + WATERMARK_SITES.joinToString("|") + ")(?:\\s*[.,]\\s*(?:com|net|org|me|io|co|cc|site|info))?\\b",
        RegexOption.IGNORE_CASE,
    )
    private const val WATERMARK_SENTENCE_MAX = 120
    private val SENTENCE_BREAK = Regex("(?<=[.!?。！？…\"”])\\s+")

    /** Drops short sentences that name such a site; in longer ones only the name goes. */
    private fun removeWatermarks(text: String): String {
        if (!WATERMARK.containsMatchIn(text)) return text
        // A plain split at sentence ends: a stamp like "hand. ranobes" must stay its own part.
        return text.split(SENTENCE_BREAK)
            .mapNotNull { sentence ->
                when {
                    !WATERMARK.containsMatchIn(sentence) -> sentence
                    sentence.length <= WATERMARK_SENTENCE_MAX -> null
                    else -> WATERMARK.replace(sentence, "").replace(Regex("\\s{2,}"), " ").trim()
                }
            }
            .joinToString(" ")
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

    /**
     * Censor noise after a word: "Slash.!.+" -> "Slash!", "What.?.+" -> "What?".
     * Only runs at the end of a word that mix "+" with . ! ? are touched, so "C++",
     * "A+" and in-word censoring like "shi.+vered" (handled later) are left to other rules.
     */
    private val PLUS_NOISE = Regex("(?<=\\p{L})[.!?+]*\\+[.!?+]*(?!\\p{L})")

    private fun fixPlusNoise(text: String): String = PLUS_NOISE.replace(text) { match ->
        val run = match.value
        when {
            run.none { it == '.' || it == '!' || it == '?' } -> run // "C++", "A+"
            '!' in run -> "!"
            '?' in run -> "?"
            else -> "."
        }
    }

    private fun fixSymbols(text: String): String =
        EXCLAMATION_FOR_I.replace(CURRENCY_FOR_E.replace(fixPlusNoise(text), "e"), "i")

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

    /**
     * Latin letters that are separate characters rather than a letter plus a mark, so
     * Unicode normalisation does not simplify them ("ɓreasts", "đead", "ħell").
     */
    private val VARIANT_LETTERS: Map<Char, Char> = mapOf(
        'ɓ' to 'b', 'ƀ' to 'b', 'ƃ' to 'b', 'ƅ' to 'b', 'ɗ' to 'd', 'ƌ' to 'd', 'đ' to 'd', 'ɖ' to 'd',
        'ɇ' to 'e', 'ǝ' to 'e', 'ɛ' to 'e', 'ƒ' to 'f', 'ɠ' to 'g', 'ǥ' to 'g', 'ɡ' to 'g', 'ħ' to 'h',
        'ɦ' to 'h', 'ı' to 'i', 'ɨ' to 'i', 'ɩ' to 'i', 'ȷ' to 'j', 'ɉ' to 'j', 'ƙ' to 'k', 'ꝁ' to 'k',
        'ł' to 'l', 'ŀ' to 'l', 'ƚ' to 'l', 'ɫ' to 'l', 'ɱ' to 'm', 'ɲ' to 'n', 'ƞ' to 'n', 'ŋ' to 'n',
        'ø' to 'o', 'ɵ' to 'o', 'ɔ' to 'o', 'ƥ' to 'p', 'ɋ' to 'q', 'ɍ' to 'r', 'ɽ' to 'r', 'ɼ' to 'r', 'ɾ' to 'r', 'ɹ' to 'r', 'ʀ' to 'r', 'ʂ' to 's',
        'ŧ' to 't', 'ƭ' to 't', 'ʈ' to 't', 'ʉ' to 'u', 'ʋ' to 'v', 'ʌ' to 'v', 'ɏ' to 'y', 'ƴ' to 'y',
        'ƶ' to 'z', 'ʐ' to 'z', 'ȥ' to 'z', 'ƈ' to 'c', 'ȼ' to 'c',
        'Ɓ' to 'B', 'Ƀ' to 'B', 'Ɗ' to 'D', 'Đ' to 'D', 'Ɇ' to 'E', 'Ɛ' to 'E', 'Ƒ' to 'F', 'Ɠ' to 'G',
        'Ǥ' to 'G', 'Ħ' to 'H', 'Ɨ' to 'I', 'Ɉ' to 'J', 'Ƙ' to 'K', 'Ł' to 'L', 'Ŋ' to 'N', 'Ø' to 'O',
        'Ƥ' to 'P', 'Ɍ' to 'R', 'Ŧ' to 'T', 'Ƭ' to 'T', 'Ʉ' to 'U', 'Ʋ' to 'V', 'Ɏ' to 'Y', 'Ƴ' to 'Y',
        'Ƶ' to 'Z', 'Ȼ' to 'C',
    )

    /** Letters that can stand for more than one letter; the dictionary picks the real word. */
    private val ALTERNATIVES: Map<Char, Char> = mapOf('ŋ' to 'm', 'Ŋ' to 'M')

    /**
     * English text only: "ǩill", "ɓreasts", "𝐤𝐢𝐥𝐥", "ｋｉｌｌ", "Xiǎo" -> "kill", "breasts",
     * "kill", "kill", "Xiao".
     */
    private fun toPlainLatin(text: String): String {
        if (text.all { it.code < 128 }) return text
        val words = WORD.replace(text) { match -> plainWord(match.value) }
        // NFKD also turns fullwidth and "fancy font" letters (𝐤, ｋ) into plain ones.
        return COMBINING_MARKS.replace(Normalizer.normalize(words, Normalizer.Form.NFKD), "")
            .let { Normalizer.normalize(it, Normalizer.Form.NFC) }
    }

    private fun plainWord(word: String): String {
        if (word.none { it in VARIANT_LETTERS }) return word
        val first = word.map { VARIANT_LETTERS[it] ?: it }.joinToString("")
        val dict = dictionary
        if (dict == null || dict.isWord(first) || word.none { it in ALTERNATIVES }) return first
        val second = word.map { ALTERNATIVES[it] ?: VARIANT_LETTERS[it] ?: it }.joinToString("")
        return if (dict.isWord(second)) second else first
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
