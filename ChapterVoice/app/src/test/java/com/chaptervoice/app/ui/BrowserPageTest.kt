package com.chaptervoice.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.chaptervoice.app.AppSettings
import com.chaptervoice.app.browser.TabManager
import com.chaptervoice.app.service.ReaderState
import com.chaptervoice.app.service.ReaderStatus
import com.chaptervoice.app.service.ReaderUiState
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Showing a web page must not crash (the page container used to be looked up by position). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class BrowserPageTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @After fun reset() = ReaderState.update { ReaderUiState() }

    private fun tabsWithPage(): Pair<AppSettings, TabManager> {
        val settings = AppSettings(compose.activity)
        val tabs = TabManager(compose.activity, settings)
        tabs.active.load("https://example.com/novel/x/33")
        return settings to tabs
    }

    @Test fun showsPage() {
        val (settings, tabs) = tabsWithPage()
        compose.setContent { AppTheme(settings) { BrowserPage(tabs.active) } }
        compose.waitForIdle()
    }

    @Test fun playerTitleOpensPage() {
        ReaderState.update {
            it.copy(
                status = ReaderStatus.PLAYING, chapterUrl = "https://example.com/novel/x/34", chapterTitle = "Chapter 34: Bird",
                bookName = "Book X", total = 3, position = 1, pieces = listOf("a", "b", "c"), currentText = "b",
            )
        }
        val (settings, tabs) = tabsWithPage()
        compose.setContent { AppTheme(settings) { AppRoot(settings, tabs, null, {}) } }
        compose.onAllNodesWithText("Chapter 34: Bird")[0].performClick() // mini player
        compose.waitForIdle()
        val titles = compose.onAllNodesWithText("Chapter 34: Bird")
        titles[titles.fetchSemanticsNodes().size - 1].performClick() // title in the full-screen player
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
    }
}
