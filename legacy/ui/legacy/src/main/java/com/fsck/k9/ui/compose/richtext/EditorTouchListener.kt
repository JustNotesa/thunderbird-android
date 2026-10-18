package com.fsck.k9.ui.compose.richtext

import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * The one touch listener the message text field can have: two fingers zoom the message area ([zoom]), everything else
 * goes to [delegate], which sets the width of an inline image.
 *
 * While the fingers are on the text the gesture belongs to the zoom alone. The text field and the delegate are told
 * that the gesture is over, so that neither the cursor nor a selection nor a width that was being set follows, and the
 * view around the text, which scrolls the whole message, is told not to take the pinch away.
 *
 * The first finger of a pinch reaches the text field before the second one is known to be coming, so the field gets
 * the focus and the keyboard. If it did not have the focus before the gesture, it is given back when the pinch ends.
 */
internal class EditorTouchListener(
    private val editText: EditText,
    private val zoom: EditorZoom,
    private val delegate: View.OnTouchListener,
) : View.OnTouchListener {
    private var isPinching = false
    private var hadFocus = false

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) hadFocus = editText.hasFocus()

        val pinching = zoom.onTouchEvent(event)
        if (pinching != isPinching) onPinchChange(view, event, pinching)
        isPinching = pinching

        return if (pinching) true else delegate.onTouch(view, event)
    }

    private fun onPinchChange(view: View, event: MotionEvent, pinching: Boolean) {
        sendCancel(event)
        if (!pinching && !hadFocus) hideKeyboard(view)
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

    private fun hideKeyboard(view: View) {
        // A pinch is not a tap in the text: the cursor and the keyboard the first finger put there have to go again.
        editText.clearFocus()
        view.context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(view.windowToken, 0)
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
