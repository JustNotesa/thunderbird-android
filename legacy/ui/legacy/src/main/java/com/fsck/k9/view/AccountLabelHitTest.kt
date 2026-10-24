package com.fsck.k9.view

import android.graphics.Rect
import android.text.Layout
import android.text.Spanned

/**
 * Whether a tap on the subject of a message was on the label at its end that tells the user where the message is.
 * That brings the message back into view in the list it was opened from, while a tap anywhere else on the subject
 * shows all of it.
 *
 * The label is a small pill inside the line of the subject it is on, so the whole height of that line counts as the
 * target of the tap, with a little more around it.
 */
internal object AccountLabelHitTest {
    /**
     * @param layout the layout of the text of the view the tap was in
     * @param text the text of that view, which carries the label as a span
     * @param touchX horizontal position of the tap in the coordinates of the view
     * @param touchY vertical position of the tap in the coordinates of the view
     * @param viewPaddingLeft padding of the view on the left
     * @param viewPaddingTop padding of the view at the top
     * @param verticalTouchSlop extra height around the line of the label, in pixels
     */
    fun isTouchOnAccountLabel(
        layout: Layout,
        text: CharSequence,
        touchX: Int,
        touchY: Int,
        viewPaddingLeft: Int,
        viewPaddingTop: Int,
        verticalTouchSlop: Int,
    ): Boolean {
        val labelBounds = labelBounds(layout, text, verticalTouchSlop)

        return labelBounds?.contains(touchX - viewPaddingLeft, touchY - viewPaddingTop) == true
    }

    /** The area the label is drawn in, in the coordinates of the text, or null when the text carries no label. */
    private fun labelBounds(layout: Layout, text: CharSequence, verticalTouchSlop: Int): Rect? {
        val spanned = text as? Spanned
        val span = spanned?.getSpans(0, spanned.length, AccountLabelSpan::class.java)?.firstOrNull()
        if (spanned == null || span == null) return null

        val spanStart = spanned.getSpanStart(span)
        val line = layout.getLineForOffset(spanStart)
        val pill = span.pillBounds(spanned, spanStart, spanned.getSpanEnd(span))
        val spanLeft = layout.getPrimaryHorizontal(spanStart)

        return Rect(
            (spanLeft + pill.first).toInt(),
            layout.getLineTop(line) - verticalTouchSlop,
            (spanLeft + pill.last + 1).toInt(),
            layout.getLineBottom(line) + verticalTouchSlop,
        )
    }
}
