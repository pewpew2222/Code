package com.chaptervoice.app

import android.content.Context
import com.chaptervoice.app.core.NextMode
import com.chaptervoice.app.core.TextCleaner
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

private val DEFAULT_RULES = listOf(TextCleaner.Replacement("exp", "experience"))

class AppSettings(context: Context) {

    companion object {
        /** Bumped when a text-rules switch changes, so every screen shows the same state. */
        val rulesSwitches = MutableStateFlow(0)

        /** Bumped when the theme or accent colour changes. */
        val themeChanges = MutableStateFlow(0)
    }

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var speechRate: Float
        get() = prefs.getFloat("speech_rate", 1.0f)
        set(value) = prefs.edit().putFloat("speech_rate", value).apply()

    var pitch: Float
        get() = prefs.getFloat("pitch", 1.0f)
        set(value) = prefs.edit().putFloat("pitch", value).apply()

    var nextMode: NextMode
        get() = runCatching { NextMode.valueOf(prefs.getString("next_mode", null)!!) }.getOrDefault(NextMode.AUTO)
        set(value) = prefs.edit().putString("next_mode", value.name).apply()

    var autoContinue: Boolean
        get() = prefs.getBoolean("auto_continue", true)
        set(value) = prefs.edit().putBoolean("auto_continue", value).apply()

    var readTitle: Boolean
        get() = prefs.getBoolean("read_title", true)
        set(value) = prefs.edit().putBoolean("read_title", value).apply()

    /** Stop after this many chapters in a row (0 = never). Works as a sleep timer. */
    var chapterLimit: Int
        get() = prefs.getInt("chapter_limit", 0)
        set(value) = prefs.edit().putInt("chapter_limit", value).apply()

    var adBlock: Boolean
        get() = prefs.getBoolean("ad_block", true)
        set(value) = prefs.edit().putBoolean("ad_block", value).apply()

    fun lastUrlOrNull(): String? = prefs.getString("last_url", null)

    /** Silence after each paragraph, in milliseconds. */
    var paragraphPauseMs: Int
        get() = prefs.getInt("paragraph_pause_ms", 500)
        set(value) = prefs.edit().putInt("paragraph_pause_ms", value).apply()

    /** Extra silence between sentences, in milliseconds (0 = only the voice's own pause). */
    var sentencePauseMs: Int
        get() = prefs.getInt("sentence_pause_ms", 0).coerceIn(0, 300)
        set(value) = prefs.edit().putInt("sentence_pause_ms", value).apply()

    /** Chapters to keep downloaded ahead while reading (0 = off). */
    var preloadCount: Int
        get() = prefs.getInt("preload_count", 3)
        set(value) = prefs.edit().putInt("preload_count", value).apply()

    var deleteAfterRead: Boolean
        get() = prefs.getBoolean("delete_after_read", true)
        set(value) = prefs.edit().putBoolean("delete_after_read", value).apply()

    /** "Say X as Y" rules for the reader. Starts with "exp" -> "experience". */
    var pronunciationRules: List<TextCleaner.Replacement>
        get() = runCatching {
            val json = JSONArray(prefs.getString("pronunciation_rules", null) ?: return DEFAULT_RULES)
            (0 until json.length()).map { i ->
                val o = json.getJSONObject(i)
                TextCleaner.Replacement(o.getString("from"), o.optString("to"))
            }
        }.getOrDefault(DEFAULT_RULES)
        set(value) {
            val json = JSONArray()
            value.forEach { json.put(JSONObject().put("from", it.from).put("to", it.to)) }
            prefs.edit().putString("pronunciation_rules", json.toString()).apply()
        }

    /** Read "No. 1" as "number 1". */
    var numberRule: Boolean
        get() = prefs.getBoolean("number_rule", true)
        set(value) = prefs.edit().putBoolean("number_rule", value).apply()

    /**
     * Master switch for all text rules (censor fixes, symbol and emoticon filters,
     * watermark removal, pronunciation rules). Off = read the text exactly as written.
     * Only a switch: the rules themselves are never touched.
     */
    var textRulesEnabled: Boolean
        get() = prefs.getBoolean("text_rules_enabled", true)
        set(value) {
            prefs.edit().putBoolean("text_rules_enabled", value).apply()
            rulesSwitches.value++
        }

