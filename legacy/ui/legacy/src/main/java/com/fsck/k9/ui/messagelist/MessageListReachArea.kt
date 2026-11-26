package com.fsck.k9.ui.messagelist

/**
 * The empty area above the first message that lets the user pull the list down within reach of the thumb.
 *
 * It is only used while the toolbar is at the bottom of the screen, which is the case for the single-pane layout in
 * one-handed mode.
 */
internal object MessageListReachArea {
    const val LIST_HEIGHT_FRACTION = 0.45f

    /**
     * How far, in dp, the finger has to move to switch between the two rest positions: pulling down from the docked
     * list to open the reach area, or pushing up from the open list to dock it.
     */
    const val DETENT_PULL_DP = 64

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
     * Where the list comes to rest after the user let go of it. There are only two rest positions: [DOCKED], with the
     * first message at its normal place, and [OPEN], with the reach area fully shown.
     *
     * @param firstItemOffset The top of the first item relative to the top padding edge of the list, or null when the
     *   first item isn't displayed.
     * @param brokeThroughDetent Whether the user pulled the docked list down through the detent in this gesture.
     * @param threshold How far the list has to be pushed up from open to dock, see [detentThreshold].
     * @return The rest position the list has to move to, or null when it is scrolled away from its start (or has no
     *   reach area), where it isn't held by either rest position.
     */
    fun restPositionAfterRelease(
        firstItemOffset: Int?,
        reachHeight: Int,
        brokeThroughDetent: Boolean,
        threshold: Float,
    ): RestPosition? {
        return when {
            reachHeight <= 0 || firstItemOffset == null -> null
            brokeThroughDetent -> RestPosition.OPEN
            !isRevealed(firstItemOffset, reachHeight) -> null
            -firstItemOffset > threshold -> RestPosition.DOCKED
            else -> RestPosition.OPEN
        }
    }

    /**
     * How far the list has to scroll to rest at [position], positive when the messages move up.
     */
    fun scrollToRest(position: RestPosition, firstItemOffset: Int, reachHeight: Int): Int {
        return when (position) {
            RestPosition.DOCKED -> firstItemOffset + reachHeight
            RestPosition.OPEN -> firstItemOffset
        }
    }

    /**
     * How far the finger has to pull before the list gives way: [DETENT_PULL_DP], but no more than half the reach
     * area.
     */
    fun detentThreshold(reachHeight: Int, density: Float): Float {
        return minOf(DETENT_PULL_DP * density, reachHeight * DETENT_FRACTION_LIMIT)
    }

    /**
     * Applies a movement of the finger to the list while it is held at its docked rest position. The list doesn't
     * move at all until the pull passes the threshold.
     *
     * @param pull How far the finger has pulled down so far, never negative.
     * @param fingerDelta How far the finger moved now, positive when it moves down.
     * @param threshold The pull at which the list opens, see [detentThreshold].
     */
    fun pullThroughDetent(pull: Float, fingerDelta: Float, threshold: Float): DetentStep {
        val newPull = pull + fingerDelta
        // Pushing up beyond the start scrolls the list as usual, so the part below zero is passed on in full.
        val beyondStart = newPull.coerceAtMost(0f)
        val brokeThrough = pull <= threshold && newPull > threshold
        return DetentStep(
            pull = newPull.coerceAtLeast(0f),
            contentDelta = beyondStart,
            brokeThrough = brokeThrough,
        )
    }

    private const val DETENT_FRACTION_LIMIT = 0.5f
}

/**
 * The result of a movement of the finger against the detent.
 *
 * @property pull How far the finger has pulled down from the start.
 * @property contentDelta How far the list moves, positive when the messages move down.
 * @property brokeThrough Whether this movement took the pull past the threshold.
 */
internal data class DetentStep(val pull: Float, val contentDelta: Float, val brokeThrough: Boolean)

/** The two rest positions of the message list in one-handed mode. */
internal enum class RestPosition {
    /** The first message is at its normal place at the top, the reach area isn't visible. */
    DOCKED,

    /** The reach area is fully shown, the list starts below it. */
    OPEN,
}

/** The top edge and the height of an item of the list, in pixels. */
internal data class ItemBounds(val top: Int, val height: Int)

/**
 * How far the list has to scroll so an item is displayed completely in the part of the list that shows messages,
 * which doesn't include the reach area above the first message. Positive when the items have to move down, like
 * the result of `LinearSmoothScroller.calculateDyToMakeVisible()`. 0 when the item is displayed already.
 *
 * Not counting the reach area as part of the list's top padding, as the default does, is what keeps the list from
 * being pulled down into it just because a message that is displayed already is to be brought into view.
 */
internal fun dyToMakeVisible(itemTop: Int, itemBottom: Int, viewportTop: Int, viewportBottom: Int): Int {
    val toStart = viewportTop - itemTop
    if (toStart > 0) return toStart

    val toEnd = viewportBottom - itemBottom
    return if (toEnd < 0) toEnd else 0
}

/**
 * The height of all messages, estimated from the items that are laid out. It is exact when all of them are.
 *
 * The scroll range of the list can't be used for this: it only counts items that are visible within the padding of
 * the list, and the extra padding below the messages can be as high as the list itself. Then no item counts as
 * visible, the range is 0, the list is taken for empty and keeps all of its padding for good.
 *
 * @param itemCount The number of items in the list.
 * @param firstLaidOut The adapter position of the first item that is laid out.
 * @param lastLaidOut The adapter position of the last item that is laid out.
 * @param laidOutHeight The distance from the top of the first to the bottom of the last item that is laid out.
 */
internal fun estimateContentHeight(itemCount: Int, firstLaidOut: Int, lastLaidOut: Int, laidOutHeight: Int): Int {
    val laidOutCount = lastLaidOut - firstLaidOut + 1

    return when {
        itemCount <= 0 || laidOutCount <= 0 -> 0
        laidOutCount >= itemCount -> laidOutHeight
        else -> (laidOutHeight.toFloat() / laidOutCount * itemCount).toInt()
    }
}
