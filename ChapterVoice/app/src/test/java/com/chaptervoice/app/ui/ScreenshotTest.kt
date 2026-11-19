package com.chaptervoice.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.chaptervoice.app.AppSettings
import com.chaptervoice.app.data.Book
import com.chaptervoice.app.data.ReadingPosition
import com.chaptervoice.app.service.ReaderStatus
import com.chaptervoice.app.service.ReaderUiState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the main screens to PNG files (build/outputs/roborazzi) for a visual check. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val settings get() = AppSettings(ApplicationProvider.getApplicationContext())

    private val state = ReaderUiState(
        status = ReaderStatus.PLAYING,
        chapterUrl = "https://www.empirenovel.com/novel/complete-martial-arts-attributes/34",
        chapterTitle = "Chapter 34: Attack Of A Gigantic Bird",
        position = 96,
        total = 105,
        previousText = "Wang Teng was afraid he would accidentally kill it. That would be a pity.",
        currentText = "When the second lesson ended, during the break, Wang Teng said to Lin Chuhan, “I have to leave. I have something to attend to.”",
        nextText = "After Wang Teng finished speaking, he rushed out of the classroom without waiting for Lin Chuhan’s reply.",
        minutesLeft = 2,
        bookName = "Complete Martial Arts Attributes",
    )

    private fun shoot(name: String, dark: Boolean, accent: String = "blue", content: @Composable () -> Unit) {
        settings.themeMode = if (dark) "dark" else "light"
        settings.accent = accent
        compose.setContent { AppTheme(settings) { Surface(Modifier.fillMaxSize()) { content() } } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun nowPlayingDark() = shoot("now_playing_dark", dark = true) {
        NowPlayingScreen(state, settings, true, true, {}, {}, {}, {}, {})
    }

    @Test fun nowPlayingLightPurple() = shoot("now_playing_light_purple", dark = false, accent = "purple") {
        NowPlayingScreen(state, settings, true, false, {}, {}, {}, {}, {})
    }

    @Test fun libraryDark() = shoot("library_dark", dark = true) {
        Column {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                LibraryScreen(
                    books = listOf(
                        Book("empirenovel.com/novel/complete-martial-arts-attributes", "Complete Martial Arts Attributes", null, true,
                            "https://www.empirenovel.com/novel/complete-martial-arts-attributes/34", "Chapter 34: Attack Of A Gigantic Bird", 2),
                        Book("site.com/book/1", "Shadow Slave", null, true, "https://site.com/book/1/1203", "Chapter 1203: Dark Sea", 1),
                        Book("site.com/book/2", "Lord of Mysteries", null, true, "https://site.com/book/2/88", "Chapter 88: The Clown", 0),
                    ),
                    positions = listOf(ReadingPosition("https://www.empirenovel.com/novel/complete-martial-arts-attributes/34", "", "", 95, 105, 0)),
                    bookmarks = emptyList(),
                    downloads = emptyList(),
                    onContinue = {}, onRemoveBook = {}, onOpenUrl = {}, onSearch = {}, onOpenDownloads = {},
                )
            }
            MiniPlayer(state) {}
        }
    }

    @Test fun libraryEmptyLight() = shoot("library_empty_light", dark = false, accent = "green") {
        LibraryScreen(emptyList(), emptyList(), emptyList(), emptyList(), {}, {}, {}, {}, {})
    }

    @Test fun settingsDark() = shoot("settings_dark", dark = true, accent = "amber") {
        SettingsScreen(settings) {}
    }
}
