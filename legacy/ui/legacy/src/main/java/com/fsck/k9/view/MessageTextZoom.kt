package com.fsck.k9.view

import kotlin.math.roundToInt

private const val MIN_TEXT_ZOOM_FACTOR = 0.5f
private const val MAX_TEXT_ZOOM_FACTOR = 3f
private const val MIN_TEXT_ZOOM_PERCENT = 50
private const val MAX_TEXT_ZOOM_PERCENT = 300

/**
 * The text size the reader chose by pinching the message view, as a factor of the text size the message is configured
 * with.
 *
 * It isn't persisted, but it applies to every message read while the app runs.
 */
internal object MessageTextZoom {
    var factor = 1f
        private set

    /** Takes [factor] as the text size to use from now on. */
    fun update(factor: Float) {
        this.factor = factor.coerceIn(MIN_TEXT_ZOOM_FACTOR, MAX_TEXT_ZOOM_FACTOR)
    }

    /** The text zoom in percent for a message whose text size is [baseTextZoom] percent of the normal size. */
    fun textZoom(baseTextZoom: Int): Int {
        return (baseTextZoom * factor).roundToInt().coerceIn(MIN_TEXT_ZOOM_PERCENT, MAX_TEXT_ZOOM_PERCENT)
    }
}

/**
 * The text zoom factor of a pinch that started at [startFactor] and was scaled by [scaleFactor] while the fingers
 * moved, limited to the text zoom range.
 */
internal fun calculateZoomFactor(startFactor: Float, scaleFactor: Float): Float {
    return (startFactor * scaleFactor).coerceIn(MIN_TEXT_ZOOM_FACTOR, MAX_TEXT_ZOOM_FACTOR)
}
