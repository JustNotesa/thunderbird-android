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

    /** How far, in pixels, the first message may be from its normal place and still count as being there. */
    private const val AT_TOP_TOLERANCE = 2

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
     * The top of the first item that takes up space, relative to the top padding of the list. Items without height
     * are skipped: the list starts with the (usually empty) list of in-app notification banners, which would
     * otherwise be taken for the first item, although the first message is the item after it.
     *
     * @param itemCount The number of items in the list.
     * @param paddingTop The top padding of the list.
     * @param boundsOf The position and height of the item at the given adapter position, or null when the item isn't
     *   displayed. That is the case when the list was scrolled so far that the item has left it.
     * @return The offset, or null when the start of the list isn't displayed.
     */
    fun firstItemOffset(itemCount: Int, paddingTop: Int, boundsOf: (position: Int) -> ItemBounds?): Int? {
        for (position in 0 until itemCount) {
            val bounds = boundsOf(position)
            if (bounds == null || bounds.height > 0) return bounds?.let { it.top - paddingTop }
        }
        return null
    }

    /**
     * Whether the list shows the reach area, i.e. the first message sits lower than its normal place at the top.
     *
     * @param firstItemOffset The top of the first item relative to the top padding edge of the list, or null when the
     *   first item isn't displayed. Pulled all the way down it is 0, at its normal place it is `-reachHeight`.
     * @param reachHeight The height of the reach area.
     */
    fun isRevealed(firstItemOffset: Int?, reachHeight: Int): Boolean {
        return reachHeight > 0 && firstItemOffset != null && firstItemOffset > -reachHeight
    }

    /**
     * Whether the user scrolled the list away from its start, so the first item isn't at its normal place or lower.
     * Something that changes the list under the user, e.g. the status row of a search growing, can push the first
     * item out of place as well, but only a scroll of the user is meant to keep it there.
     */
    fun isScrolledAway(firstItemOffset: Int?, reachHeight: Int): Boolean {
        return reachHeight > 0 && (firstItemOffset == null || firstItemOffset < -reachHeight - AT_TOP_TOLERANCE)
    }

    /**
     * Whether the list has to be moved so the first message sits at its normal place at the top. That is the case when
     * the user neither revealed the reach area nor scrolled away from the start, and the first message isn't at its
     * normal place: because it is lower (reach area shown by the layout), or because the layout moved it up, e.g.
     * when items above the first message grew. Then it might not even be displayed any more ([firstItemOffset] null).
     */
    fun shouldAnchorBelowReachArea(
        isRevealedByUser: Boolean,
        isScrolledAwayByUser: Boolean,
        firstItemOffset: Int?,
        reachHeight: Int,
    ): Boolean {
        return reachHeight > 0 && !isRevealedByUser && !isScrolledAwayByUser &&
            (firstItemOffset == null || kotlin.math.abs(firstItemOffset + reachHeight) > AT_TOP_TOLERANCE)
    }

    /**
     * Whether the list has to stay at the first message when items are inserted in front of it. RecyclerView keeps
     * the item that was displayed at the top where it is, so new messages would appear above the top edge of the
     * list, out of sight. That is only wanted when the first message sits at its normal place, i.e. the user neither
     * revealed the reach area nor scrolled down.
     */
    fun shouldKeepFirstItemOnTop(isRevealedByUser: Boolean, firstItemOffset: Int?, reachHeight: Int): Boolean {
        return reachHeight > 0 && !isRevealedByUser && firstItemOffset != null &&
            kotlin.math.abs(firstItemOffset + reachHeight) <= AT_TOP_TOLERANCE
    }

    /**
     * Whether the list should snap to the first message's normal place at the top after the user let go of it. That
     * is the case when the reach area is shown but only its lower part, [SNAP_FRACTION] of it or less, is left above
     * the normal place.
     */
    fun shouldSnapToTop(firstItemOffset: Int?, reachHeight: Int): Boolean {
        if (!isRevealed(firstItemOffset, reachHeight) || firstItemOffset == null) return false

        val distanceToTop = firstItemOffset + reachHeight
        return distanceToTop <= reachHeight * SNAP_FRACTION
    }
}

/** The top edge and the height of an item of the list, in pixels. */
internal data class ItemBounds(val top: Int, val height: Int)
