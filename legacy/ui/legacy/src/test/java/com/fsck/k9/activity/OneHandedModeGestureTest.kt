package com.fsck.k9.activity

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class OneHandedModeGestureTest {
    private val testSubject = OneHandedModeGesture

    @Test
    fun `swiping down with the toolbar at the top turns the mode on`() {
        val result = testSubject.decide(dx = 5f, dy = 120f, thresholdPx = 100f, isAtBottom = false)

        assertThat(result).isEqualTo(OneHandedModeToggle.TURN_ON)
    }

    @Test
    fun `swiping up with the toolbar at the bottom turns the mode off`() {
        val result = testSubject.decide(dx = -5f, dy = -120f, thresholdPx = 100f, isAtBottom = true)

        assertThat(result).isEqualTo(OneHandedModeToggle.TURN_OFF)
    }

    @Test
    fun `swiping up with the toolbar at the top does nothing`() {
        val result = testSubject.decide(dx = 0f, dy = -300f, thresholdPx = 100f, isAtBottom = false)

        assertThat(result).isEqualTo(OneHandedModeToggle.NONE)
    }

    @Test
    fun `swiping down with the toolbar at the bottom does nothing`() {
        val result = testSubject.decide(dx = 0f, dy = 300f, thresholdPx = 100f, isAtBottom = true)

        assertThat(result).isEqualTo(OneHandedModeToggle.NONE)
    }

    @Test
    fun `a move shorter than the threshold does nothing`() {
        val result = testSubject.decide(dx = 0f, dy = 99f, thresholdPx = 100f, isAtBottom = false)

        assertThat(result).isEqualTo(OneHandedModeToggle.NONE)
    }

    @Test
    fun `a move that reaches the threshold counts`() {
        val result = testSubject.decide(dx = 0f, dy = 100f, thresholdPx = 100f, isAtBottom = false)

        assertThat(result).isEqualTo(OneHandedModeToggle.TURN_ON)
    }

    @Test
    fun `a mostly horizontal move does nothing`() {
        val result = testSubject.decide(dx = 200f, dy = 150f, thresholdPx = 100f, isAtBottom = false)

        assertThat(result).isEqualTo(OneHandedModeToggle.NONE)
    }

    @Test
    fun `a tap does nothing`() {
        val result = testSubject.decide(dx = 0f, dy = 0f, thresholdPx = 100f, isAtBottom = true)

        assertThat(result).isEqualTo(OneHandedModeToggle.NONE)
    }
}
