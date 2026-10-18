package com.fsck.k9.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.Spanned
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isBetween
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.fsck.k9.ui.R
import com.google.android.material.R as MaterialR
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

private const val ACCOUNT_NAME = "Privat"
private const val SUBJECT = "Lieferung verzoegert sich"
private const val ACCOUNT_COLOR = Color.RED

// The label as it is laid out on the screen, in the pixels of the default density.
private const val GAP_BEFORE = 8
private const val HORIZONTAL_PADDING = 8
private const val DOT_SIZE = 6
private const val DOT_GAP = 4
private const val DOT_LEFT = GAP_BEFORE + HORIZONTAL_PADDING
private const val EDGE_TOLERANCE = 3

private const val BITMAP_WIDTH = 400
private const val BITMAP_HEIGHT = 80
private const val BASELINE = 40
private const val SUBJECT_TEXT_SIZE_SP = 24f

private val PILL_SURFACE = MaterialR.attr.colorSurfaceContainerHigh

/**
 * The account a message belongs to is a label at the end of the subject, not a chip on a row of its own: a pill that
 * carries the account colour as a dot and leaves the line of the subject the height it was.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountLabelSpanTest : RobolectricTest() {
    private val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(),
        R.style.Theme_Legacy_Test,
    )
    private val subjectPaint = Paint().apply {
        textSize = SUBJECT_TEXT_SIZE_SP * context.resources.displayMetrics.scaledDensity
    }

    @Test
    fun `the label should be as wide as its text with the dot and the padding around it`() {
        // Arrange
        val testSubject = AccountLabelSpan(context, ACCOUNT_COLOR)
        val labelPaint = Paint().apply {
            textSize = context.resources.getDimension(R.dimen.messageViewAccountLabelTextSize)
        }

        // Act
        val width = testSubject.getSize(subjectPaint, ACCOUNT_NAME, 0, ACCOUNT_NAME.length, null)

        // Assert
        val textWidth = labelPaint.measureText(ACCOUNT_NAME).roundToInt()
        assertThat(width).isEqualTo(GAP_BEFORE + 2 * HORIZONTAL_PADDING + DOT_SIZE + DOT_GAP + textWidth)
    }

    @Test
    fun `the label should not make the line of the subject any taller`() {
        // Arrange
        val testSubject = AccountLabelSpan(context, ACCOUNT_COLOR)
        val fontMetrics = Paint.FontMetricsInt()

        // Act
        testSubject.getSize(subjectPaint, ACCOUNT_NAME, 0, ACCOUNT_NAME.length, fontMetrics)

        // Assert
        assertThat(fontMetrics.ascent).isEqualTo(subjectPaint.fontMetricsInt.ascent)
        assertThat(fontMetrics.descent).isEqualTo(subjectPaint.fontMetricsInt.descent)
    }

    @Test
    fun `the account should be marked by a dot of its color in front of the name`() {
        // Arrange
        val testSubject = AccountLabelSpan(context, ACCOUNT_COLOR)

        // Act
        val dot = bounds(drawn(testSubject), ACCOUNT_COLOR)

        // Assert: the dot sits in the padding of the pill and is a circle the size it is specified as
        assertThat(dot.left).isBetween(DOT_LEFT - EDGE_TOLERANCE, DOT_LEFT + EDGE_TOLERANCE)
        assertThat(dot.width()).isBetween(DOT_SIZE - EDGE_TOLERANCE, DOT_SIZE)
        assertThat(dot.height()).isBetween(dot.width() - 1, dot.width() + 1)
    }

    @Test
    fun `the name should be written on a pill of the surface of the theme`() {
        // Arrange
        val testSubject = AccountLabelSpan(context, ACCOUNT_COLOR)
        val pillColor = ThemeUtils.getStyledColor(context, PILL_SURFACE)

        // Act
        val bitmap = drawn(testSubject)

        // Assert: the pill starts at the gap in front of the subject and fills the padding in front of the dot
        val pill = bounds(bitmap, pillColor)
        assertThat(pill.left).isBetween(GAP_BEFORE - EDGE_TOLERANCE, GAP_BEFORE + EDGE_TOLERANCE)
        assertThat(bitmap.column(DOT_LEFT - HORIZONTAL_PADDING / 2)).contains(pillColor)
    }

    @Test
    fun `the subject should carry the name of the account so that it is read out`() {
        // Arrange
        val testSubject = builderWithLabel()

        // Act
        val text = testSubject.build(context)
        val spans = spansOf(text)

        // Assert
        assertThat(text.toString()).isEqualTo("$SUBJECT $ACCOUNT_NAME")
        assertThat(spans).hasSize(1)
        assertThat(coveredText(text, spans.first()).toString()).isEqualTo(ACCOUNT_NAME)
    }

    @Test
    fun `the name of the account should show up when it is set before the subject`() {
        // Arrange
        val testSubject = SubjectTextBuilder()
        testSubject.setAccountLabel(AccountLabel(ACCOUNT_NAME, ACCOUNT_COLOR))

        // Act
        testSubject.setSubject(SUBJECT)

        // Assert
        assertThat(testSubject.build(context).toString()).isEqualTo("$SUBJECT $ACCOUNT_NAME")
    }

    @Test
    fun `the subject should stand on its own when there is no account to show`() {
        // Arrange
        val testSubject = SubjectTextBuilder()
        testSubject.setSubject(SUBJECT)

        // Act
        val text = testSubject.build(context)

        // Assert
        assertThat(text.toString()).isEqualTo(SUBJECT)
        assertThat(spansOf(text)).isEmpty()
    }

    @Test
    fun `copying the subject should leave the name of the account out`() {
        // Arrange
        val testSubject = builderWithLabel()

        // Act
        val clipboardText = testSubject.clipboardText()

        // Assert
        assertThat(clipboardText).isEqualTo(SUBJECT)
    }

    private fun builderWithLabel(): SubjectTextBuilder {
        val testSubject = SubjectTextBuilder()
        testSubject.setSubject(SUBJECT)
        testSubject.setAccountLabel(AccountLabel(ACCOUNT_NAME, ACCOUNT_COLOR))

        return testSubject
    }

    private fun drawn(span: AccountLabelSpan): Bitmap {
        val bitmap = Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Bitmap.Config.ARGB_8888)
        span.draw(
            Canvas(bitmap),
            ACCOUNT_NAME,
            0,
            ACCOUNT_NAME.length,
            0f,
            0,
            BASELINE,
            BITMAP_HEIGHT,
            subjectPaint,
        )

        return bitmap
    }

    private fun spansOf(text: CharSequence): List<AccountLabelSpan> {
        if (text !is Spanned) {
            return emptyList()
        }

        return text.getSpans(0, text.length, AccountLabelSpan::class.java).toList()
    }

    private fun coveredText(text: CharSequence, span: AccountLabelSpan): CharSequence {
        val spanned = text as Spanned

        return spanned.subSequence(spanned.getSpanStart(span), spanned.getSpanEnd(span))
    }

    /** The pixel range this bitmap holds the given color in. */
    private fun bounds(bitmap: Bitmap, color: Int): Rect {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (bitmap.getPixel(x, y) == color) {
                    left = minOf(left, x)
                    right = maxOf(right, x)
                    top = minOf(top, y)
                    bottom = maxOf(bottom, y)
                }
            }
        }

        return Rect(left, top, right, bottom)
    }

    private fun Bitmap.column(x: Int): List<Int> {
        return (0 until height).map { y -> getPixel(x, y) }
    }
}
