package com.fsck.k9.ui.messagelist

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test

class SwipeThresholdTrackerTest {
    private val testSubject = SwipeThresholdTracker()

    @Test
    fun `update() with threshold reached should report that the threshold was reached`() {
        // Act
        val result = testSubject.update(thresholdReached = true)

        // Assert
        assertThat(result).isEqualTo(SwipeThresholdChange.Reached)
    }

    @Test
    fun `update() with threshold reached twice should not report a change`() {
        // Arrange
        testSubject.update(thresholdReached = true)

        // Act
        val result = testSubject.update(thresholdReached = true)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `update() with threshold not reached anymore should report that the threshold was left`() {
        // Arrange
        testSubject.update(thresholdReached = true)

        // Act
        val result = testSubject.update(thresholdReached = false)

        // Assert
        assertThat(result).isEqualTo(SwipeThresholdChange.Left)
    }

    @Test
    fun `update() with threshold not reached should not report a change`() {
        // Act
        val result = testSubject.update(thresholdReached = false)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `update() after reset() should report that the threshold was reached again`() {
        // Arrange
        testSubject.update(thresholdReached = true)
        testSubject.reset()

        // Act
        val result = testSubject.update(thresholdReached = true)

        // Assert
        assertThat(result).isEqualTo(SwipeThresholdChange.Reached)
    }
}
