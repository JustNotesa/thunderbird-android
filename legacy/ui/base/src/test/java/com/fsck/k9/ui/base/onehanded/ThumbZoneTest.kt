package com.fsck.k9.ui.base.onehanded

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class ThumbZoneTest {
    @Test
    fun `long content should start below the header fraction of the screen`() {
        val result = ThumbZone.topInset(viewportHeight = 1000, contentHeight = null, headerHeight = 200)

        assertThat(result).isEqualTo(420)
    }

    @Test
    fun `short content should sit at the bottom of the viewport`() {
        val result = ThumbZone.topInset(viewportHeight = 1000, contentHeight = 300, headerHeight = 200)

        assertThat(result).isEqualTo(700)
    }

    @Test
    fun `medium content should start below the header fraction`() {
        val result = ThumbZone.topInset(viewportHeight = 1000, contentHeight = 800, headerHeight = 200)

        assertThat(result).isEqualTo(420)
    }

    @Test
    fun `a tall header should push the content down`() {
        val result = ThumbZone.topInset(viewportHeight = 1000, contentHeight = null, headerHeight = 600)

        assertThat(result).isEqualTo(600)
    }

    @Test
    fun `empty list should leave the whole viewport to the header`() {
        val result = ThumbZone.topInset(viewportHeight = 1000, contentHeight = 0, headerHeight = 200)

        assertThat(result).isEqualTo(1000)
    }

    @Test
    fun `no viewport should not reserve any space`() {
        val result = ThumbZone.topInset(viewportHeight = 0, contentHeight = 100, headerHeight = 200)

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `header should stay in place while the first item is at the top inset`() {
        val result = ThumbZone.headerShift(firstItemTop = 420, topInset = 420, headerHeight = 200)

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `header should follow the content when it scrolls up`() {
        val result = ThumbZone.headerShift(firstItemTop = 320, topInset = 420, headerHeight = 200)

        assertThat(result).isEqualTo(-100)
    }

    @Test
    fun `header should not move down when the list is pulled beyond its start`() {
        val result = ThumbZone.headerShift(firstItemTop = 500, topInset = 420, headerHeight = 200)

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `header should be gone once the first item scrolled out of view`() {
        val result = ThumbZone.headerShift(firstItemTop = null, topInset = 420, headerHeight = 200)

        assertThat(result).isEqualTo(-200)
    }
}
