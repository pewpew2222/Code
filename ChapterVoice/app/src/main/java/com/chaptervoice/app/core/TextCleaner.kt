package com.chaptervoice.app.core

/**
 * Makes text easier for TTS to read.
 *
 * Some sites "censor" words by putting dots inside them ("sh.i.p.s", "succ.u.m.b",
 * "w.a.n.g", "pa.s.sed"), which TTS reads as pauses or single letters. Those dots are
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
    private val DOTTED = Regex("(?<![\\p{L}\\p{N}.@/_])(\\p{L}+(?:\\.\\p{L}+)+)(\\.?)(?![\\p{L}\\p{N}@/_])")

    private val ABBREVIATIONS = setOf(
        "a.m", "p.m", "e.g", "i.e", "a.k.a", "r.i.p", "u.s", "u.k", "u.s.a", "n.b", "p.s", "q.e.d",
        "d.c", "l.a", "ph.d", "b.c", "a.d", "m.d", "b.a", "m.a", "b.sc", "m.sc", "o.k", "t.v", "v.s",
        "et.al",
    )

    /** Endings that make a dotted word a web address ("site.com", "novel.co.uk"). */
    private val DOMAIN_ENDINGS = setOf(
        "com", "net", "org", "io", "me", "co", "uk", "info", "site", "app", "dev", "xyz", "tv", "cc", "moe",
    )

    fun clean(text: String): String = DOTTED.replace(text) { match ->
        val word = match.groupValues[1]
        val trailingDot = match.groupValues[2]
        if (isCensored(word)) word.replace(".", "") + trailingDot else match.value
    }

    private fun isCensored(word: String): Boolean {
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
