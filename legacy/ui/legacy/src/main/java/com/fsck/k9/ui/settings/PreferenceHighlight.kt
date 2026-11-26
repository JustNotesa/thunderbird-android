package com.fsck.k9.ui.settings

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fsck.k9.ui.R
import com.google.android.material.color.MaterialColors

private const val MAX_ATTEMPTS = 20
private const val RETRY_DELAY_MS = 50L
private const val HIGHLIGHT_DURATION_MS = 1600L
private const val HIGHLIGHT_ALPHA = 90

/**
 * Scrolls to the preference with the given [key] and lets it light up for a moment.
 *
 * The preference list is filled a moment after the fragment's view was created, so this waits for it. If the
 * preference does not exist on the screen, the user is told.
 */
internal fun PreferenceFragmentCompat.scrollToAndHighlightPreference(key: String) {
    val list = listView ?: return
    tryHighlight(list, key, attempt = 1)
}

private fun PreferenceFragmentCompat.tryHighlight(list: RecyclerView, key: String, attempt: Int) {
    val callback = list.adapter as? PreferenceGroup.PreferencePositionCallback
    val position = callback?.getPreferenceAdapterPosition(key) ?: RecyclerView.NO_POSITION

    if (position == RecyclerView.NO_POSITION) {
        if (attempt < MAX_ATTEMPTS) {
            list.postDelayed({ if (isAdded) tryHighlight(list, key, attempt + 1) }, RETRY_DELAY_MS)
        } else if (isAdded) {
            Toast.makeText(requireContext(), R.string.settings_search_highlight_failed, Toast.LENGTH_LONG).show()
        }
        return
    }

    (list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
        ?: list.scrollToPosition(position)
    list.post { list.findViewHolderForAdapterPosition(position)?.itemView?.flash() }
}

private fun View.flash() {
    val originalBackground = background
    val color = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
    val drawable = ColorDrawable(ColorUtils.setAlphaComponent(color, HIGHLIGHT_ALPHA))
    background = drawable

    ValueAnimator.ofInt(HIGHLIGHT_ALPHA, 0).apply {
        duration = HIGHLIGHT_DURATION_MS
        startDelay = RETRY_DELAY_MS
        addUpdateListener { drawable.color = ColorUtils.setAlphaComponent(color, it.animatedValue as Int) }
        addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    background = originalBackground
                }
            },
        )
        start()
    }
}
