package com.fsck.k9.ui.compose.richtext

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import net.thunderbird.feature.mail.message.composer.richtext.InlineImage
import kotlin.math.abs
import kotlin.math.roundToInt

// The width of an image is set in steps of this many percent, so that the gesture does not feel twitchy.
private const val PERCENT_STEP = 5

// A finger that is down for longer than this is not a tap, so it gets no hint about the swipe.
private const val TAP_TIMEOUT_FACTOR = 3

/**
 * Lets the user set the width of an inline image by swiping sideways over it: the image follows the finger, and the
 * width it would be sent with is shown on it.
 *
 * The text field keeps the touch handling for everything else. A tap places the cursor, a vertical swipe scrolls, and
 * a swipe that starts somewhere other than on an image selects text, all as before. Only a swipe that has moved more
 * sideways than up, and further than the finger's slop, is taken over - until the finger is lifted again, so that no
 * text is selected and the cursor stays where it was.
 *
 * A tap on an image is not taken over: it places the cursor as before and shows a hint that says what a swipe over the
 * image does, for a user who does not know it yet.
 *
 * @param onBeforeChange called once per gesture, before it changes the width of an image for the first time
 * @param onChange called when the gesture is over, so that its result is kept like any other change of formatting
 * @param hints shows the hint over an image that was tapped
 */
internal class InlineImageResizer(
    private val editText: EditText,
    private val onBeforeChange: () -> Unit,
    private val onChange: () -> Unit,
    private val hints: InlineImageHintAnimator = InlineImageHintAnimator(editText),
) : View.OnTouchListener {
    private val touchSlop = ViewConfiguration.get(editText.context).scaledTouchSlop
    private val tapTimeout = ViewConfiguration.getTapTimeout() * TAP_TIMEOUT_FACTOR
    private var span: InlineImageSpan? = null
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var startPercent = 0
    private var isResizing = false

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(event)
            MotionEvent.ACTION_MOVE -> onMove(view, event)
            MotionEvent.ACTION_UP -> onUp(view, event)
            MotionEvent.ACTION_CANCEL -> onUp(view, event)
            else -> false
        }
    }

    private fun onDown(event: MotionEvent): Boolean {
        downX = event.x
        downY = event.y
        downTime = event.downTime
        span = editText.imageSpanAt(event.x, event.y)

        // The text field gets the gesture as well: it decides what a tap or a scroll does.
        return false
    }

    private fun onMove(view: View, event: MotionEvent): Boolean {
        val image = span ?: return false
        val dx = event.x - downX
        val dy = event.y - downY

        // The gesture belongs to the image as soon as it swipes to the side, and every move of it belongs there after.
        val takenOver = isResizing || isSwipeToTheSide(dx, dy)
        if (takenOver && !isResizing) {
            // The text field saw the finger go down; without a cancel it would place the cursor or select text.
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            editText.onTouchEvent(cancel)
            cancel.recycle()
            startResize(view, image)
        }
        if (takenOver) resize(view, image, dx)

        return takenOver
    }

    private fun onUp(view: View, event: MotionEvent): Boolean {
        val image = span
        span = null
        if (!isResizing) {
            // The tap belongs to the text field; the hint only says what the swipe the user has not found does.
            if (isTap(event)) image?.let { hints.show(it) }

            return false
        }

        isResizing = false
        image?.isResizing = false
        image?.let { editText.text.redrawImage(it) }
        view.parent?.requestDisallowInterceptTouchEvent(false)
        view.invalidate()
        onChange()

        return true
    }

    /** Whether the finger has moved sideways far enough to tell the swipe apart from a scroll. */
    private fun isSwipeToTheSide(dx: Float, dy: Float): Boolean {
        return abs(dx) > touchSlop && abs(dx) > abs(dy)
    }

    /**
     * Whether the finger went down and up so close together, and so still, that it was a tap on the image rather than
     * a swipe that is already over. A gesture that was cancelled is not a tap, however short it was.
     */
    private fun isTap(event: MotionEvent): Boolean {
        val isLifted = event.actionMasked == MotionEvent.ACTION_UP
        val isStill = abs(event.x - downX) <= touchSlop && abs(event.y - downY) <= touchSlop

        return isLifted && isStill && event.eventTime - downTime <= tapTimeout
    }

    private fun startResize(view: View, image: InlineImageSpan) {
        isResizing = true
        startPercent = image.widthPercent ?: image.naturalPercent(textWidth())
        image.isResizing = true
        hints.cancel()
        // The text field scrolls the whole message, so the swipe must not scroll it away under the finger.
        view.parent?.requestDisallowInterceptTouchEvent(true)
        onBeforeChange()
    }

    private fun resize(view: View, image: InlineImageSpan, dx: Float) {
        val width = textWidth()
        if (width <= 0) return

        val percent = startPercent + dx * InlineImage.MAX_WIDTH_PERCENT / width
        editText.text.setWidthPercent(image, percent.inStepsOf(PERCENT_STEP))
        view.requestLayout()
    }

    private fun textWidth(): Int {
        return editText.width - editText.paddingLeft - editText.paddingRight
    }
}

/**
 * The percentage the gesture asks for, in steps of [step] percent.
 */
private fun Float.inStepsOf(step: Int): Int {
    val steps = (this / step).roundToInt()

    return (steps * step).coerceIn(InlineImage.MIN_WIDTH_PERCENT, InlineImage.MAX_WIDTH_PERCENT)
}
