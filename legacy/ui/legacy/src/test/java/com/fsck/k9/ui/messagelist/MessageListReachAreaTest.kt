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

    @Test
    fun `isRevealed() with first item pulled down should be true`() {
        assertThat(testSubject.isRevealed(firstVisiblePosition = 0, firstItemOffset = -100, reachHeight = 900))
            .isEqualTo(true)
    }

    @Test
    fun `isRevealed() with first item at its normal place should be false`() {
        assertThat(testSubject.isRevealed(firstVisiblePosition = 0, firstItemOffset = -900, reachHeight = 900))
            .isEqualTo(false)
    }

    @Test
    fun `isRevealed() with list scrolled down should be false`() {
        assertThat(testSubject.isRevealed(firstVisiblePosition = 3, firstItemOffset = null, reachHeight = 900))
            .isEqualTo(false)
        assertThat(testSubject.isRevealed(firstVisiblePosition = 0, firstItemOffset = -1200, reachHeight = 900))
            .isEqualTo(false)
    }

    @Test
    fun `isRevealed() without reach area should be false`() {
        assertThat(testSubject.isRevealed(firstVisiblePosition = 0, firstItemOffset = 0, reachHeight = 0))
            .isEqualTo(false)
    }

    @Test
    fun `shouldAnchorBelowReachArea() with reach area shown by the list itself should be true`() {
        val result = testSubject.shouldAnchorBelowReachArea(
            isRevealedByUser = false,
            firstVisiblePosition = 0,
            firstItemOffset = 0,
            reachHeight = 900,
        )

        assertThat(result).isEqualTo(true)
    }

    @Test
    fun `shouldAnchorBelowReachArea() with reach area revealed by user should be false`() {
        val result = testSubject.shouldAnchorBelowReachArea(
            isRevealedByUser = true,
            firstVisiblePosition = 0,
            firstItemOffset = -300,
            reachHeight = 900,
        )

        assertThat(result).isEqualTo(false)
    }

    @Test
    fun `shouldAnchorBelowReachArea() with list scrolled down should be false`() {
        val result = testSubject.shouldAnchorBelowReachArea(
            isRevealedByUser = false,
            firstVisiblePosition = 50,
            firstItemOffset = null,
            reachHeight = 900,
        )

        assertThat(result).isEqualTo(false)
    }

    @Test
    fun `shouldAnchorBelowReachArea() with first item already at its normal place should be false`() {
        val result = testSubject.shouldAnchorBelowReachArea(
            isRevealedByUser = false,
            firstVisiblePosition = 0,
            firstItemOffset = -900,
            reachHeight = 900,
        )

        assertThat(result).isEqualTo(false)
    }

    @Test
    fun `shouldSnapToTop() close to the normal top should be true`() {
        // 300 of 1000 pixels revealed
        assertThat(testSubject.shouldSnapToTop(firstVisiblePosition = 0, firstItemOffset = -700, reachHeight = 1000))
            .isEqualTo(true)
    }

    @Test
    fun `shouldSnapToTop() exactly at the snap limit should be true`() {
        assertThat(testSubject.shouldSnapToTop(firstVisiblePosition = 0, firstItemOffset = -600, reachHeight = 1000))
            .isEqualTo(true)
    }

    @Test
    fun `shouldSnapToTop() further down than the snap limit should be false`() {
        assertThat(testSubject.shouldSnapToTop(firstVisiblePosition = 0, firstItemOffset = -500, reachHeight = 1000))
            .isEqualTo(false)
        assertThat(testSubject.shouldSnapToTop(firstVisiblePosition = 0, firstItemOffset = 0, reachHeight = 1000))
            .isEqualTo(false)
    }

    @Test
    fun `shouldSnapToTop() at the normal top should be false`() {
        assertThat(testSubject.shouldSnapToTop(firstVisiblePosition = 0, firstItemOffset = -1000, reachHeight = 1000))
            .isEqualTo(false)
    }

    @Test
    fun `shouldSnapToTop() with list scrolled down or without reach area should be false`() {
        assertThat(testSubject.shouldSnapToTop(firstVisiblePosition = 4, firstItemOffset = null, reachHeight = 1000))
            .isEqualTo(false)
        assertThat(testSubject.shouldSnapToTop(firstVisiblePosition = 0, firstItemOffset = 0, reachHeight = 0))
            .isEqualTo(false)
    }
}
