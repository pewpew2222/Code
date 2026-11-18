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
    val message: String? = null,
    /** A problem that does not stop reading, e.g. a missing voice. */
    val warning: String? = null,
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
