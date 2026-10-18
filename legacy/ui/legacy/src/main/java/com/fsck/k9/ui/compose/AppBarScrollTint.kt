package com.fsck.k9.ui.compose

import android.animation.ValueAnimator
import android.view.View
import androidx.annotation.ColorInt

/**
 * Gives the app bar of the composer the look Gmail has: at the top it has the colour of the message area below it,
 * and scrolled message content lifts it by giving it the colour of a container surface - without a line under it and
 * without a shadow.
 *
 * Only the moment the message area leaves the top or comes back to it changes the colour; scrolling on within the
 * message doesn't start the animation again.
 *
 * @param appBar the app bar, whose background is also what is shown behind the status bar
 * @param toolbar the toolbar in the app bar, which has a background of its own
 * @param atRestColor the colour of the message area, for as long as the message isn't scrolled
 * @param scrolledColor the colour of the app bar while the message is scrolled
 */
class AppBarScrollTint(
    private val appBar: View,
    private val toolbar: View,
    @ColorInt private val atRestColor: Int,
    @ColorInt private val scrolledColor: Int,
) {
    private var isScrolled = false
    private var color = atRestColor
    private var animator: ValueAnimator? = null

    init {
        setColor(atRestColor)
    }

    /**
     * Tells the app bar how far the message area is scrolled.
     */
    fun onScrolled(scrollY: Int) {
        val scrolled = scrollY > 0
        if (scrolled == isScrolled) return

        isScrolled = scrolled
        animateTo(if (scrolled) scrolledColor else atRestColor)
    }

    private fun animateTo(color: Int) {
        animator?.cancel()
        animator = ValueAnimator.ofArgb(this.color, color).apply {
            duration = ANIMATION_DURATION
            addUpdateListener { animation -> setColor(animation.animatedValue as Int) }
            start()
        }
    }

    private fun setColor(color: Int) {
        this.color = color
        appBar.setBackgroundColor(color)
        toolbar.setBackgroundColor(color)
    }

    private companion object {
        const val ANIMATION_DURATION = 150L
    }
}
