package com.chaptervoice.app.core

/**
 * Makes text easier for TTS to read.
 *
 * Some sites "censor" words by putting dots inside them ("sh.i.p.s", "succ.u.m.b",
 * "w.a.n.g", "pa.s.sed"), which TTS reads as pauses or single letters. Those dots are
 * removed, while abbreviations ("a.m.", "e.g.", "U.S.A."), initials ("J.K."),
 * web addresses and decimals are left alone.
 */
object TextCleaner {

    /** Letters and dots with no spaces, optionally ending in a dot. */
    private val DOTTED = Regex("(?<![\\p{L}\\p{N}.@/_-])(\\p{L}+(?:\\.\\p{L}+)+)(\\.?)(?![\\p{L}\\p{N}@/_-])")

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
        // Initials and acronyms: "J.K", "U.S.A", "F.B.I".
        if (parts.all { it.length == 1 && it[0].isUpperCase() }) return false
        // Web addresses: "a.site.com".
        if (parts.last().lowercase() in DOMAIN_ENDINGS && parts.dropLast(1).any { it.length >= 3 }) return false
        return true
    }
}
