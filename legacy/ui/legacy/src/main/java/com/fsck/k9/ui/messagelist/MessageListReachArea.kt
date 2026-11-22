package com.fsck.k9.ui.messagelist

/**
 * The empty area above the first message that lets the user pull the list down within reach of the thumb.
 *
 * It is only used while the toolbar is at the bottom of the screen, which is the case for the single-pane layout in
 * one-handed mode.
 */
internal object MessageListReachArea {
    const val LIST_HEIGHT_FRACTION = 0.45f

    /** The part of the reach area, counted from the normal top, in which the list snaps to the top. */
    const val SNAP_FRACTION = 0.4f

    /**
     * @param listHeight The height of the message list in pixels.
     * @param bottomBarHeight The height of the toolbar at the bottom, or 0 when the toolbar is at the top.
     * @param hasMessages Whether the list contains messages. A list without messages is left as it is.
     */
    fun height(listHeight: Int, bottomBarHeight: Int, hasMessages: Boolean): Int {
        if (bottomBarHeight <= 0 || listHeight <= 0 || !hasMessages) return 0

        return (listHeight * LIST_HEIGHT_FRACTION).toInt()
    }

    /**
     * The space needed below the messages so that the list can be scrolled far enough to move the first message to
     * the top, i.e. past the reach area. A list that is long enough already can, and gets no extra space.
     *
     * @param listHeight The height of the message list in pixels.
     * @param topPadding The part of the top padding that isn't the reach area (the in-app notification banner).
     * @param contentHeight The height of all messages, without any padding.
     * @param bottomPadding The bottom padding the list has anyway.
     */
    fun extraBottomPadding(listHeight: Int, topPadding: Int, contentHeight: Int, bottomPadding: Int): Int {
        return (listHeight - topPadding - contentHeight - bottomPadding).coerceAtLeast(0)
    }

    /**
     * Whether the list shows the reach area, i.e. the first message sits lower than its normal place at the top.
     *
     * @param firstVisiblePosition The adapter position of the first visible item.
     * @param firstItemOffset The top of the first item relative to the top padding edge of the list, or null when the
     *   first item isn't displayed. Pulled all the way down it is 0, at its normal place it is `-reachHeight`.
     * @param reachHeight The height of the reach area.
     */
    fun isRevealed(firstVisiblePosition: Int, firstItemOffset: Int?, reachHeight: Int): Boolean {
        return reachHeight > 0 && firstVisiblePosition == 0 && firstItemOffset != null && firstItemOffset > -reachHeight
    }

    /**
     * Whether the list has to be moved so the first message sits at its normal place at the top. That is the case when
     * the user didn't reveal the reach area themselves, the list is at its start, and the first message is lower than
     * its normal place.
     */
    fun shouldAnchorBelowReachArea(
        isRevealedByUser: Boolean,
        firstVisiblePosition: Int,
        firstItemOffset: Int?,
        reachHeight: Int,
    ): Boolean {
        return reachHeight > 0 && !isRevealedByUser &&
            isRevealed(firstVisiblePosition, firstItemOffset, reachHeight)
    }

    /**
     * Whether the list should snap to the first message's normal place at the top after the user let go of it. That
     * is the case when the reach area is shown but only its lower part, [SNAP_FRACTION] of it or less, is left above
     * the normal place.
     */
    fun shouldSnapToTop(firstVisiblePosition: Int, firstItemOffset: Int?, reachHeight: Int): Boolean {
        if (!isRevealed(firstVisiblePosition, firstItemOffset, reachHeight) || firstItemOffset == null) return false

        val distanceToTop = firstItemOffset + reachHeight
        return distanceToTop <= reachHeight * SNAP_FRACTION
    }
}
