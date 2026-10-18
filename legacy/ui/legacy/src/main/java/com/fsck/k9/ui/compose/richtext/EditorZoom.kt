package com.fsck.k9.ui.compose.richtext

import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.fsck.k9.ui.R
import com.fsck.k9.view.MessageWebView
import com.fsck.k9.view.PinchListener
import kotlin.math.roundToInt

private const val UNCHANGED_PERCENT = 100
private const val MIN_PERCENT = 70
private const val MAX_PERCENT = 250

// How close a size is to the one the composer set that the user can only have meant that size.
private const val UNCHANGED_RANGE = 3

// How long the pill stays up after the pinch, so that the size it shows can still be read, and how long it takes to
// fade out afterwards.
private const val PILL_DURATION = 1000L
private const val PILL_FADE_DURATION = 200L
private const val INDICATOR_ALPHA = 1f

/**
 * Lets the user make the message area of the composer larger or smaller by spreading or pinching two fingers over it,
 * like zooming.
 *
 * Everything that is registered as a [ZoomTarget] scales with the same factor: the message text, the signature and the
 * quote around it. Subject and recipients are not part of it, so they keep the size they have.
 *
 * This is a zoom of the view alone: the formatting, the message that is saved and the width of the images in it stay as
 * they are, and relative sizes and list indents grow and shrink along with the text. The pill over the message area
 * says so while the fingers are on it.
 *
 * The sizes the composer set are the ones the zoom is measured from, so that spreading the fingers and then pinching
 * them again brings the message back to where it was.
 *
 * @param zoomIndicator the pill that shows the size the user is pinching to
 * @param zoomLabel the text in the pill, which names that size
 */
internal class EditorZoom(
    private val zoomIndicator: View,
    private val zoomLabel: TextView,
) : ScaleGestureDetector.OnScaleGestureListener {
    private val detector = ScaleGestureDetector(zoomIndicator.context, this)
    private val handler = Handler(Looper.getMainLooper())
    private val targets = mutableListOf<ZoomTarget>()
    private val hideIndicator = Runnable { fadeOutIndicator() }
    private var startPercent = UNCHANGED_PERCENT
    private var scaleFactor = 1f
    private var isPinching = false

    /**
     * The size the message area is shown with, in percent of the sizes the composer set.
     */
    var percent = UNCHANGED_PERCENT
        private set

    /**
     * Makes [target] scale with the message area from now on, starting at the size it is left in.
     */
    fun addTarget(target: ZoomTarget) {
        targets += target
        target.setZoomPercent(percent)
    }

    /**
     * Feeds [event] to the pinch detector and tells whether the gesture being made is a pinch, that is whether two
     * fingers have been on the message area since the second one of them touched down.
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
        showIndicator()

        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        handler.removeCallbacks(hideIndicator)
        handler.postDelayed(hideIndicator, PILL_DURATION)
    }

    /**
     * Shows the message area in [percent] of the sizes the composer set, e.g. the ones it was left in.
     */
    fun setPercent(percent: Int) {
        this.percent = unchangedPercent(percent.coerceIn(MIN_PERCENT, MAX_PERCENT))
        targets.forEach { it.setZoomPercent(this.percent) }
    }

    private fun showIndicator() {
        handler.removeCallbacks(hideIndicator)
        zoomIndicator.animate().cancel()
        zoomLabel.text = zoomIndicator.context.getString(R.string.message_compose_zoom_indicator, percent)
        zoomIndicator.alpha = INDICATOR_ALPHA
        zoomIndicator.isVisible = true
    }

    private fun fadeOutIndicator() {
        zoomIndicator.animate()
            .alpha(0f)
            .setDuration(PILL_FADE_DURATION)
            .withEndAction { zoomIndicator.isVisible = false }
            .start()
    }
}

/**
 * Something in the message area of the composer that is made larger or smaller with the message text: a text field, or
 * a preview of a signature or of the quote.
 */
internal interface ZoomTarget {
    /**
     * Shows this in [percent] of the size the composer gave it.
     */
    fun setZoomPercent(percent: Int)
}

/**
 * A text field of the message area. Its text is made larger or smaller instead of being scaled as a whole, so that it
 * stays selectable and wraps in the same lines.
 */
internal class TextViewZoomTarget(private val textView: TextView) : ZoomTarget {
    private var baseTextSize = 0f

    override fun setZoomPercent(percent: Int) {
        // The size the composer sets is only known once it has set it, which it does after this is created.
        if (baseTextSize == 0f) baseTextSize = textView.textSize
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseTextSize * percent / UNCHANGED_PERCENT)
    }
}

/**
 * A preview of a signature or of the quote, which is shown in a [MessageWebView]. It is scaled with the text zoom of
 * the WebView, while the pinch of the WebView itself, which would take the size the reader chose in the message viewer,
 * is replaced by [zoom].
 */
internal class WebViewZoomTarget(
    private val webView: MessageWebView,
    private val zoom: EditorZoom,
) : ZoomTarget {
    private val touchListener = ZoomTouchListener(zoom)

    init {
        webView.pinchListener = PinchListener { event -> touchListener.onTouch(webView, event) }
    }

    override fun setZoomPercent(percent: Int) {
        webView.setTextZoomPercent(percent)
    }
}

/**
 * The size the message area is shown with, in percent of the sizes the composer set, after the distance between the
 * fingers has changed [scaleFactor] times.
 */
internal fun zoomPercent(startPercent: Int, scaleFactor: Float): Int {
    return (startPercent * scaleFactor).roundToInt().coerceIn(MIN_PERCENT, MAX_PERCENT)
}

/**
 * [percent], unless it is so close to the size the composer set that the user can only have meant that size: a pinch
 * that ends there snaps back to it, so that letting the fingers come together undoes the pinch.
 */
internal fun unchangedPercent(percent: Int): Int {
    val unchanged = UNCHANGED_PERCENT - UNCHANGED_RANGE..UNCHANGED_PERCENT + UNCHANGED_RANGE
    return if (percent in unchanged) UNCHANGED_PERCENT else percent
}
