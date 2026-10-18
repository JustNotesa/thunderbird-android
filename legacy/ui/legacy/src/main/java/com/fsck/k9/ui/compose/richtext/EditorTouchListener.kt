package com.fsck.k9.ui.compose.richtext

import android.view.MotionEvent
import android.view.View
import android.widget.EditText

/**
 * The one touch listener the message text field can have: two fingers zoom the text ([zoom]), everything else goes to
 * [delegate], which sets the width of an inline image.
 *
 * While the fingers are on the text the gesture belongs to the zoom alone. The text field and the delegate are told
 * that the gesture is over, so that neither the cursor nor a selection nor a width that was being set follows, and the
 * view around the text, which scrolls the whole message, is told not to take the pinch away.
 */
internal class EditorTouchListener(
    private val editText: EditText,
    private val zoom: EditorZoom,
    private val delegate: View.OnTouchListener,
) : View.OnTouchListener {
    private var isPinching = false

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val pinching = zoom.onTouchEvent(event)
        if (pinching != isPinching) onPinchChange(view, event, pinching)
        isPinching = pinching

        return if (pinching) true else delegate.onTouch(view, event)
    }

    private fun onPinchChange(view: View, event: MotionEvent, pinching: Boolean) {
        sendCancel(event)
        view.parent?.requestDisallowInterceptTouchEvent(pinching)
    }

    private fun sendCancel(event: MotionEvent) {
        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
        try {
            editText.onTouchEvent(cancel)
            delegate.onTouch(editText, cancel)
        } finally {
            cancel.recycle()
        }
    }
}
