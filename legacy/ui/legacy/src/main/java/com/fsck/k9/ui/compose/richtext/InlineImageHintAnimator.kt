package com.fsck.k9.ui.compose.richtext

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Build
import android.widget.EditText
import com.fsck.k9.ui.R

// How long the hint is on the screen, and how long it stays there without animators to draw it with.
private const val HINT_DURATION = 1400L
private const val STATIC_HINT_DURATION = 1000L
private const val STATIC_HINT_PROGRESS = 0.5f
private const val HINT_START_PROGRESS = 0f

/**
 * Shows over an inline image that its width can be set by swiping sideways over it, so that the user knows what an
 * image does when it is touched. The hint itself is drawn by [InlineImageSpan]; this only moves it along.
 *
 * The hint is over one image at a time: showing it on another image, or starting to resize, takes the running one away.
 * With the animations of the system switched off it stands still for a moment instead of moving.
 */
internal class InlineImageHintAnimator(private val editText: EditText) {
    private var animator: ValueAnimator? = null
    private var standing: Runnable? = null
    private var image: InlineImageSpan? = null

    /**
     * Shows the hint over [image], taking away a hint that is still running.
     */
    fun show(image: InlineImageSpan) {
        cancel()
        this.image = image
        announce()

        if (areAnimatorsEnabled()) {
            animate(image)
        } else {
            showStatically(image)
        }
    }

    /**
     * Takes the hint away if it is on the screen, so that no piece of it stays over an image.
     */
    fun cancel() {
        animator?.cancel()
        animator = null
        standing?.let { editText.removeCallbacks(it) }
        standing = null
        clear()
    }

    private fun animate(image: InlineImageSpan) {
        // The first frame is drawn right away, so the hint is there in the same frame the user tapped.
        setProgress(image, HINT_START_PROGRESS)

        animator = ValueAnimator.ofFloat(HINT_START_PROGRESS, 1f).apply {
            duration = HINT_DURATION
            addUpdateListener { animation -> setProgress(image, animation.animatedFraction) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    this@InlineImageHintAnimator.animator = null
                    clear()
                }
            })
            start()
        }
    }

    private fun showStatically(image: InlineImageSpan) {
        setProgress(image, STATIC_HINT_PROGRESS)
        val removeHint = Runnable {
            standing = null
            clear()
        }
        standing = removeHint
        editText.postDelayed(removeHint, STATIC_HINT_DURATION)
    }

    private fun setProgress(image: InlineImageSpan, progress: Float) {
        image.hintProgress = progress
        editText.text.redrawImage(image)
    }

    private fun clear() {
        val image = image ?: return
        this.image = null
        image.hintProgress = null
        editText.text.redrawImage(image)
    }

    private fun announce() {
        val text = editText.context.getString(R.string.message_compose_image_resize_hint)
        editText.announceForAccessibility(text)
    }

    private fun areAnimatorsEnabled(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
    }
}
