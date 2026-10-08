package com.fsck.k9.ui.messagelist

/**
 * What the search results display about the search on the server.
 */
sealed interface ServerSearchStatus {
    val title: String

    /**
     * @param detail What the search has found so far.
     * @param activity What the search is doing at the moment. It is displayed instead of [detail] when the search is
     *   being held up, so it doesn't look like it is stuck.
     */
    data class Running(
        override val title: String,
        val progress: Progress? = null,
        val detail: String? = null,
        val activity: String? = null,
    ) : ServerSearchStatus

    data class Progress(
        val folderIndex: Int,
        val folderCount: Int,
        val folderLabel: String,
    )

    data class Ended(
        override val title: String,
        val result: Result,
        val message: String? = null,
        val isMessageContentsSearchOffered: Boolean = false,
    ) : ServerSearchStatus

    enum class Result {
        COMPLETE,
        STOPPED,
        PROBLEM,
    }
}
