package com.fsck.k9.ui.base.onehanded

import kotlin.math.roundToInt

/**
 * The layout rules of the thumb zone: the top part of the screen only shows information (a large title and an
 * optional explanation), everything that can be operated sits in the lower part, right above the toolbar.
 */
object ThumbZone {
    /** Share of the screen height that stays reserved for the header while content is long. */
    const val HEADER_FRACTION = 0.42f

    /**
     * The space a list leaves empty above its first item.
     *
     * Long content starts below the header area. Short content is pushed down so it ends directly above the toolbar.
     * The space is never smaller than the header itself.
     *
     * @param viewportHeight The height available to the list.
     * @param contentHeight The height of all items, or `null` when not all of them are laid out (long content).
     * @param headerHeight The height of the header.
     */
    fun topInset(viewportHeight: Int, contentHeight: Int?, headerHeight: Int): Int {
        if (viewportHeight <= 0) return 0

        val minimum = maxOf(headerHeight, (viewportHeight * HEADER_FRACTION).roundToInt())
        return maxOf(minimum, contentHeight?.let { viewportHeight - it } ?: 0)
    }

    /**
     * How far the header moves up so it scrolls away together with the content.
     *
     * @param firstItemTop The top of the first item, or `null` when the first item has scrolled out of view.
     * @return Zero or a negative number.
     */
    fun headerShift(firstItemTop: Int?, topInset: Int, headerHeight: Int): Int {
        if (firstItemTop == null) return -headerHeight

        return minOf(0, firstItemTop - topInset)
    }
}
