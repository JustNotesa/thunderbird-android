package com.fsck.k9.ui.compose.richtext

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import androidx.appcompat.content.res.AppCompatResources
import com.fsck.k9.ui.R
import app.k9mail.core.ui.legacy.designsystem.R as DesignSystemR

// How long the hint is on the screen: it fades in, the arrow stretches and shrinks twice, and then the hint fades out.
private const val FADE_IN_DURATION = 150L
private const val STRETCH_DURATION = 2000L
private const val FADE_OUT_DURATION = 250L

// How long the hint stands still when there are no animators to draw it with, and how much of it is there then.
private const val STATIC_HINT_DURATION = 1200L
private const val HINT_START_PROGRESS = 0f
private const val FULL_PROGRESS = 1f
private const val HIDDEN_OPACITY = 0f
private const val FULL_OPACITY = 1f

/**
 * Shows over an inline image that its width can be set by swiping sideways over it, so that the user knows what an
 * image does when it is touched. The hint itself is drawn by [InlineImageSpan]; this only moves it along: the arrow
 * stretches and shrinks, and the finger drags its right head with it.
 *
 * The hint is over one image at a time: showing it on another image, or starting to resize, takes the running one away.
 * With the animations of the system switched off it stands still for a moment instead of moving.
 */
internal class InlineImageHintAnimator(private val editText: EditText) {
    private var animator: Animator? = null
    private var standing: Runnable? = null
    private var image: InlineImageSpan? = null
    private var progress = HINT_START_PROGRESS

    /** The finger that drags the right head of the arrow, which is the same picture for every hint. */
    private val finger: Drawable? by lazy {
        AppCompatResources.getDrawable(editText.context, DesignSystemR.drawable.ic_touch_app)
    }

    /**
     * Shows the hint over [image], taking away a hint that is still running.
     */
    fun show(image: InlineImageSpan) {
        cancel()
        this.image = image
        image.hintFinger = finger
        announce()

        if (areAnimatorsEnabled()) {
            progress = HINT_START_PROGRESS
            animate(image)
        } else {
            progress = HINT_FULL_WIDTH_PROGRESS
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
        drawHint(image, HIDDEN_OPACITY)

        val stretching = ValueAnimator.ofFloat(HINT_START_PROGRESS, FULL_PROGRESS).apply {
            duration = STRETCH_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                progress = animation.animatedFraction
                drawHint(image, FULL_OPACITY)
            }
        }
        val phases = AnimatorSet()
        phases.playSequentially(
            fade(image, HIDDEN_OPACITY, FULL_OPACITY, FADE_IN_DURATION),
            stretching,
            fade(image, FULL_OPACITY, HIDDEN_OPACITY, FADE_OUT_DURATION),
        )
        phases.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // A hint that was taken away can still end; it must not take the one that took its place with it.
                if (animation !== this@InlineImageHintAnimator.animator) return

                this@InlineImageHintAnimator.animator = null
                clear()
            }
        })

        animator = phases
        phases.start()
    }

    private fun showStatically(image: InlineImageSpan) {
        drawHint(image, FULL_OPACITY)

        val removeHint = Runnable {
            standing = null
            clear()
        }
        standing = removeHint
        editText.postDelayed(removeHint, STATIC_HINT_DURATION)
    }

    /** Fades the whole hint in or out over [duration], which the arrow does not notice. */
    private fun fade(image: InlineImageSpan, from: Float, to: Float, duration: Long): ValueAnimator {
        return ValueAnimator.ofFloat(from, to).apply {
            this.duration = duration
            addUpdateListener { animation -> drawHint(image, animation.animatedValue as Float) }
        }
    }

    /** Puts the hint where it has come and how much of it is there, and has the text field draw it. */
    private fun drawHint(image: InlineImageSpan, opacity: Float) {
        image.hintOpacity = opacity
        image.hintProgress = progress
        redraw(image)
    }

    private fun redraw(image: InlineImageSpan) {
        // The text field keeps what it drew until the text changes, so every frame has to ask it for a new one.
        editText.text.redrawImage(image)
        editText.invalidate()
    }

    private fun clear() {
        val image = image ?: return
        this.image = null
        image.hintProgress = null
        image.hintFinger = null
        redraw(image)
    }

    private fun announce() {
        val text = editText.context.getString(R.string.message_compose_image_resize_hint)
        editText.announceForAccessibility(text)
    }

    private fun areAnimatorsEnabled(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
    }
}
