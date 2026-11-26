package com.fsck.k9.ui.base.onehanded

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.fsck.k9.ui.base.R

/**
 * Puts a header (large title and optional explanation) above a list and arranges the list for the thumb zone:
 * long content starts below the header and scrolls up together with it, short content sits at the bottom.
 *
 * The header is an overlay in [host]. It is only shown while a list is attached.
 */
internal class ThumbZoneController(private val host: ViewGroup) {
    private val header: View = LayoutInflater.from(host.context).inflate(R.layout.thumb_zone_header, host, false)
    private val titleView: TextView = header.findViewById(R.id.thumb_zone_title)
    private val descriptionView: TextView = header.findViewById(R.id.thumb_zone_description)

    private var surface: ScrollSurface? = null
    private var originalPaddingTop = 0
    private var originalClipToPadding = true
    private var updatePending = false

    /** Whether the texts are taken from the toolbar, or have been set explicitly. */
    private var followsToolbar = true

    init {
        header.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP,
        )
        host.addView(header)
        header.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> scheduleUpdate() }
    }

    /** Attaches the first list found in [root]. Returns `false` if there is none. */
    fun attach(root: View): Boolean {
        val newSurface = ScrollSurface.find(root) ?: return false
        if (newSurface.view !== surface?.view) use(newSurface)
        return true
    }

    private fun use(newSurface: ScrollSurface) {
        detachCurrent()

        surface = newSurface
        val view = newSurface.view
        originalPaddingTop = view.paddingTop
        originalClipToPadding = view.clipToPadding
        view.clipToPadding = false
        newSurface.bind(onScrolled = ::updateShift, onLaidOut = ::scheduleUpdate)
        header.visibility = View.VISIBLE
        scheduleUpdate()
    }

    /** Detaches the list if it is part of [root], e.g. because the fragment showing it goes away. */
    fun detach(root: View?) {
        val view = surface?.view ?: return
        if (root == null || root === view || view.isDescendantOf(root)) detachCurrent()
    }

    fun setTexts(title: CharSequence?, description: CharSequence?) {
        followsToolbar = false
        showTexts(title, description)
    }

    /** Takes over title and subtitle of the toolbar, unless texts have been set explicitly. */
    fun syncFromToolbar(title: CharSequence?, subtitle: CharSequence?) {
        if (followsToolbar) showTexts(title, subtitle)
    }

    private fun showTexts(title: CharSequence?, description: CharSequence?) {
        titleView.text = title
        descriptionView.text = description
        descriptionView.visibility = if (description.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun detachCurrent() {
        val current = surface ?: return
        current.unbind()
        val view = current.view
        view.setPadding(view.paddingLeft, originalPaddingTop, view.paddingRight, view.paddingBottom)
        view.clipToPadding = originalClipToPadding
        surface = null
        header.visibility = View.GONE
    }

    private fun scheduleUpdate() {
        if (updatePending) return
        updatePending = true
        header.post {
            updatePending = false
            update()
        }
    }

    private fun update() {
        val current = surface ?: return
        val view = current.view
        val viewport = view.height - view.paddingBottom
        if (viewport <= 0) return

        val state = current.read()
        val inset = ThumbZone.topInset(viewport, state.contentHeight, header.height)
        if (view.paddingTop != inset) {
            view.setPadding(view.paddingLeft, inset, view.paddingRight, view.paddingBottom)
            if (state.firstItemTop != null) view.post { current.alignToStart() }
        }
        updateShift()
    }

    private fun updateShift() {
        val current = surface ?: return
        val view = current.view
        val state = current.read()
        val shift = ThumbZone.headerShift(state.firstItemTop, view.paddingTop, header.height)
        header.translationY = (view.topIn(host) - header.top + shift).toFloat()
    }
}

private fun View.topIn(ancestor: ViewGroup): Int {
    var top = 0
    var current: View = this
    while (current !== ancestor) {
        top += current.top
        current = current.parent as? View ?: return 0
    }
    return top
}

private fun View.isDescendantOf(ancestor: View): Boolean {
    var current: Any? = parent
    while (current != null) {
        if (current === ancestor) return true
        current = (current as? View)?.parent
    }
    return false
}
