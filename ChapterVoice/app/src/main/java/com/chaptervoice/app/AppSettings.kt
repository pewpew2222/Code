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

    var lastUrl: String
        get() = prefs.getString("last_url", null) ?: "https://www.google.com"
        set(value) = prefs.edit().putString("last_url", value).apply()
}
