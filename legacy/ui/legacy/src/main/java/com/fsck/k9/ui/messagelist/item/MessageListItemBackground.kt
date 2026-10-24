package com.fsck.k9.ui.messagelist.item

import android.animation.ValueAnimator
import android.content.res.Resources
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import com.fsck.k9.ui.R

/**
 * The background of a message list row: a rounded rectangle that is inset from the sides of the list, so the
 * rows are separated by whitespace instead of by a divider line.
 *
 * A row is rebound every time it is recycled, so the [Drawable] is created once and only recolored via [setColor].
 */
internal class MessageListItemBackground(res: Resources, color: Int) {
    private val shape = GradientDrawable()
    private var color: Int = color

    val drawable: Drawable

    init {
        shape.setColor(color)
        shape.cornerRadius = res.getDimension(R.dimen.messageListItemCornerRadius)

        val horizontalInset = res.getDimensionPixelSize(R.dimen.messageListItemBackgroundInset)
        drawable = ContentKeepingInsetDrawable(shape, horizontalInset)
    }

    fun setColor(color: Int) {
        this.color = color
        shape.setColor(color)
    }

    /**
     * Fade the background to [highlightColor] and back to the color of the row, so that it stands out for a moment.
     *
     * @return the animation, so that it can be stopped when the row is used for another message.
     */
    fun pulse(highlightColor: Int, durationMillis: Long = PULSE_DURATION_MILLIS): ValueAnimator {
        val animator = ValueAnimator.ofArgb(color, highlightColor, color)
        animator.duration = durationMillis
        animator.addUpdateListener { animation ->
            shape.setColor(animation.animatedValue as Int)
        }
        animator.start()

        return animator
    }

    private companion object {
        const val PULSE_DURATION_MILLIS = 1200L
    }
}

/**
 * Insets the shape without insetting the row: an [InsetDrawable] would otherwise hand its inset to the view as padding
 * and move the content of the row away from where it is laid out.
 */
private class ContentKeepingInsetDrawable(drawable: Drawable, horizontalInset: Int) :
    InsetDrawable(drawable, horizontalInset, 0, horizontalInset, 0) {
    override fun getPadding(padding: Rect): Boolean {
        padding.setEmpty()

        return false
    }
}
