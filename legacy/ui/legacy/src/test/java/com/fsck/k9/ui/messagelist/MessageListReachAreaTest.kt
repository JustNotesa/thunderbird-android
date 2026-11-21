package com.fsck.k9.ui.messagelist

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class MessageListReachAreaTest {
    private val testSubject = MessageListReachArea

    @Test
    fun `height() with bottom bar should be a fraction of the list height`() {
        val result = testSubject.height(listHeight = 2000, bottomBarHeight = 150, hasMessages = true)

        assertThat(result).isEqualTo(900)
    }

    @Test
    fun `height() without bottom bar should be zero`() {
        val result = testSubject.height(listHeight = 2000, bottomBarHeight = 0, hasMessages = true)

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `height() without messages should be zero`() {
        val result = testSubject.height(listHeight = 2000, bottomBarHeight = 150, hasMessages = false)

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `height() before the list was laid out should be zero`() {
        val result = testSubject.height(listHeight = 0, bottomBarHeight = 150, hasMessages = true)

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `extraBottomPadding() with short list should fill up the list height`() {
        val result = testSubject.extraBottomPadding(
            listHeight = 2000,
            topPadding = 100,
            contentHeight = 600,
            bottomPadding = 200,
        )

        assertThat(result).isEqualTo(1100)
    }

    @Test
    fun `extraBottomPadding() with long list should be zero`() {
        val result = testSubject.extraBottomPadding(
            listHeight = 2000,
            topPadding = 0,
            contentHeight = 5000,
            bottomPadding = 200,
        )

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `extraBottomPadding() with list that just fills the height should be zero`() {
        val result = testSubject.extraBottomPadding(
            listHeight = 2000,
            topPadding = 0,
            contentHeight = 1800,
            bottomPadding = 200,
        )

        assertThat(result).isEqualTo(0)
    }
}
