package com.fsck.k9.ui.compose.richtext

import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import kotlin.math.roundToInt

private const val UNCHANGED_PERCENT = 100
private const val MIN_PERCENT = 70
private const val MAX_PERCENT = 250

// How long the label stays up after the pinch, so that the size it shows can still be read.
private const val LABEL_DURATION = 700L
private const val PERCENT = " %"

/**
 * Lets the user make the message text larger or smaller by spreading or pinching two fingers over it, like zooming.
 *
 * Only the size the text is displayed with changes: its formatting, the message that is saved and the width of the
 * images in it stay as they are, and relative sizes and list indents grow and shrink along with the text.
 *
 * The size the composer set is kept as the size the zoom is measured from, so that spreading the fingers and then
 * pinching them again brings the text back to where it was.
 *
 * @param zoomIndicator the label that shows the size the user is pinching to
 */
internal class EditorZoom(
    private val editText: EditText,
    private val zoomIndicator: TextView,
) : ScaleGestureDetector.OnScaleGestureListener {
    private val detector = ScaleGestureDetector(editText.context, this)
    private val handler = Handler(Looper.getMainLooper())
    private val hideIndicator = Runnable { zoomIndicator.isVisible = false }
    private var normalTextSize = 0f
    private var startPercent = UNCHANGED_PERCENT
    private var scaleFactor = 1f
    private var isPinching = false

    /**
     * The size the text is shown with, in percent of the size the composer set.
     */
    var percent = UNCHANGED_PERCENT
        private set

    /**
     * Feeds [event] to the pinch detector and tells whether the gesture being made is a pinch, that is whether two
     * fingers have been on the text since the second one of them touched down.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> isPinching = false
            MotionEvent.ACTION_POINTER_DOWN -> isPinching = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isPinching = false
        }
        detector.onTouchEvent(event)

        return isPinching
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        startPercent = percent
        scaleFactor = 1f

        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        // A scale is the distance between two fingers: once one of them is lifted the detector goes on reporting the
        // distance of the one point that is left to itself, which would take the text to one of the two sizes.
        if (detector.currentSpan == 0f) return true

        scaleFactor *= detector.scaleFactor
        setPercent(zoomPercent(startPercent, scaleFactor))
        showIndicator(percent)

        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        handler.removeCallbacks(hideIndicator)
        handler.postDelayed(hideIndicator, LABEL_DURATION)
    }

    /**
     * Shows the message text in [percent] of the size the composer set, e.g. the one it was left in.
     */
    fun setPercent(percent: Int) {
        this.percent = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)

        // The size the composer sets is only known once it has set it, which it does after this is created.
        if (normalTextSize == 0f) normalTextSize = editText.textSize
        editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, normalTextSize * this.percent / UNCHANGED_PERCENT)
    }

    private fun showIndicator(percent: Int) {
        zoomIndicator.text = "$percent$PERCENT"
        zoomIndicator.isVisible = true
    }
}

/**
 * The size the text is shown with, in percent of the size the composer set, after the distance between the fingers has
 * changed [scaleFactor] times.
 */
internal fun zoomPercent(startPercent: Int, scaleFactor: Float): Int {
    return (startPercent * scaleFactor).roundToInt().coerceIn(MIN_PERCENT, MAX_PERCENT)
}
