package com.fsck.k9.ui.messagelist

/**
 * Tracks whether a swipe gesture is currently past the threshold at which the swipe action is performed.
 */
internal class SwipeThresholdTracker {
    private var thresholdReached = false

    /**
     * Update the threshold state and return the change that happened, if there was one.
     *
     * @param thresholdReached Whether the swipe currently is past the threshold.
     * @return [SwipeThresholdChange.Reached] when the threshold was just crossed, [SwipeThresholdChange.Left] when the
     * swipe went back below the threshold, or `null` when the state didn't change.
     */
    fun update(thresholdReached: Boolean): SwipeThresholdChange? {
        if (thresholdReached == this.thresholdReached) return null

        this.thresholdReached = thresholdReached

        return if (thresholdReached) SwipeThresholdChange.Reached else SwipeThresholdChange.Left
    }

    /**
     * Reset the tracker to the state where the threshold hasn't been reached.
     */
    fun reset() {
        thresholdReached = false
    }
}

/**
 * The change of the swipe threshold state.
 */
internal enum class SwipeThresholdChange {
    /** The swipe crossed the threshold. */
    Reached,

    /** The swipe went back below the threshold. */
    Left,
}
