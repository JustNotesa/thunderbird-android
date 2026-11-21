package com.fsck.k9.activity

import android.view.View
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator

/**
 * Hides a bar at the bottom of the screen by sliding it down, and shows it again by sliding it back up.
 */
internal class BottomBarScrollHider(private val bar: View) {
    var isHidden: Boolean = false
        private set

    fun onScrolled(dy: Int) {
        when {
            dy > 0 -> hide()
            dy < 0 -> show()
        }
    }

    fun hide() {
        if (isHidden) return

        isHidden = true
        bar.animate().cancel()
        bar.animate()
            .translationY(bar.height.toFloat())
            .setDuration(HIDE_DURATION_MS)
            .setInterpolator(FastOutLinearInInterpolator())
            .start()
    }

    fun show() {
        if (!isHidden) return

        isHidden = false
        bar.animate().cancel()
        bar.animate()
            .translationY(0f)
            .setDuration(SHOW_DURATION_MS)
            .setInterpolator(LinearOutSlowInInterpolator())
            .start()
    }

    fun reset() {
        bar.animate().cancel()
        bar.translationY = 0f
        isHidden = false
    }

    private companion object {
        const val HIDE_DURATION_MS = 175L
        const val SHOW_DURATION_MS = 225L
    }
}
