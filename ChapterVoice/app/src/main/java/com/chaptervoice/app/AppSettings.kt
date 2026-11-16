package com.chaptervoice.app

import android.content.Context
import com.chaptervoice.app.core.NextMode

class AppSettings(context: Context) {
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
