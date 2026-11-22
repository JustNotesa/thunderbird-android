package com.fsck.k9.ui.messagelist

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
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
        assertThat(testSubject.isRevealed(firstItemOffset = -100, reachHeight = 900))
            .isEqualTo(true)
    }

    @Test
    fun `isRevealed() with first item at its normal place should be false`() {
        assertThat(testSubject.isRevealed(firstItemOffset = -900, reachHeight = 900))
            .isEqualTo(false)
    }

    @Test
    fun `isRevealed() with list scrolled down should be false`() {
        assertThat(testSubject.isRevealed(firstItemOffset = null, reachHeight = 900))
            .isEqualTo(false)
        assertThat(testSubject.isRevealed(firstItemOffset = -1200, reachHeight = 900))
            .isEqualTo(false)
    }

    @Test
    fun `isRevealed() without reach area should be false`() {
        assertThat(testSubject.isRevealed(firstItemOffset = 0, reachHeight = 0))
            .isEqualTo(false)
    }

    @Test
    fun `shouldAnchorBelowReachArea() with reach area shown by the list itself should be true`() {
        val result = testSubject.shouldAnchorBelowReachArea(
            isRevealedByUser = false,
            firstItemOffset = 0,
            reachHeight = 900,
        )

        assertThat(result).isEqualTo(true)
    }

    @Test
    fun `shouldAnchorBelowReachArea() with reach area revealed by user should be false`() {
        val result = testSubject.shouldAnchorBelowReachArea(
            isRevealedByUser = true,
            firstItemOffset = -300,
            reachHeight = 900,
        )

        assertThat(result).isEqualTo(false)
    }

    @Test
    fun `shouldAnchorBelowReachArea() with list scrolled down should be false`() {
        val result = testSubject.shouldAnchorBelowReachArea(
            isRevealedByUser = false,
            firstItemOffset = null,
            reachHeight = 900,
        )

        assertThat(result).isEqualTo(false)
    }

    @Test
    fun `shouldAnchorBelowReachArea() with first item already at its normal place should be false`() {
        val result = testSubject.shouldAnchorBelowReachArea(
            isRevealedByUser = false,
            firstItemOffset = -900,
            reachHeight = 900,
        )

        assertThat(result).isEqualTo(false)
    }

    @Test
    fun `shouldSnapToTop() close to the normal top should be true`() {
        // 300 of 1000 pixels revealed
        assertThat(testSubject.shouldSnapToTop(firstItemOffset = -700, reachHeight = 1000))
            .isEqualTo(true)
    }

    @Test
    fun `shouldSnapToTop() exactly at the snap limit should be true`() {
        assertThat(testSubject.shouldSnapToTop(firstItemOffset = -600, reachHeight = 1000))
            .isEqualTo(true)
    }

    @Test
    fun `shouldSnapToTop() further down than the snap limit should be false`() {
        assertThat(testSubject.shouldSnapToTop(firstItemOffset = -500, reachHeight = 1000))
            .isEqualTo(false)
        assertThat(testSubject.shouldSnapToTop(firstItemOffset = 0, reachHeight = 1000))
            .isEqualTo(false)
    }

    @Test
    fun `shouldSnapToTop() at the normal top should be false`() {
        assertThat(testSubject.shouldSnapToTop(firstItemOffset = -1000, reachHeight = 1000))
            .isEqualTo(false)
    }

    @Test
    fun `shouldSnapToTop() with list scrolled down or without reach area should be false`() {
        assertThat(testSubject.shouldSnapToTop(firstItemOffset = null, reachHeight = 1000))
            .isEqualTo(false)
        assertThat(testSubject.shouldSnapToTop(firstItemOffset = 0, reachHeight = 0))
            .isEqualTo(false)
    }

    @Test
    fun `firstItemOffset() should skip items without height`() {
        // The in-app notification banner list is the first item of the list. It is empty, so it has no height.
        val bounds = listOf(ItemBounds(top = 1000, height = 0), ItemBounds(top = 1000, height = 200))

        val result = testSubject.firstItemOffset(itemCount = 2, paddingTop = 1000) { bounds[it] }

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `firstItemOffset() should be relative to the top padding`() {
        val bounds = listOf(ItemBounds(top = 100, height = 200))

        val result = testSubject.firstItemOffset(itemCount = 1, paddingTop = 1000) { bounds[it] }

        assertThat(result).isEqualTo(-900)
    }

    @Test
    fun `firstItemOffset() with item that isn't displayed should be null`() {
        val bounds = listOf(ItemBounds(top = 1000, height = 0), null)

        val result = testSubject.firstItemOffset(itemCount = 2, paddingTop = 1000) { bounds[it] }

        assertThat(result).isNull()
    }

    @Test
    fun `firstItemOffset() without items should be null`() {
        val result = testSubject.firstItemOffset(itemCount = 0, paddingTop = 1000) { null }

        assertThat(result).isNull()
    }

    @Test
    fun `shouldKeepFirstItemOnTop() with first item at its normal place should be true`() {
        assertThat(testSubject.shouldKeepFirstItemOnTop(false, firstItemOffset = -900, reachHeight = 900))
            .isEqualTo(true)
    }

    @Test
    fun `shouldKeepFirstItemOnTop() with reach area revealed by user should be false`() {
        assertThat(testSubject.shouldKeepFirstItemOnTop(true, firstItemOffset = -300, reachHeight = 900))
            .isEqualTo(false)
    }

    @Test
    fun `shouldKeepFirstItemOnTop() with list scrolled down should be false`() {
        assertThat(testSubject.shouldKeepFirstItemOnTop(false, firstItemOffset = -1000, reachHeight = 900))
            .isEqualTo(false)
        assertThat(testSubject.shouldKeepFirstItemOnTop(false, firstItemOffset = null, reachHeight = 900))
            .isEqualTo(false)
    }

    @Test
    fun `shouldKeepFirstItemOnTop() without reach area should be false`() {
        assertThat(testSubject.shouldKeepFirstItemOnTop(false, firstItemOffset = 0, reachHeight = 0))
            .isEqualTo(false)
    }
}
