package com.fsck.k9.view

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.After
import org.junit.Before
import org.junit.Test

class MessageTextZoomTest {
    @Before
    fun setUp() {
        MessageTextZoom.update(DEFAULT_FACTOR)
    }

    @After
    fun tearDown() {
        MessageTextZoom.update(DEFAULT_FACTOR)
    }

    @Test
    fun theTextSizeStartsAtTheConfiguredSize() {
        assertThat(MessageTextZoom.factor).isEqualTo(1f)
    }

    @Test
    fun aPinchScalesTheTextSize() {
        val result = calculateZoomFactor(startFactor = 1f, scaleFactor = 1.5f)

        assertThat(result).isEqualTo(1.5f)
    }

    @Test
    fun aPinchScalesTheTextSizeThePinchStartedAt() {
        val result = calculateZoomFactor(startFactor = 2f, scaleFactor = 0.5f)

        assertThat(result).isEqualTo(1f)
    }

    @Test
    fun textIsNotEnlargedMoreThanThreeTimes() {
        val result = calculateZoomFactor(startFactor = 2f, scaleFactor = 4f)

        assertThat(result).isEqualTo(3f)
    }

    @Test
    fun textIsNotShrunkBelowHalfItsSize() {
        val result = calculateZoomFactor(startFactor = 1f, scaleFactor = 0.1f)

        assertThat(result).isEqualTo(0.5f)
    }

    @Test
    fun theConfiguredTextSizeIsUsedWithoutZooming() {
        assertThat(MessageTextZoom.textZoom(baseTextZoom = 120)).isEqualTo(120)
    }

    @Test
    fun theChosenTextSizeAppliesToLaterMessages() {
        MessageTextZoom.update(factor = 2f)

        assertThat(MessageTextZoom.textZoom(baseTextZoom = 110)).isEqualTo(220)
    }

    @Test
    fun theChosenTextSizeIsLimited() {
        MessageTextZoom.update(factor = 5f)

        assertThat(MessageTextZoom.textZoom(baseTextZoom = 120)).isEqualTo(300)
    }

    @Test
    fun aChosenTextSizeOutOfRangeIsLimited() {
        MessageTextZoom.update(factor = 0.1f)

        assertThat(MessageTextZoom.factor).isEqualTo(0.5f)
        assertThat(MessageTextZoom.textZoom(baseTextZoom = 60)).isEqualTo(50)
    }

    private companion object {
        const val DEFAULT_FACTOR = 1f
    }
}
