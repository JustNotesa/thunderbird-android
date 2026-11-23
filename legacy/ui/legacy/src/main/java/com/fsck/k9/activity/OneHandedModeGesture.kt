package com.fsck.k9.activity

import kotlin.math.abs

/**
 * What a swipe on the title of the toolbar does to the one-handed mode.
 */
internal enum class OneHandedModeToggle {
    TURN_ON,
    TURN_OFF,
    NONE,
}

internal object OneHandedModeGesture {
    /** How far, in dp, a finger has to travel vertically for the move to count as a swipe. */
    const val VERTICAL_THRESHOLD_DP = 48

    /** How far, in dp, the toolbar follows the finger while it is being swiped. */
    const val MAX_DRAG_DP = 24

    /** How much of the finger's travel the toolbar follows. */
    const val DRAG_FACTOR = 0.3f

    /** How many times further the finger has to travel vertically than horizontally. */
    const val VERTICAL_DOMINANCE = 2

    /**
     * Swiping down on a toolbar at the top moves it down to the bottom of the screen, and swiping up on a toolbar at
     * the bottom moves it back up. Every other move, including horizontal and short ones, does nothing.
     *
     * @param dx The horizontal distance the finger moved in pixels.
     * @param dy The vertical distance the finger moved in pixels, positive when moving down.
     * @param thresholdPx The vertical distance in pixels that has to be reached.
     * @param isAtBottom Whether the toolbar is currently at the bottom of the screen.
     */
    fun decide(dx: Float, dy: Float, thresholdPx: Float, isAtBottom: Boolean): OneHandedModeToggle {
        if (abs(dy) < thresholdPx || abs(dy) < abs(dx) * VERTICAL_DOMINANCE) return OneHandedModeToggle.NONE

        return when {
            dy > 0 && !isAtBottom -> OneHandedModeToggle.TURN_ON
            dy < 0 && isAtBottom -> OneHandedModeToggle.TURN_OFF
            else -> OneHandedModeToggle.NONE
        }
    }

    /**
     * How far the toolbar follows a finger that moved [dy] pixels vertically. It only gives way in the direction that
     * would toggle the mode, and never further than [maxDragPx].
     */
    fun dragOffset(dy: Float, isAtBottom: Boolean, maxDragPx: Float): Float {
        val towardsToggle = if (isAtBottom) dy < 0 else dy > 0
        if (!towardsToggle) return 0f

        return (dy * DRAG_FACTOR).coerceIn(-maxDragPx, maxDragPx)
    }
}
