package com.fsck.k9.ui.messageview

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class PinnedHeaderTest {
    @Test
    fun `unfolded header should be capped at 40 percent of the screen height`() {
        val result = pinnedHeaderMaxHeight(screenHeight = 1080)

        assertThat(result).isEqualTo(432)
    }

    @Test
    fun `swipe up should fold an unfolded header`() {
        val result = headerSwipeAction(
            deltaY = -80f,
            isCollapsed = false,
            canScrollHeaderUp = false,
        )

        assertThat(result).isEqualTo(HeaderSwipe.FOLD)
    }

    @Test
    fun `swipe up should scroll a header that has more to show instead of folding it`() {
        val result = headerSwipeAction(
            deltaY = -80f,
            isCollapsed = false,
            canScrollHeaderUp = true,
        )

        assertThat(result).isEqualTo(HeaderSwipe.NONE)
    }

    @Test
    fun `swipe down should unfold a folded header`() {
        val result = headerSwipeAction(
            deltaY = 80f,
            isCollapsed = true,
            canScrollHeaderUp = false,
        )

        assertThat(result).isEqualTo(HeaderSwipe.UNFOLD)
    }
}
