package com.fsck.k9.view

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.Spanned
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isTrue
import com.fsck.k9.ui.R
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test
import org.robolectric.annotation.GraphicsMode

private const val ACCOUNT_NAME = "Privat"
private const val SUBJECT = "Lieferung verzoegert sich"
private const val LONG_SUBJECT = "Die Lieferung der bestellten Ware hat sich wieder verzögert, weil der Zug " +
    "aus dem Nachbarland erst gestern wieder den Betrieb aufgenommen hat"
private const val ACCOUNT_COLOR = Color.RED

private const val VIEW_WIDTH = 400
private const val VIEW_HEIGHT = 200
private const val VIEW_PADDING = 4
private const val TOUCH_SLOP = 8

/**
 * The account a message belongs to is a label at the end of the subject. A tap on it opens the inbox of that account,
 * a tap on the subject itself shows the rest of the subject.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountLabelHitTestTest : RobolectricTest() {
    private val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(),
        R.style.Theme_Legacy_Test,
    )

    @Test
    fun `a tap in the middle of the label should be on it`() {
        // Arrange
        val text = builderWithLabel(SUBJECT).build(context)
        val layout = layoutOf(text)

        // Act
        val touchX = middleOfLabel(layout, text)
        val touchY = lineCentre(layout, lineOfLabel(layout, text))

        // Assert
        assertThat(isTouchOnLabel(layout, text, touchX, touchY)).isTrue()
    }

    @Test
    fun `a tap on the subject should not be on the label`() {
        // Arrange
        val text = builderWithLabel(SUBJECT).build(context)
        val layout = layoutOf(text)
        val line = lineOfLabel(layout, text)

        // Act
        val touchX = (spanLeft(layout, text) / 2).toInt()
        val touchY = lineCentre(layout, line)

        // Assert
        assertThat(isTouchOnLabel(layout, text, touchX, touchY)).isFalse()
    }

    @Test
    fun `a tap below the line of the label should not be on it, past the touch slop`() {
        // Arrange
        val text = builderWithLabel(SUBJECT).build(context)
        val layout = layoutOf(text)
        val line = lineOfLabel(layout, text)

        // Act
        val touchX = middleOfLabel(layout, text)
        val touchY = layout.getLineBottom(line) + TOUCH_SLOP + 1

        // Assert
        assertThat(isTouchOnLabel(layout, text, touchX, touchY)).isFalse()
    }

    @Test
    fun `a tap right below the line of the label should be on it, within the touch slop`() {
        // Arrange
        val text = builderWithLabel(SUBJECT).build(context)
        val layout = layoutOf(text)
        val line = lineOfLabel(layout, text)

        // Act: the last row of pixels the label covers is the line plus the slop, minus one
        val touchX = middleOfLabel(layout, text)
        val touchY = layout.getLineBottom(line) + TOUCH_SLOP - 1

        // Assert
        assertThat(isTouchOnLabel(layout, text, touchX, touchY)).isTrue()
    }

    @Test
    fun `a subject without a label should have no label to tap on`() {
        // Arrange
        val text = builderWithoutLabel().build(context)
        val layout = layoutOf(text)

        // Act
        val touchX = VIEW_WIDTH / 2
        val touchY = lineCentre(layout, 0)

        // Assert
        assertThat(isTouchOnLabel(layout, text, touchX, touchY)).isFalse()
    }

    @Test
    fun `the label should be found on the line it is on`() {
        // Arrange: a subject that doesn't fit on one line, so that the label moves to the next one
        val text = builderWithLabel(LONG_SUBJECT).build(context)
        val layout = layoutOf(text)

        // Act
        val line = lineOfLabel(layout, text)
        val touchX = middleOfLabel(layout, text)
        val touchY = lineCentre(layout, line)

        // Assert
        assertThat(line).isGreaterThan(0)
        assertThat(isTouchOnLabel(layout, text, touchX, touchY)).isTrue()
    }

    /** A tap at this position in the text, which the view reports at the given padding. */
    private fun isTouchOnLabel(layout: Layout, text: CharSequence, textX: Int, textY: Int): Boolean {
        return AccountLabelHitTest.isTouchOnAccountLabel(
            layout,
            text,
            textX + VIEW_PADDING,
            textY + VIEW_PADDING,
            VIEW_PADDING,
            VIEW_PADDING,
            TOUCH_SLOP,
        )
    }

    private fun builderWithLabel(subject: String): SubjectTextBuilder {
        val testSubject = SubjectTextBuilder()
        testSubject.setSubject(subject)
        testSubject.setAccountLabel(AccountLabel(ACCOUNT_NAME, ACCOUNT_COLOR))

        return testSubject
    }

    private fun builderWithoutLabel(): SubjectTextBuilder {
        val testSubject = SubjectTextBuilder()
        testSubject.setSubject(SUBJECT)

        return testSubject
    }

    /** The text as it is laid out on the screen: a [TextView] of a fixed size that holds it. */
    private fun layoutOf(text: CharSequence): Layout {
        val textView = TextView(context)
        textView.text = text
        textView.setPadding(VIEW_PADDING, VIEW_PADDING, VIEW_PADDING, VIEW_PADDING)
        textView.measure(
            View.MeasureSpec.makeMeasureSpec(VIEW_WIDTH, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(VIEW_HEIGHT, View.MeasureSpec.AT_MOST),
        )
        textView.layout(0, 0, textView.measuredWidth, textView.measuredHeight)

        return checkNotNull(textView.layout)
    }

    private fun lineOfLabel(layout: Layout, text: CharSequence): Int {
        return layout.getLineForOffset(spanStartOfLabel(text))
    }

    private fun spanStartOfLabel(text: CharSequence): Int {
        val spanned = text as Spanned
        val span = spanned.getSpans(0, spanned.length, AccountLabelSpan::class.java).single()

        return spanned.getSpanStart(span)
    }

    private fun spanLeft(layout: Layout, text: CharSequence): Float {
        return layout.getPrimaryHorizontal(spanStartOfLabel(text))
    }

    /** The width the label takes in the line: the gap in front of the subject and the pill behind it. */
    private fun spanWidth(text: CharSequence): Int {
        val spanned = text as Spanned
        val span = spanned.getSpans(0, spanned.length, AccountLabelSpan::class.java).single()

        return span.getSize(Paint(), spanned, spanned.getSpanStart(span), spanned.getSpanEnd(span), null)
    }

    private fun middleOfLabel(layout: Layout, text: CharSequence): Int {
        return (spanLeft(layout, text) + spanWidth(text) / 2).toInt()
    }

    private fun lineCentre(layout: Layout, line: Int): Int {
        return (layout.getLineTop(line) + layout.getLineBottom(line)) / 2
    }
}
