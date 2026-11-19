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
        position = 5,
        total = 8,
        previousText = "Wang Teng was afraid he would accidentally kill it. That would be a pity.",
        currentText = "When the second lesson ended, during the break, Wang Teng said to Lin Chuhan, “I have to leave. I have something to attend to.”",
        nextText = "After Wang Teng finished speaking, he rushed out of the classroom without waiting for Lin Chuhan’s reply.",
        minutesLeft = 2,
        pieces = listOf(
            "Chapter 34: Attack Of A Gigantic Bird",
            "Before hatching the egg, Wang Teng planned to pay a visit to the pet shop to ask some questions.",
            "This egg wasn’t ordinary!",
            "Wang Teng was afraid he would accidentally kill it. That would be a pity.",
            "When the second lesson ended, during the break, Wang Teng said to Lin Chuhan, “I have to leave. I have something to attend to.”",
            "After Wang Teng finished speaking, he rushed out of the classroom without waiting for Lin Chuhan’s reply.",
            "Slash!",
            "A gigantic shadow swept across the school grounds.",
        ),
        bookName = "Complete Martial Arts Attributes",
    )

    private fun shoot(name: String, dark: Boolean, accent: String = "blue", content: @Composable () -> Unit) {
        settings.themeMode = if (dark) "dark" else "light"
        settings.accent = accent
        compose.setContent { AppTheme(settings) { Surface(Modifier.fillMaxSize()) { content() } } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun nowPlayingDark() = shoot("now_playing_dark", dark = true) {
        NowPlayingScreen(state, settings, true, true, {}, {}, {}, {}, {}, {})
    }

    @Test fun nowPlayingLightPurple() = shoot("now_playing_light_purple", dark = false, accent = "purple") {
        NowPlayingScreen(state, settings, true, false, {}, {}, {}, {}, {}, {})
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

    @Test fun bottomBarDark() = shoot("bottom_bar_dark", dark = true) {
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom) {
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            MiniPlayer(state) {}
            FloatingNavBar(
                entries = listOf(
                    NavEntry(com.chaptervoice.app.R.drawable.ic_library, "Bibliotek"),
                    NavEntry(com.chaptervoice.app.R.drawable.ic_web, "Webben"),
                    NavEntry(com.chaptervoice.app.R.drawable.ic_download, "Nedladdat"),
                    NavEntry(com.chaptervoice.app.R.drawable.ic_settings, "Inställningar"),
                ),
                selected = 1,
                onSelect = {},
            )
        }
    }

    @Test fun tabsGridDark() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tabs = com.chaptervoice.app.browser.TabManager(context, settings)
        val urls = listOf(
            "https://www.empirenovel.com/novel/complete-martial-arts-attributes/34" to "Complete Martial Arts Attributes - Chapter 34",
            "https://novelfire.net/book/shadow-slave" to "Shadow Slave - Novel Fire",
            "https://www.royalroad.com/fiction/1" to "Royal Road",
            "https://www.novelphoenix.com/" to "Novel Phoenix - Read Light Novels",
        )
        val created = urls.map { (url, title) -> tabs.newTab(url).also { it.title = title } }
        tabs.select(created[1].id)
        shoot("tabs_grid_dark", dark = true, accent = "pink") {
            TabsGrid(tabs, readerTabId = created[0].id, reading = true, onOpen = {}, onNewTab = {})
        }
    }

    @Test fun newTabDark() {
        val now = 0L
        val history = listOf(
            "https://www.empirenovel.com/novel/a/1", "https://www.empirenovel.com/novel/a/2", "https://www.empirenovel.com/novel/b/3",
            "https://www.royalroad.com/fiction/1", "https://www.royalroad.com/fiction/2", "https://novelfire.net/book/x",
            "https://www.novelphoenix.com/", "https://www.scribblehub.com/series/1",
        ).map { com.chaptervoice.app.data.HistoryEntry(it, "", now) }
        val book = Book("empirenovel.com/novel/how-do-we-play", "How Do We Play If This Boss Isnt Nerfed", null, true,
            "https://www.empirenovel.com/novel/how-do-we-play/123", "Chapter 123", 0, caughtUp = true)
        val update = com.chaptervoice.app.data.BookUpdate(book.key, 3, "https://www.empirenovel.com/novel/how-do-we-play/124", "Chapter 124: The Abyss")
        fun item(title: String, site: String, tags: List<String> = emptyList(), summary: String = "") =
            com.chaptervoice.app.core.DiscoverItem(title, "https://$site/novel/" + title.lowercase().replace(' ', '-'), null, site, summary, tags)
        com.chaptervoice.app.data.DiscoverFeed.showOnly(
            com.chaptervoice.app.data.DiscoverState(
                sites = listOf("empirenovel.com", "royalroad.com", "scribblehub.com"),
                items = mapOf(
                    "empirenovel.com" to listOf(item("Shadow Slave", "empirenovel.com"), item("Lord of Mysteries", "empirenovel.com")),
                    "royalroad.com" to listOf(item("The Wandering Inn", "royalroad.com"), item("Beware Of Chicken", "royalroad.com")),
                    "scribblehub.com" to listOf(item("My Vampire System", "scribblehub.com")),
                ),
            ),
        )
        shoot("new_tab_dark", dark = true, accent = "amber") {
            NewTabPage(
                settings = settings,
                history = history,
                books = listOf(book),
                updates = mapOf(book.key to update),
                recentlyClosed = listOf("Shadow Slave - Novel Fire" to "https://novelfire.net/book/shadow-slave"),
                onOpen = {},
                onReadNewChapter = {},
            )
        }
    }

    @Test fun libraryEmptyLight() = shoot("library_empty_light", dark = false, accent = "green") {
        LibraryScreen(emptyList(), emptyList(), emptyList(), emptyList(), {}, {}, {}, {}, {})
    }

    @Test fun settingsDark() = shoot("settings_dark", dark = true, accent = "amber") {
        SettingsScreen(settings, onChanged = {})
    }
}
