package com.fsck.k9.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import com.fsck.k9.ui.R
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test
import org.robolectric.annotation.GraphicsMode

private const val ACCOUNT_COLOR = Color.RED

// The dot as it is laid out on the screen, in the pixels of the default density.
private const val DOT_SIZE = 6
private const val DOT_OFFSET = 20
private const val EDGE_TOLERANCE = 3

private const val BITMAP_WIDTH = 100
private const val BITMAP_HEIGHT = 80
private const val BASELINE = 40
private const val SUBJECT_TEXT_SIZE_SP = 24f

/**
 * The account a message was received on, marked by a dot of its colour. It stands in for the label that tells the
 * user where the message is, when the subject is too long to show the label.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountDotSpanTest : RobolectricTest() {
    private val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(),
        R.style.Theme_Legacy_Test,
    )
    private val subjectPaint = Paint().apply {
        textSize = SUBJECT_TEXT_SIZE_SP * context.resources.displayMetrics.scaledDensity
    }

    @Test
    fun `the dot should be drawn in the color of the account`() {
        // Arrange
        val testSubject = AccountDotSpan(context, ACCOUNT_COLOR)

        // Act
        val bitmap = drawn(testSubject)

        // Assert: the dot is a circle the size it is specified as
        val dot = bounds(bitmap, ACCOUNT_COLOR)
        assertThat(dot.width()).isBetween(DOT_SIZE - EDGE_TOLERANCE, DOT_SIZE)
        assertThat(dot.height()).isBetween(dot.width() - 1, dot.width() + 1)
    }

    @Test
    fun `the dot should be as wide as the dot in front of the name of the account`() {
        // Arrange
        val testSubject = AccountDotSpan(context, ACCOUNT_COLOR)

        // Act
        val width = testSubject.getSize(subjectPaint, "", 0, 0, null)

        // Assert
        assertThat(width).isEqualTo(DOT_SIZE)
    }

    @Test
    fun `the dot should not make the line of the subject any taller`() {
        // Arrange
        val testSubject = AccountDotSpan(context, ACCOUNT_COLOR)
        val fontMetrics = Paint.FontMetricsInt()

        // Act
        testSubject.getSize(subjectPaint, "", 0, 0, fontMetrics)

        // Assert
        assertThat(fontMetrics.ascent).isEqualTo(subjectPaint.fontMetricsInt.ascent)
        assertThat(fontMetrics.descent).isEqualTo(subjectPaint.fontMetricsInt.descent)
    }

    private fun drawn(span: AccountDotSpan): Bitmap {
        val bitmap = Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Bitmap.Config.ARGB_8888)
        span.draw(
            Canvas(bitmap),
            "",
            0,
            0,
            DOT_OFFSET.toFloat(),
            0,
            BASELINE,
            BITMAP_HEIGHT,
            subjectPaint,
        )

        return bitmap
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
}
