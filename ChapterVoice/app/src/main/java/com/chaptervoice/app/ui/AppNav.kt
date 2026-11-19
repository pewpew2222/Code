package com.chaptervoice.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The four parts of the app in the bottom bar. */
enum class Section { LIBRARY, WEB, DOWNLOADS, SETTINGS }

/** Navigation state shared by the whole app. */
class AppNav {
    var section by mutableStateOf(Section.LIBRARY)

    /** The full-screen player is shown on top of everything. */
    var playerOpen by mutableStateOf(false)

    /** Counts taps on the section that is already open (scroll to top, new tab...). */
    val reselects = mutableStateMapOf<Section, Int>()

    fun reselect(section: Section) {
        reselects[section] = (reselects[section] ?: 0) + 1
    }

    /** The browser tab whose page is being read aloud; it follows the reader to new chapters. */
    var readerTabId by mutableLongStateOf(-1L)
}
