package com.fsck.k9.ui.compose.richtext

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import kotlin.math.abs

/**
 * The one touch listener the message text field can have: two fingers zoom the message area ([zoom]), everything else
 * goes to [delegate], which sets the width of an inline image.
 *
 * While the fingers are on the text the gesture belongs to the zoom alone, and the view around the text, which scrolls
 * the whole message, is told not to take the pinch away.
 *
 * The first finger of a pinch reaches the field before the second one is known to be coming, and a finger the field has
 * already acted on takes the focus, places the cursor and raises the keyboard - the cursor then sits where that finger
 * happened to be down rather than where the user meant it. So the finger that goes down is kept back from the field for
 * as long as a tap may last ([ViewConfiguration.getTapTimeout]) and is given to it as it went down as soon as the
 * gesture is known to be a single finger: a tap, a movement further than the slop of a finger, or a long press that has
 * waited out the time a tap may take. A second finger in the meantime is a pinch, which the field never sees at all.
 */
internal class EditorTouchListener(
    private val editText: EditText,
    private val zoom: EditorZoom,
    private val delegate: View.OnTouchListener,
) : View.OnTouchListener {
    private val touchSlop = ViewConfiguration.get(editText.context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())
    private val replayHeld = Runnable { replay() }
    private var isPinching = false
    private var isGivenToField = false
    private var held: MotionEvent? = null
    private var downX = 0f
    private var downY = 0f

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val pinching = zoom.onTouchEvent(event)
        if (pinching != isPinching) onPinchChange(view, event, pinching)
        isPinching = pinching

        if (event.actionMasked == MotionEvent.ACTION_DOWN) return holdDown(view, event)

        // The field is given what it has been kept back from before the event that ends the waiting, so that the
        // delegate never has to take back a gesture the field was never given.
        releaseHeld(event)

        val handled = if (pinching) true else delegate.onTouch(view, event)

        // A gesture the field was never given is ours alone: it must not reach it with the finger that follows it.
        return handled || !isGivenToField
    }

    /**
     * Keeps the finger that went down back from the field for as long as a tap may take, so that the second finger of
     * a pinch can still arrive before the field places a cursor and raises the keyboard. The delegate sees the gesture
     * begin where the field will find it.
     */
    private fun holdDown(view: View, event: MotionEvent): Boolean {
        downX = event.x
        downY = event.y
        held = MotionEvent.obtain(event)
        isGivenToField = false
        handler.postDelayed(replayHeld, ViewConfiguration.getTapTimeout().toLong())
        delegate.onTouch(view, event)

        return true
    }

    /**
     * Gives the field the finger it was kept back from, or lets it go, as soon as the gesture shows what it is: a tap
     * is played out as it was, a pinch and a gesture the view around the text has taken over are forgotten.
     */
    private fun releaseHeld(event: MotionEvent) {
        if (held == null) return
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> drop()
            MotionEvent.ACTION_UP -> replay()
            MotionEvent.ACTION_MOVE -> if (isBeyondTouchSlop(event)) replay()
        }
    }

    private fun replay() {
        val held = held ?: return
        this.held = null
        isGivenToField = true
        handler.removeCallbacks(replayHeld)
        try {
            editText.onTouchEvent(held)
        } finally {
            held.recycle()
        }
    }

    private fun drop() {
        handler.removeCallbacks(replayHeld)
        held?.recycle()
        held = null
    }

    /** Whether the finger has moved so far that the gesture cannot be a tap any more. */
    private fun isBeyondTouchSlop(event: MotionEvent): Boolean {
        return abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop
    }

    private fun onPinchChange(view: View, event: MotionEvent, pinching: Boolean) {
        // The delegate saw the gesture begin, so it is told it is over; the field only if it was given the gesture.
        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
        try {
            if (isGivenToField) editText.onTouchEvent(cancel)
            delegate.onTouch(editText, cancel)
        } finally {
            cancel.recycle()
        }
        view.parent?.requestDisallowInterceptTouchEvent(pinching)
    }
}

/**
 * The touch listener of a view of the message area that has nothing else to do with touches, e.g. a signature or a
 * quote next to the message text: two fingers zoom the message area ([zoom]), a single finger is left to the view.
 *
 * The view around it, which scrolls the whole message, is told not to take the pinch away.
 */
internal class ZoomTouchListener(
    private val zoom: EditorZoom,
) : View.OnTouchListener {
    private var isPinching = false

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val pinching = zoom.onTouchEvent(event)
        if (pinching != isPinching) view.parent?.requestDisallowInterceptTouchEvent(pinching)
        isPinching = pinching

        return pinching
    }
}
