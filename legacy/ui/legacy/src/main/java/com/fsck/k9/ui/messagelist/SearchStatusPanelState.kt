package com.fsck.k9.ui.messagelist

/**
 * Where the state of the server search is displayed.
 */
internal enum class SearchStatusPlacement {
    /** As the first row of the search results. */
    LIST_ITEM,

    /** In the bottom area, above the search filters, so the list of results starts with the first hit. */
    BOTTOM_PANEL,
    ;

    companion object {
        /**
         * The state moves to the bottom together with the toolbar and the search filters (one-handed mode). Otherwise
         * those are at the top, and so is the state.
         */
        fun of(areBannersAtBottom: Boolean): SearchStatusPlacement {
            return if (areBannersAtBottom) BOTTOM_PANEL else LIST_ITEM
        }
    }
}

/**
 * What the compact panel in the bottom area displays for a [ServerSearchStatus]: one line of text and, when they are
 * offered, the buttons for searching the contents and the attachments of the messages.
 *
 * @param isContentsButtonVisible While the search is running this skips the rest of it and searches the contents
 *   right away.
 */
internal data class SearchStatusPanelState(
    val text: String,
    val isRunning: Boolean,
    val result: ServerSearchStatus.Result?,
    val isContentsButtonVisible: Boolean,
    val isAttachmentsButtonVisible: Boolean,
) {
    val hasButtons: Boolean get() = isContentsButtonVisible || isAttachmentsButtonVisible

    companion object {
        private const val SEPARATOR = " · "

        fun of(status: ServerSearchStatus): SearchStatusPanelState {
            return when (status) {
                is ServerSearchStatus.Running -> SearchStatusPanelState(
                    text = join(status.title, status.activity ?: status.detail),
                    isRunning = true,
                    result = null,
                    isContentsButtonVisible = status.isMessageContentsSearchOffered,
                    isAttachmentsButtonVisible = false,
                )

                is ServerSearchStatus.Ended -> SearchStatusPanelState(
                    text = join(status.title, status.message),
                    isRunning = false,
                    result = status.result,
                    isContentsButtonVisible = status.isMessageContentsSearchOffered,
                    isAttachmentsButtonVisible = status.isAttachmentSearchOffered,
                )
            }
        }

        private fun join(title: String, detail: String?): String {
            return listOfNotNull(title, detail).filter { it.isNotBlank() }.joinToString(SEPARATOR)
        }
    }
}