    /** The same switch for one book; off means that book is read exactly as written. */
    fun bookRulesEnabled(bookKey: String): Boolean =
        bookKey !in (prefs.getStringSet("book_rules_off", emptySet()) ?: emptySet())

    fun setBookRulesEnabled(bookKey: String, enabled: Boolean) {
        val off = (prefs.getStringSet("book_rules_off", emptySet()) ?: emptySet()).toMutableSet()
        if (enabled) off.remove(bookKey) else off.add(bookKey)
        prefs.edit().putStringSet("book_rules_off", off).apply()
        rulesSwitches.value++
    }

    /** Whether text rules are used for a chapter of [bookKey]. */
    fun rulesActive(bookKey: String?): Boolean =
        textRulesEnabled && (bookKey == null || bookRulesEnabled(bookKey))

    /** Rules for one book (see BookKey); they apply before the general rules. */
    fun bookRules(bookKey: String): List<TextCleaner.Replacement> =
        allBookRules()[bookKey].orEmpty()

    fun setBookRules(bookKey: String, rules: List<TextCleaner.Replacement>) {
        val all = allBookRules().toMutableMap()
        if (rules.isEmpty()) all.remove(bookKey) else all[bookKey] = rules
        val json = JSONObject()
        all.forEach { (key, list) ->
            json.put(key, JSONArray().apply { list.forEach { put(JSONObject().put("from", it.from).put("to", it.to)) } })
        }
        prefs.edit().putString("book_rules", json.toString()).apply()
    }

    /** Books that have their own rules, with the number of rules. */
    fun booksWithRules(): List<Pair<String, Int>> =
        allBookRules().map { it.key to it.value.size }.sortedBy { it.first }

    private fun allBookRules(): Map<String, List<TextCleaner.Replacement>> = runCatching {
        val json = JSONObject(prefs.getString("book_rules", "{}")!!)
        json.keys().asSequence().associateWith { key ->
            val arr = json.getJSONArray(key)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TextCleaner.Replacement(o.getString("from"), o.optString("to"))
            }
        }
    }.getOrDefault(emptyMap())

    /** The rules for reading a chapter of [bookKey]: the book's own first, then the general ones. */
    fun pronunciation(bookKey: String? = null) = TextCleaner.Pronunciation(
        bookKey?.let { bookRules(it) }.orEmpty() + pronunciationRules,
        numberRule,
    )

    /** "system", "light" or "dark". */
    var themeMode: String
        get() = prefs.getString("theme_mode", "system") ?: "system"
        set(value) {
            prefs.edit().putString("theme_mode", value).apply()
            themeChanges.value++
        }

    /** Accent colour id (see AppTheme), or "dynamic" for the phone's own colours. */
    var accent: String
        get() = prefs.getString("accent", "blue") ?: "blue"
        set(value) {
            prefs.edit().putString("accent", value).apply()
            themeChanges.value++
        }

    /** Sites pinned to / hidden from "most visited" on the start page. */
    var pinnedSites: Set<String>
        get() = prefs.getStringSet("pinned_sites", emptySet()).orEmpty().toSet()
        set(value) = prefs.edit().putStringSet("pinned_sites", value).apply()
    var hiddenSites: Set<String>
        get() = prefs.getStringSet("hidden_sites", emptySet()).orEmpty().toSet()
        set(value) = prefs.edit().putStringSet("hidden_sites", value).apply()

    /** TTS engine package, e.g. SherpaTTS ("" = the phone's default engine). */
    var ttsEngine: String
        get() = prefs.getString("tts_engine", "") ?: ""
        set(value) = prefs.edit().putString("tts_engine", value).apply()

    /** "auto" (from the page) or a language tag like "en". */
    var ttsLanguage: String
        get() = prefs.getString("tts_language", "auto") ?: "auto"
        set(value) = prefs.edit().putString("tts_language", value).apply()

    /** Chosen voice per language ("" = the engine's default voice for it). */
    fun ttsVoice(language: String): String = prefs.getString("tts_voice_$language", "") ?: ""
    fun setTtsVoice(language: String, voice: String) = prefs.edit().putString("tts_voice_$language", voice).apply()

    var removeOverlays: Boolean
        get() = prefs.getBoolean("remove_overlays", true)
        set(value) = prefs.edit().putBoolean("remove_overlays", value).apply()

    var lastUrl: String
        get() = prefs.getString("last_url", null) ?: "https://www.google.com"
        set(value) = prefs.edit().putString("last_url", value).apply()
}
