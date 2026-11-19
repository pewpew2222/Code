package com.chaptervoice.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ReaderStatus { IDLE, LOADING, PLAYING, PAUSED, FINISHED, ERROR }

data class ReaderUiState(
    val status: ReaderStatus = ReaderStatus.IDLE,
    val chapterUrl: String? = null,
    val chapterTitle: String? = null,
    val position: Int = 0,
    val total: Int = 0,
    val currentText: String = "",
    /** The paragraphs around the current one, for the full-screen player. */
    val previousText: String = "",
    /** All paragraphs of the chapter, as read aloud (for the scrollable text in the player). */
    val pieces: List<String> = emptyList(),
    val nextText: String = "",
    val minutesLeft: Int = 0,
    val bookName: String? = null,
    val coverUrl: String? = null,
    val message: String? = null,
    /** A problem that does not stop reading, e.g. a missing voice. */
    val warning: String? = null,
    /** True while the chapter is read without text rules (exactly as written). */
    val rulesOff: Boolean = false,
    val chaptersRead: Int = 0,
)

/** Shared between the reader service and the UI (same process). */
object ReaderState {
    private val mutable = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = mutable.asStateFlow()

    internal fun update(transform: (ReaderUiState) -> ReaderUiState) = mutable.update(transform)

    /**
     * HTML of the page the user is looking at, handed from the in-app browser to the
     * service. Too large for an Intent extra, so it is passed through memory.
     */
    @Volatile
    internal var pendingPage: Pair<String, String>? = null
}
