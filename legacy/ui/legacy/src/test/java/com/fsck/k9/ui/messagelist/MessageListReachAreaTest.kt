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
    fun `estimateContentHeight() with all items laid out should be their height`() {
        val result = estimateContentHeight(
            itemCount = 5,
            firstLaidOut = 0,
            lastLaidOut = 4,
            laidOutHeight = 1200,
        )

        assertThat(result).isEqualTo(1200)
    }

    @Test
    fun `estimateContentHeight() with some items laid out should extrapolate to all items`() {
        val result = estimateContentHeight(
            itemCount = 20,
            firstLaidOut = 5,
            lastLaidOut = 9,
            laidOutHeight = 1000,
        )

        assertThat(result).isEqualTo(4000)
    }

    @Test
    fun `estimateContentHeight() with no items laid out should be zero`() {
        val result = estimateContentHeight(
            itemCount = 20,
            firstLaidOut = 0,
            lastLaidOut = -1,
            laidOutHeight = 0,
        )

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `extraBottomPadding() with long list measured by its laid out items should be zero`() {
        val contentHeight = estimateContentHeight(
            itemCount = 22,
            firstLaidOut = 18,
            lastLaidOut = 21,
            laidOutHeight = 960,
        )

        val result = testSubject.extraBottomPadding(
            listHeight = 2326,
            topPadding = 0,
            contentHeight = contentHeight,
            bottomPadding = 471,
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
            isScrolledAwayByUser = false,
            firstItemOffset = 0,
            reachHeight = 900,
        )

        assertThat(result).isEqualTo(true)
    }

    @Test
    fun `shouldAnchorBelowReachArea() with reach area revealed by user should be false`() {
        val result = testSubject.shouldAnchorBelowReachArea(
            isRevealedByUser = true,
            isScrolledAwayByUser = false,
            firstItemOffset = -300,
            reachHeight = 900,
        )

        assertThat(result).isEqualTo(false)
    }

    @Test
    fun `shouldAnchorBelowReachArea() with list scrolled down by the user should be false`() {
        val result = testSubject.shouldAnchorBelowReachArea(
            isRevealedByUser = false,
            isScrolledAwayByUser = true,
            firstItemOffset = null,
            reachHeight = 900,
        )

        assertThat(result).isEqualTo(false)
    }

    @Test
    fun `shouldAnchorBelowReachArea() with first item already at its normal place should be false`() {
        val result = testSubject.shouldAnchorBelowReachArea(
            isRevealedByUser = false,
            isScrolledAwayByUser = false,
            firstItemOffset = -900,
            reachHeight = 900,
        )

        assertThat(result).isEqualTo(false)
    }

    @Test
    fun `restPositionAfterRelease() with list pushed up less than the threshold should be open`() {
        val result = testSubject.restPositionAfterRelease(
            firstItemOffset = -100,
            reachHeight = 900,
            brokeThroughDetent = false,
            threshold = 160f,
        )

        assertThat(result).isEqualTo(RestPosition.OPEN)
    }

    @Test
    fun `restPositionAfterRelease() with list pushed up exactly the threshold should be open`() {
        val result = testSubject.restPositionAfterRelease(
            firstItemOffset = -160,
            reachHeight = 900,
            brokeThroughDetent = false,
            threshold = 160f,
        )

        assertThat(result).isEqualTo(RestPosition.OPEN)
    }

    @Test
    fun `restPositionAfterRelease() with list pushed up more than the threshold should be docked`() {
        val result = testSubject.restPositionAfterRelease(
            firstItemOffset = -161,
            reachHeight = 900,
            brokeThroughDetent = false,
            threshold = 160f,
        )

        assertThat(result).isEqualTo(RestPosition.DOCKED)
    }

    @Test
    fun `restPositionAfterRelease() with list pushed up almost all the way should be docked`() {
        val result = testSubject.restPositionAfterRelease(
            firstItemOffset = -899,
            reachHeight = 900,
            brokeThroughDetent = false,
            threshold = 160f,
        )

        assertThat(result).isEqualTo(RestPosition.DOCKED)
    }

    @Test
    fun `restPositionAfterRelease() after breaking through the detent should be open`() {
        val result = testSubject.restPositionAfterRelease(
            firstItemOffset = -900,
            reachHeight = 900,
            brokeThroughDetent = true,
            threshold = 160f,
        )

        assertThat(result).isEqualTo(RestPosition.OPEN)
    }

    @Test
    fun `restPositionAfterRelease() with docked list that wasn't pulled through the detent should stay`() {
        val result = testSubject.restPositionAfterRelease(
            firstItemOffset = -900,
            reachHeight = 900,
            brokeThroughDetent = false,
            threshold = 160f,
        )

        assertThat(result).isNull()
    }

    @Test
    fun `restPositionAfterRelease() with list scrolled down or without reach area should be null`() {
        assertThat(
            testSubject.restPositionAfterRelease(
                firstItemOffset = null,
                reachHeight = 900,
                brokeThroughDetent = false,
                threshold = 160f,
            ),
        ).isNull()
        assertThat(
            testSubject.restPositionAfterRelease(
                firstItemOffset = 0,
                reachHeight = 0,
                brokeThroughDetent = false,
                threshold = 160f,
            ),
        ).isNull()
    }

    @Test
    fun `scrollToRest() to docked should move the first item to its normal place`() {
        assertThat(testSubject.scrollToRest(RestPosition.DOCKED, firstItemOffset = -100, reachHeight = 900))
            .isEqualTo(800)
    }

    @Test
    fun `scrollToRest() to open should move the first item to the end of the reach area`() {
        assertThat(testSubject.scrollToRest(RestPosition.OPEN, firstItemOffset = -900, reachHeight = 900))
            .isEqualTo(-900)
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

    @Test
    fun `shouldAnchorBelowReachArea() with first item pushed above its normal place by the layout should be true`() {
        // The search status row above the first message grew, which moved the list up.
        val result = testSubject.shouldAnchorBelowReachArea(
            isRevealedByUser = false,
            isScrolledAwayByUser = false,
            firstItemOffset = -1172,
            reachHeight = 1046,
        )

        assertThat(result).isEqualTo(true)
    }

    @Test
    fun `shouldAnchorBelowReachArea() with first item that left the list should be true`() {
        val result = testSubject.shouldAnchorBelowReachArea(
            isRevealedByUser = false,
            isScrolledAwayByUser = false,
            firstItemOffset = null,
            reachHeight = 1046,
        )

        assertThat(result).isEqualTo(true)
    }

    @Test
    fun `isScrolledAway() with first item above its normal place should be true`() {
        assertThat(testSubject.isScrolledAway(firstItemOffset = -1100, reachHeight = 1046)).isEqualTo(true)
        assertThat(testSubject.isScrolledAway(firstItemOffset = null, reachHeight = 1046)).isEqualTo(true)
    }

    @Test
    fun `isScrolledAway() with first item at its normal place or lower should be false`() {
        assertThat(testSubject.isScrolledAway(firstItemOffset = -1046, reachHeight = 1046)).isEqualTo(false)
        assertThat(testSubject.isScrolledAway(firstItemOffset = -500, reachHeight = 1046)).isEqualTo(false)
        assertThat(testSubject.isScrolledAway(firstItemOffset = null, reachHeight = 0)).isEqualTo(false)
    }

    @Test
    fun `detentThreshold() should be 64 dp`() {
        val result = testSubject.detentThreshold(reachHeight = 1000, density = 2.5f)

        assertThat(result).isEqualTo(160f)
    }

    @Test
    fun `detentThreshold() should be at most half the reach area`() {
        val result = testSubject.detentThreshold(reachHeight = 200, density = 2.5f)

        assertThat(result).isEqualTo(100f)
    }

    @Test
    fun `pullThroughDetent() before the threshold should not move the list`() {
        val result = testSubject.pullThroughDetent(pull = 0f, fingerDelta = 100f, threshold = 160f)

        assertThat(result).isEqualTo(DetentStep(pull = 100f, contentDelta = 0f, brokeThrough = false))
    }

    @Test
    fun `pullThroughDetent() across the threshold should break through`() {
        val result = testSubject.pullThroughDetent(pull = 150f, fingerDelta = 30f, threshold = 160f)

        assertThat(result).isEqualTo(DetentStep(pull = 180f, contentDelta = 0f, brokeThrough = true))
    }

    @Test
    fun `pullThroughDetent() exactly at the threshold should not break through yet`() {
        val result = testSubject.pullThroughDetent(pull = 100f, fingerDelta = 60f, threshold = 160f)

        assertThat(result).isEqualTo(DetentStep(pull = 160f, contentDelta = 0f, brokeThrough = false))
    }

    @Test
    fun `pullThroughDetent() after the threshold should not break through again`() {
        val result = testSubject.pullThroughDetent(pull = 180f, fingerDelta = 30f, threshold = 160f)

        assertThat(result.brokeThrough).isEqualTo(false)
    }

    @Test
    fun `pullThroughDetent() pushing back up should not move the list`() {
        val result = testSubject.pullThroughDetent(pull = 100f, fingerDelta = -40f, threshold = 160f)

        assertThat(result).isEqualTo(DetentStep(pull = 60f, contentDelta = 0f, brokeThrough = false))
    }

    @Test
    fun `pullThroughDetent() pushing up beyond the start should pass the rest on in full`() {
        val result = testSubject.pullThroughDetent(pull = 20f, fingerDelta = -50f, threshold = 160f)

        assertThat(result).isEqualTo(DetentStep(pull = 0f, contentDelta = -30f, brokeThrough = false))
    }

    @Test
    fun `dyToMakeVisible() with item at the top of the viewport should not scroll into the reach area`() {
        // The list has 1000 px of reach area as top padding; the first message sits at its normal place at the top.
        val result = dyToMakeVisible(itemTop = 0, itemBottom = 200, viewportTop = 0, viewportBottom = 2000)

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `dyToMakeVisible() with item above the viewport should scroll it down to the top`() {
        val result = dyToMakeVisible(
            itemTop = -150,
            itemBottom = 50,
            viewportTop = 0,
            viewportBottom = 2000,
        )

        assertThat(result).isEqualTo(150)
    }

    @Test
    fun `dyToMakeVisible() with item below the viewport should scroll it up to the bottom`() {
        val result = dyToMakeVisible(
            itemTop = 1900,
            itemBottom = 2100,
            viewportTop = 0,
            viewportBottom = 2000,
        )

        assertThat(result).isEqualTo(-100)
    }
}
