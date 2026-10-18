package com.fsck.k9.ui.compose.richtext

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test
import org.robolectric.annotation.GraphicsMode

private const val IMAGE_LEFT = 100
private const val IMAGE_WIDTH = 400
private const val IMAGE_HEIGHT = 200
private const val CANVAS_WIDTH = 600
private const val CANVAS_HEIGHT = 400
private const val CLEAR = 0

/**
 * The hint an inline image draws over itself: a translucent pill with an arrow that reaches out and back. The picture
 * has no colour of its own, so everything the canvas holds afterwards is the hint.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InlineImageHintDrawingTest : RobolectricTest() {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val pictures = mutableMapOf<String, Drawable>()
    private val images = InlineImages(
        store = InlineImageStore { contentId -> pictures[contentId] },
        limits = InlineImageLimits(maxWidth = { CANVAS_WIDTH }, maxHeight = CANVAS_HEIGHT),
    )
    private val text = IMAGE_CHARACTER.toString()

    @Test
    fun `an image should draw the hint on itself and nowhere else`() {
        // Arrange
        val bitmap = drawToBitmap(hintProgress = 0.5f)

        // Assert
        val hint = bitmap.boundsOfPaintedPixels()
        assertThat(hint.left).isGreaterThan(IMAGE_LEFT)
        assertThat(hint.right).isLessThan(IMAGE_LEFT + IMAGE_WIDTH)
        assertThat(hint.top).isGreaterThan(0)
        assertThat(hint.bottom).isLessThan(CANVAS_HEIGHT)
    }

    @Test
    fun `an image should draw no hint while it is not shown`() {
        // Arrange
        val bitmap = drawToBitmap(hintProgress = null)

        // Assert
        assertThat(bitmap.boundsOfPaintedPixels().width()).isEqualTo(0)
    }

    @Test
    fun `a hint that has just begun should draw less than one that is fully there`() {
        // Arrange
        val faded = drawToBitmap(hintProgress = 0.01f)
        val full = drawToBitmap(hintProgress = 0.5f)

        // Act
        val fadedPixels = faded.countOpaquePixels()
        val fullPixels = full.countOpaquePixels()

        // Assert
        assertThat(fadedPixels).isLessThan(fullPixels)
    }

    private fun drawToBitmap(hintProgress: Float?): Bitmap {
        pictures["a"] = picture(IMAGE_WIDTH, IMAGE_HEIGHT)
        val span = images.spanFor("a")
        span.getSize(Paint(), text, 0, 1, null)
        span.hintProgress = hintProgress

        val bitmap = Bitmap.createBitmap(CANVAS_WIDTH, CANVAS_HEIGHT, Bitmap.Config.ARGB_8888)
        span.draw(Canvas(bitmap), text, 0, 1, IMAGE_LEFT.toFloat(), 0, 0, CANVAS_HEIGHT, Paint())

        return bitmap
    }

    private fun Bitmap.pixels(): List<Int> {
        return (0 until height).flatMap { y -> (0 until width).map { x -> getPixel(x, y) } }
    }

    private fun Bitmap.countOpaquePixels(): Int {
        return pixels().count { pixel -> Color.alpha(pixel) >= OPAQUE }
    }

    private fun Bitmap.boundsOfPaintedPixels(): PaintedBounds {
        val painted = pixels().withIndex().filter { (_, pixel) -> pixel != CLEAR }

        return PaintedBounds(
            left = painted.minOfOrNull { (index) -> index % width } ?: NO_PIXEL,
            right = painted.maxOfOrNull { (index) -> index % width } ?: NO_PIXEL,
            top = painted.minOfOrNull { (index) -> index / width } ?: NO_PIXEL,
            bottom = painted.maxOfOrNull { (index) -> index / width } ?: NO_PIXEL,
        )
    }

    private fun picture(width: Int, height: Int): Drawable {
        return BitmapDrawable(context.resources, Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
    }

    private data class PaintedBounds(val left: Int, val right: Int, val top: Int, val bottom: Int) {
        fun width(): Int = right - left
    }

    private companion object {
        const val OPAQUE = 128
        const val NO_PIXEL = 0
    }
}
