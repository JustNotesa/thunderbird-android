package com.fsck.k9.view

import android.content.Context
import android.graphics.Color
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.endsWith
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import com.fsck.k9.ui.R
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test
import org.robolectric.annotation.GraphicsMode

private const val ACCOUNT_NAME = "Privat"
private const val SHORT_SUBJECT = "Lieferung verzoegert sich"
private const val LONG_SUBJECT = "Die Lieferung der bestellten Ware hat sich wieder verzögert, weil der Zug " +
    "aus dem Nachbarland erst gestern wieder den Betrieb aufgenommen hat"
private const val ACCOUNT_COLOR = Color.RED

private const val VIEW_WIDTH = 400
private const val MIN_VIEW_WIDTH = 150
private const val MAX_VIEW_WIDTH = 600
private const val VIEW_WIDTH_STEP = 25
private const val MAX_LINES = 3
private const val SUBJECT_TEXT_SIZE_SP = 24f

// The space in front of the dot, which is not a place where the line may break.
private const val SPACE = "\u00A0"

/**
 * The subject of a message is collapsed to a few lines. When the label that tells the user where the message is
 * doesn't fit into those lines, it is replaced by a dot in the colour of the account.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubjectTextBuilderTest : RobolectricTest() {
    private val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(),
        R.style.Theme_Legacy_Test,
    )
    private val subjectPaint = TextPaint().apply {
        textSize = SUBJECT_TEXT_SIZE_SP * context.resources.displayMetrics.scaledDensity
    }

    @Test
    fun `a subject that doesn't fit should be shortened and marked with a dot`() {
        // Arrange
        val testSubject = builderWithLabel(LONG_SUBJECT)

        // Act
        val text = testSubject.buildCollapsed(context, subjectPaint, VIEW_WIDTH, MAX_LINES)

        // Assert: the subject is cut short, marked with an ellipsis and followed by the dot
        val spanned = text as Spanned
        val dotSpan = dotSpansOf(text).single()
        val visibleText = spanned.subSequence(0, spanned.getSpanStart(dotSpan))
        assertThat(visibleText.toString()).endsWith("…$SPACE")
        assertThat(spanned.getSpanEnd(dotSpan) - spanned.getSpanStart(dotSpan)).isEqualTo(1)
    }

    @Test
    fun `the shortened subject should not carry the label`() {
        // Arrange
        val testSubject = builderWithLabel(LONG_SUBJECT)

        // Act
        val text = testSubject.buildCollapsed(context, subjectPaint, VIEW_WIDTH, MAX_LINES)

        // Assert
        assertThat(labelSpansOf(text)).isEmpty()
    }

    @Test
    fun `the shortened subject should fit into the lines the subject is collapsed to`() {
        // Arrange
        val testSubject = builderWithLabel(LONG_SUBJECT)

        // Act
        val text = testSubject.buildCollapsed(context, subjectPaint, VIEW_WIDTH, MAX_LINES)

        // Assert
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, subjectPaint, VIEW_WIDTH)
            .setIncludePad(true)
            .build()
        assertThat(layout.lineCount).isLessThanOrEqualTo(MAX_LINES)
    }

    @Test
    fun `the shortened subject should fit into the lines at every width`() {
        // Arrange
        val testSubject = builderWithLabel(LONG_SUBJECT)
        val widths = (MIN_VIEW_WIDTH..MAX_VIEW_WIDTH step VIEW_WIDTH_STEP).toList()

        // Act
        val texts = widths.map { width -> testSubject.buildCollapsed(context, subjectPaint, width, MAX_LINES) }

        // Assert: the view lays the text out again, which may break the lines differently
        widths.zip(texts).forEach { (width, text) ->
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, subjectPaint, width)
                .setIncludePad(true)
                .build()
            assertThat(layout.lineCount, "width $width").isLessThanOrEqualTo(MAX_LINES)
        }
    }

    @Test
    fun `the shortened subject should use all the lines the subject is collapsed to`() {
        // Arrange
        val testSubject = builderWithLabel(LONG_SUBJECT)

        // Act
        val text = testSubject.buildCollapsed(context, subjectPaint, VIEW_WIDTH, MAX_LINES)

        // Assert: the subject is cut on its last collapsed line, not after the first one
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, subjectPaint, VIEW_WIDTH)
            .setIncludePad(true)
            .build()
        assertThat(layout.lineCount).isEqualTo(MAX_LINES)
    }

    @Test
    fun `the shortened subject should be shorter than the whole subject with its label`() {
        // Arrange
        val testSubject = builderWithLabel(LONG_SUBJECT)

        // Act
        val text = testSubject.buildCollapsed(context, subjectPaint, VIEW_WIDTH, MAX_LINES)

        // Assert
        assertThat(text.length).isLessThan(testSubject.build(context).length)
    }

    @Test
    fun `a subject that fits should be shown with its label`() {
        // Arrange
        val testSubject = builderWithLabel(SHORT_SUBJECT)

        // Act
        val text = testSubject.buildCollapsed(context, subjectPaint, VIEW_WIDTH, MAX_LINES)

        // Assert
        assertThat(text.toString()).isEqualTo("$SHORT_SUBJECT $ACCOUNT_NAME")
        assertThat(labelSpansOf(text)).hasSize(1)
    }

    @Test
    fun `a subject without a label should stand on its own`() {
        // Arrange
        val testSubject = SubjectTextBuilder()
        testSubject.setSubject(LONG_SUBJECT)

        // Act
        val text = testSubject.buildCollapsed(context, subjectPaint, VIEW_WIDTH, MAX_LINES)

        // Assert
        assertThat(text.toString()).isEqualTo(LONG_SUBJECT)
        assertThat(labelSpansOf(text)).isEmpty()
        assertThat(dotSpansOf(text)).isEmpty()
    }

    private fun builderWithLabel(subject: String): SubjectTextBuilder {
        val testSubject = SubjectTextBuilder()
        testSubject.setSubject(subject)
        testSubject.setLocationLabel(LocationLabel(ACCOUNT_NAME, null, ACCOUNT_COLOR))

        return testSubject
    }

    private fun labelSpansOf(text: CharSequence): List<AccountLabelSpan> {
        val spanned = text as? Spanned ?: return emptyList()

        return spanned.getSpans(0, spanned.length, AccountLabelSpan::class.java).toList()
    }

    private fun dotSpansOf(text: CharSequence): List<AccountDotSpan> {
        val spanned = text as? Spanned ?: return emptyList()

        return spanned.getSpans(0, spanned.length, AccountDotSpan::class.java).toList()
    }
}
