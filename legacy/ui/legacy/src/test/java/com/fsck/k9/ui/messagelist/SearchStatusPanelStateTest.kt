package com.fsck.k9.ui.messagelist

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.fsck.k9.ui.messagelist.ServerSearchStatus.Result
import org.junit.Test

class SearchStatusPanelStateTest {
    @Test
    fun `placement with one-handed mode should be the bottom panel`() {
        val result = SearchStatusPlacement.of(areBannersAtBottom = true)

        assertThat(result).isEqualTo(SearchStatusPlacement.BOTTOM_PANEL)
    }

    @Test
    fun `placement without one-handed mode should be the list`() {
        val result = SearchStatusPlacement.of(areBannersAtBottom = false)

        assertThat(result).isEqualTo(SearchStatusPlacement.LIST_ITEM)
    }

    @Test
    fun `running search should display its title and what it has found`() {
        val status = ServerSearchStatus.Running(title = "Server search: folder 3 of 10", detail = "2 messages found")

        val result = SearchStatusPanelState.of(status)

        assertThat(result.text).isEqualTo("Server search: folder 3 of 10 · 2 messages found")
        assertThat(result.isRunning).isTrue()
        assertThat(result.result).isNull()
    }

    @Test
    fun `running search should display what it is doing instead of what it has found`() {
        val status = ServerSearchStatus.Running(
            title = "Server search: folder 3 of 10",
            detail = "2 messages found",
            activity = "Connecting to the server… 5 s",
        )

        val result = SearchStatusPanelState.of(status)

        assertThat(result.text).isEqualTo("Server search: folder 3 of 10 · Connecting to the server… 5 s")
    }

    @Test
    fun `running search without details should only display its title`() {
        val result = SearchStatusPanelState.of(ServerSearchStatus.Running(title = "Sending query to server"))

        assertThat(result.text).isEqualTo("Sending query to server")
        assertThat(result.hasButtons).isFalse()
    }

    @Test
    fun `running search should offer to search the contents right away`() {
        val status = ServerSearchStatus.Running(title = "Searching", isMessageContentsSearchOffered = true)

        val result = SearchStatusPanelState.of(status)

        assertThat(result.isContentsButtonVisible).isTrue()
        assertThat(result.isAttachmentsButtonVisible).isFalse()
    }

    @Test
    fun `ended search should display the problem and its reason`() {
        val status = ServerSearchStatus.Ended(title = "Search failed", result = Result.PROBLEM, message = "Timeout")

        val result = SearchStatusPanelState.of(status)

        assertThat(result.text).isEqualTo("Search failed · Timeout")
        assertThat(result.isRunning).isFalse()
        assertThat(result.result).isEqualTo(Result.PROBLEM)
    }

    @Test
    fun `ended search with a blank message should only display its title`() {
        val status = ServerSearchStatus.Ended(title = "Finished", result = Result.COMPLETE, message = " ")

        val result = SearchStatusPanelState.of(status)

        assertThat(result.text).isEqualTo("Finished")
    }

    @Test
    fun `ended search should show the buttons that are offered`() {
        val status = ServerSearchStatus.Ended(
            title = "Finished",
            result = Result.COMPLETE,
            isMessageContentsSearchOffered = true,
            isAttachmentSearchOffered = true,
        )

        val result = SearchStatusPanelState.of(status)

        assertThat(result.isContentsButtonVisible).isTrue()
        assertThat(result.isAttachmentsButtonVisible).isTrue()
        assertThat(result.hasButtons).isTrue()
    }

    @Test
    fun `ended search should only show the attachments button when only that is offered`() {
        val status = ServerSearchStatus.Ended(
            title = "Finished",
            result = Result.COMPLETE,
            isAttachmentSearchOffered = true,
        )

        val result = SearchStatusPanelState.of(status)

        assertThat(result.isContentsButtonVisible).isFalse()
        assertThat(result.isAttachmentsButtonVisible).isTrue()
    }

    @Test
    fun `ended search without offers should not show a row of buttons`() {
        val status = ServerSearchStatus.Ended(title = "Finished", result = Result.COMPLETE)

        val result = SearchStatusPanelState.of(status)

        assertThat(result.hasButtons).isFalse()
    }
}
