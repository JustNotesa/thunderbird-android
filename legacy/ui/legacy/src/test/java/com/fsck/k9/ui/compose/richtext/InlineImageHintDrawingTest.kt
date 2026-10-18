package com.fsck.k9.ui.compose.richtext

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test
import org.robolectric.annotation.GraphicsMode
import app.k9mail.core.ui.legacy.designsystem.R as DesignSystemR

private const val IMAGE_LEFT = 100
private const val IMAGE_WIDTH = 400
private const val IMAGE_HEIGHT = 200
private const val CANVAS_WIDTH = 600
private const val CANVAS_HEIGHT = 400
private const val CLEAR = 0

// The pill is as wide as the image allows (160 of its 400 pixels) and 40 high, and leaves 12 pixels on every side for
// the arrow to move in.
private const val PILL_WIDTH = 160f
private const val PILL_PADDING = 12f
private const val ROOM_IN_PILL = PILL_WIDTH - PILL_PADDING * 2f

// Where in its cycle the arrow is at its longest and where it is at its shortest, and what it is worth there.
private const val WIDEST = HINT_FULL_WIDTH_PROGRESS
private const val NARROWEST = 0.5f
private const val SHORTEST_ARROW = 0.35f

// What the hint looks like while it is fading in, and how far a measurement of it may be off.
private const val FADING_IN = 0.2f
private const val WHITE = 128
private const val TOLERANCE = 5f

/**
 * The hint an inline image draws over itself: a translucent pill with an arrow that stretches and shrinks, and a finger
 * that drags the right head of the arrow. The picture has no colour of its own, so everything the canvas holds
 * afterwards is the hint. The finger is the only thing below the pill, the white arrow the only white thing on it.
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
        val bitmap = drawToBitmap(hintProgress = NARROWEST, withFinger = true)

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
        assertThat(bitmap.boundsOfPaintedPixels().width()).isEqualTo(0f)
    }

    @Test
    fun `a hint that is fading in should draw less than one that is fully there`() {
        // Arrange
        val fading = drawToBitmap(hintProgress = NARROWEST, opacity = FADING_IN)
        val full = drawToBitmap(hintProgress = NARROWEST)

        // Act
        val faded = fading.alphaOfPixels()
        val there = full.alphaOfPixels()

        // Assert
        assertThat(faded).isLessThan(there)
    }

    @Test
    fun `the arrow should stretch between the widths the pill has room for`() {
        // Arrange
        val wide = hintAt(WIDEST)
        val narrow = hintAt(NARROWEST)

        // Act
        val wideLength = wide.arrow.width()
        val narrowLength = narrow.arrow.width()

        // Assert
        assertThat(wideLength).isCloseTo(ROOM_IN_PILL, TOLERANCE)
        assertThat(narrowLength).isCloseTo(ROOM_IN_PILL * SHORTEST_ARROW, TOLERANCE)
        assertThat(wide.arrow.left).isGreaterThan(wide.pill.left)
        assertThat(wide.arrow.right).isLessThan(wide.pill.right)
    }

    @Test
    fun `the finger should stand at the right head of the arrow and follow it`() {
        // Arrange
        val wide = hintAt(WIDEST)
        val narrow = hintAt(NARROWEST)

        // Assert: the finger hangs at the right head of the arrow, and stands as far beyond it as it did when the arrow
        // was short.
        assertThat(wide.finger.right).isGreaterThan(wide.arrow.right)
        assertThat((wide.finger.right - wide.arrow.right).toFloat())
            .isCloseTo((narrow.finger.right - narrow.arrow.right).toFloat(), TOLERANCE)
        assertThat(wide.finger.right).isGreaterThan(narrow.finger.right)
    }

    /** The hint at [progress], measured where each of its parts is: the pill, the arrow on it, the finger under it. */
    private fun hintAt(progress: Float): Hint {
        val pill = drawToBitmap(hintProgress = progress).boundsOfPaintedPixels()
        val hint = drawToBitmap(hintProgress = progress, withFinger = true)
        val middle = (pill.top + pill.bottom) / 2

        return Hint(
            pill = pill,
            arrow = hint.boundsOf(middle..middle) { pixel -> Color.red(pixel) > WHITE },
            finger = hint.boundsOf(pill.bottom + 1 until CANVAS_HEIGHT) { pixel -> pixel != CLEAR },
        )
    }

    private fun drawToBitmap(hintProgress: Float?, withFinger: Boolean = false, opacity: Float = 1f): Bitmap {
        pictures["a"] = picture(IMAGE_WIDTH, IMAGE_HEIGHT)
        val span = images.spanFor("a")
        span.getSize(Paint(), text, 0, 1, null)
        span.hintProgress = hintProgress
        span.hintOpacity = opacity
        span.hintFinger = if (withFinger) finger() else null

        val bitmap = Bitmap.createBitmap(CANVAS_WIDTH, CANVAS_HEIGHT, Bitmap.Config.ARGB_8888)
        span.draw(Canvas(bitmap), text, 0, 1, IMAGE_LEFT.toFloat(), 0, 0, CANVAS_HEIGHT, Paint())

        return bitmap
    }

    private fun finger(): Drawable? {
        return AppCompatResources.getDrawable(context, DesignSystemR.drawable.ic_touch_app)
    }

    /** Where the pixels in these rows are [isPainted], which is what the drawing has left on them. */
    private fun Bitmap.boundsOf(rows: IntRange, isPainted: (Int) -> Boolean): PaintedBounds {
        val painted = rows.flatMap { y ->
            (0 until width).mapNotNull { x -> x.takeIf { isPainted(getPixel(x, y)) }?.let { x to y } }
        }

        return PaintedBounds(
            left = painted.minOfOrNull { (x, _) -> x } ?: NO_PIXEL,
            right = painted.maxOfOrNull { (x, _) -> x } ?: NO_PIXEL,
            top = painted.minOfOrNull { (_, y) -> y } ?: NO_PIXEL,
            bottom = painted.maxOfOrNull { (_, y) -> y } ?: NO_PIXEL,
        )
    }

    private fun Bitmap.boundsOfPaintedPixels(): PaintedBounds {
        return boundsOf(0 until height) { pixel -> pixel != CLEAR }
    }

    /** How much of the hint is on the picture, counted in the transparency of every one of its pixels. */
    private fun Bitmap.alphaOfPixels(): Int {
        return (0 until height).sumOf { y -> (0 until width).sumOf { x -> Color.alpha(getPixel(x, y)) } }
    }

    private fun picture(width: Int, height: Int): Drawable {
        return BitmapDrawable(context.resources, Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
    }

    private data class Hint(val pill: PaintedBounds, val arrow: PaintedBounds, val finger: PaintedBounds)

    private data class PaintedBounds(val left: Int, val right: Int, val top: Int, val bottom: Int) {
        fun width(): Float = (right - left).toFloat()
    }

    private companion object {
        const val NO_PIXEL = 0
    }
}
