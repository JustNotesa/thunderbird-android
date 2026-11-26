package com.fsck.k9.ui.base.onehanded

import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ListView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * What the thumb zone reads from a list and what it changes.
 *
 * @property firstItemTop The top of the first item, or `null` when the first item is not visible.
 * @property contentHeight The height of all items, or `null` when not all of them are laid out.
 */
internal data class SurfaceState(val firstItemTop: Int?, val contentHeight: Int?)

/**
 * A scrolling list the thumb zone header is placed above.
 */
internal interface ScrollSurface {
    val view: ViewGroup

    fun bind(onScrolled: () -> Unit, onLaidOut: () -> Unit)

    fun unbind()

    fun read(): SurfaceState

    /** Scrolls so that the first item sits right below the top padding. */
    fun alignToStart()

    companion object {
        /** Finds the first list in [root], not looking into the lists themselves. */
        fun find(root: View): ScrollSurface? {
            return when (root) {
                is RecyclerView -> RecyclerSurface(root)
                is ListView -> ListSurface(root)
                is ViewGroup -> (0 until root.childCount).firstNotNullOfOrNull { find(root.getChildAt(it)) }
                else -> null
            }
        }
    }
}

private class RecyclerSurface(private val recyclerView: RecyclerView) : ScrollSurface {
    private var scrollListener: RecyclerView.OnScrollListener? = null
    private var layoutListener: View.OnLayoutChangeListener? = null

    override val view: ViewGroup get() = recyclerView

    override fun bind(onScrolled: () -> Unit, onLaidOut: () -> Unit) {
        scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = onScrolled()
        }.also(recyclerView::addOnScrollListener)
        layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> onLaidOut() }
            .also(recyclerView::addOnLayoutChangeListener)
    }

    override fun unbind() {
        scrollListener?.let(recyclerView::removeOnScrollListener)
        layoutListener?.let(recyclerView::removeOnLayoutChangeListener)
        scrollListener = null
        layoutListener = null
    }

    override fun read(): SurfaceState {
        val count = recyclerView.adapter?.itemCount ?: 0
        if (count == 0) return SurfaceState(firstItemTop = recyclerView.paddingTop, contentHeight = 0)

        val first = recyclerView.findViewHolderForAdapterPosition(0)?.itemView
        val last = recyclerView.findViewHolderForAdapterPosition(count - 1)?.itemView
        val contentHeight = if (first != null && last != null) last.bottom - first.top else null
        return SurfaceState(firstItemTop = first?.top, contentHeight = contentHeight)
    }

    override fun alignToStart() {
        (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
    }
}

private class ListSurface(private val listView: ListView) : ScrollSurface {
    private var layoutListener: View.OnLayoutChangeListener? = null

    override val view: ViewGroup get() = listView

    override fun bind(onScrolled: () -> Unit, onLaidOut: () -> Unit) {
        listView.setOnScrollListener(
            object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: AbsListView, scrollState: Int) = Unit

                override fun onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, total: Int) {
                    onScrolled()
                }
            },
        )
        layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> onLaidOut() }
            .also(listView::addOnLayoutChangeListener)
    }

    override fun unbind() {
        listView.setOnScrollListener(null)
        layoutListener?.let(listView::removeOnLayoutChangeListener)
        layoutListener = null
    }

    override fun read(): SurfaceState {
        val count = listView.count
        if (count == 0) return SurfaceState(firstItemTop = listView.paddingTop, contentHeight = 0)

        val first = listView.getChildAt(0)?.takeIf { listView.firstVisiblePosition == 0 }
        val last = listView.getChildAt(listView.childCount - 1)?.takeIf { listView.lastVisiblePosition == count - 1 }
        val contentHeight = if (first != null && last != null) last.bottom - first.top else null
        return SurfaceState(firstItemTop = first?.top, contentHeight = contentHeight)
    }

    override fun alignToStart() {
        listView.setSelectionFromTop(0, listView.paddingTop)
    }
}
