package com.fsck.k9.ui.messagelist.item

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import com.fsck.k9.ui.R
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test
import org.robolectric.annotation.GraphicsMode

private const val WIDTH = 200
private const val HEIGHT = 60
private const val CLEAR = 0

/**
 * What the background of a message list row looks like on screen: it holds the state colour of the row, and it is
 * inset from the sides of the list with rounded corners, so that the rows are told apart by whitespace and not by
 * a divider line.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageListItemBackgroundTest : RobolectricTest() {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val inset = context.resources.getDimensionPixelSize(R.dimen.messageListItemBackgroundInset)
    private val cornerRadius = context.resources.getDimension(R.dimen.messageListItemCornerRadius).toInt()

    @Test
    fun `the background should paint its color over the whole row`() {
        // Arrange
        val testSubject = MessageListItemBackground(context.resources, Color.RED)

        // Act
        val bitmap = draw(testSubject.drawable)

        // Assert
        assertThat(bitmap.getPixel(WIDTH / 2, HEIGHT / 2)).isEqualTo(Color.RED)
    }

    @Test
    fun `the background should be inset from the sides of the row`() {
        // Arrange
        val testSubject = MessageListItemBackground(context.resources, Color.RED)

        // Act
        val bounds = draw(testSubject.drawable).paintedBounds()

        // Assert
        assertThat(bounds).isEqualTo(Rect(inset, 0, WIDTH - inset - 1, HEIGHT - 1))
    }

    @Test
    fun `the background should not move the content of the row`() {
        // Arrange
        val testSubject = MessageListItemBackground(context.resources, Color.RED)
        val padding = Rect(1, 1, 1, 1)

        // Act
        testSubject.drawable.getPadding(padding)

        // Assert
        assertThat(padding).isEqualTo(Rect())
    }

    @Test
    fun `the background should have rounded corners`() {
        // Arrange
        val testSubject = MessageListItemBackground(context.resources, Color.RED)

        // Act
        val bitmap = draw(testSubject.drawable)

        // Assert: the corner of the row is left empty, its middle is painted
        assertThat(bitmap.getPixel(inset, 0)).isEqualTo(CLEAR)
        assertThat(bitmap.getPixel(inset + cornerRadius, 0)).isEqualTo(Color.RED)
    }

    @Test
    fun `a reused background should paint its new color instead of a new drawable`() {
        // Arrange
        val testSubject = MessageListItemBackground(context.resources, Color.RED)
        val drawable = testSubject.drawable

        // Act
        testSubject.setColor(Color.BLUE)

        // Assert
        assertThat(testSubject.drawable).isSameInstanceAs(drawable)
        assertThat(draw(drawable).getPixel(WIDTH / 2, HEIGHT / 2)).isEqualTo(Color.BLUE)
    }

    /** [drawable] painted on its own, so that the tests can see where and in which color it lands. */
    private fun draw(drawable: Drawable): Bitmap {
        drawable.setBounds(0, 0, WIDTH, HEIGHT)

        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        drawable.draw(Canvas(bitmap))

        return bitmap
    }

    /** The pixel range this bitmap holds something in. */
    private fun Bitmap.paintedBounds(): Rect {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (getPixel(x, y) != CLEAR) {
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
