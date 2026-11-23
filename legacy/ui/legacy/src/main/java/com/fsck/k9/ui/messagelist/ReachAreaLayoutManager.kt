package com.fsck.k9.ui.messagelist

import androidx.recyclerview.widget.RecyclerView
import app.k9mail.ui.utils.linearlayoutmanager.LinearLayoutManager

/**
 * A layout manager that lets the message list hold back a vertical scroll before it moves.
 *
 * It is used for the detent at the first message: while it is at its normal place, the list only follows a part of
 * the finger when pulled down into the reach area.
 */
internal class ReachAreaLayoutManager : LinearLayoutManager() {
    /**
     * Gets the scroll the user asked for and returns how far to scroll instead. Both are positive when the messages
     * move up. Without it, or when it returns the scroll unchanged, the list behaves as usual.
     */
    var scrollAdjuster: ((dy: Int) -> Int)? = null

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        val adjusted = scrollAdjuster?.invoke(dy) ?: dy
        if (adjusted == dy) return super.scrollVerticallyBy(dy, recycler, state)

        super.scrollVerticallyBy(adjusted, recycler, state)
        // The held back part isn't left over for the list to treat as overscroll, which would stretch it.
        return dy
    }
}
