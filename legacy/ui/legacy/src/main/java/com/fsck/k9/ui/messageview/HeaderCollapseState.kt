package com.fsck.k9.ui.messageview

/**
 * Whether the message header pinned above the toolbar is folded into one line, plus the scroll position from which it
 * may fold again after the user unfolded it by hand.
 */
internal data class HeaderCollapseState(
    val isCollapsed: Boolean = false,
    val collapseFromY: Int,
)

/** The state of a header that has not been scrolled yet: expanded, and folding after scrolling [threshold] pixels. */
internal fun initialHeaderCollapseState(threshold: Int) = HeaderCollapseState(collapseFromY = threshold)

/**
 * Decides after a scroll of the message body whether the header folds or unfolds.
 *
 * It folds once the body is scrolled down [HeaderCollapseState.collapseFromY] pixels, but only when the body is still
 * scrollable after the header got smaller by [collapseDelta]. Otherwise the shorter header would pull the body back to
 * the top, which unfolds the header again, and so on. It unfolds when the body is back at the top.
 */
internal fun nextHeaderCollapseState(
    state: HeaderCollapseState,
    scrollY: Int,
    scrollRange: Int,
    collapseDelta: Int,
    threshold: Int,
): HeaderCollapseState {
    val canStillScrollWhenCollapsed = scrollRange - collapseDelta > 0
    val shouldCollapse = scrollY >= state.collapseFromY && canStillScrollWhenCollapsed

    return when {
        scrollY <= 0 -> initialHeaderCollapseState(threshold)
        !state.isCollapsed && shouldCollapse -> state.copy(isCollapsed = true)
        else -> state
    }
}

/**
 * The state after the user unfolded the header by tapping it. It stays unfolded until the body is scrolled
 * [threshold] pixels further down, or back to the top and down again.
 */
internal fun headerUnfoldedByUser(scrollY: Int, threshold: Int) =
    HeaderCollapseState(isCollapsed = false, collapseFromY = scrollY + threshold)

/** The part of the screen's height the header pinned above the toolbar may take up when it is unfolded. */
internal const val PINNED_HEADER_MAX_HEIGHT_FRACTION = 0.4f

/** The height the unfolded header pinned above the toolbar is capped at. A taller header scrolls within the cap. */
internal fun pinnedHeaderMaxHeight(screenHeight: Int): Int = (screenHeight * PINNED_HEADER_MAX_HEIGHT_FRACTION).toInt()

/** Which way a vertical swipe on the pinned header moved the finger. */
internal enum class HeaderSwipe { FOLD, UNFOLD, NONE }

/**
 * What a vertical swipe on the pinned header does: swiping up folds it, like scrolling the message down, and swiping
 * down unfolds it again. A header that scrolls within its cap keeps the swipe as long as it can scroll that way.
 *
 * @param deltaY How far the finger moved, negative for up.
 * @param canScrollHeaderUp Whether the header's own content has more to show when the finger moves up.
 */
internal fun headerSwipeAction(
    deltaY: Float,
    isCollapsed: Boolean,
    canScrollHeaderUp: Boolean,
): HeaderSwipe {
    return when {
        deltaY < 0 && !isCollapsed && !canScrollHeaderUp -> HeaderSwipe.FOLD
        deltaY > 0 && isCollapsed -> HeaderSwipe.UNFOLD
        else -> HeaderSwipe.NONE
    }
}
