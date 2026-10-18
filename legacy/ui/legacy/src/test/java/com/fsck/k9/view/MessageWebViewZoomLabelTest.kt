package com.fsck.k9.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import com.fsck.k9.ui.R
import com.google.android.material.color.MaterialColors
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.After
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

private const val VIEW_WIDTH = 1000
private const val VIEW_HEIGHT = 2000
private const val LABEL_HEIGHT_DP = 32f
private const val LABEL_VISIBLE_TIME_MS = 1000L
private const val LABEL_FADE_OUT_TIME_MS = 200L
private const val CENTRE_TOLERANCE = 2
private const val EDGE_TOLERANCE = 2
private val MESSAGE_SURFACE = com.google.android.material.R.attr.colorSurfaceContainerLowest
private val LABEL_SURFACE = com.google.android.material.R.attr.colorSurfaceContainerHighest

/**
 * The message is drawn on the same surface as the header around it, and the zoom label is a pill of that design
 * system rather than a dark rectangle in the corner.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageWebViewZoomLabelTest : RobolectricTest() {
    private val context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(),
        R.style.Theme_Legacy_Test,
    )
    private val density = context.resources.displayMetrics.density

    @After
    fun tearDown() {
        MessageTextZoom.update(DEFAULT_ZOOM_FACTOR)
    }

    @Test
    fun `the message should be drawn on the surface the header sits on`() {
        // Arrange
        val testSubject = message()

        // Assert
        val backgroundColor = shadowOf(testSubject).backgroundColor
        assertThat(backgroundColor).isEqualTo(themeColor(MESSAGE_SURFACE))
        assertThat(backgroundColor).isNotEqualTo(0)
    }

    @Test
    fun `the zoom label should be a pill of the design system and not a dark rectangle`() {
        // Arrange
        val testSubject = pinnedMessage()

        // Act
        val bitmap = drawn(testSubject)

        // Assert
        assertThat(pixels(bitmap)).contains(themeColor(LABEL_SURFACE))
    }

    @Test
    fun `the zoom label should be in the middle at the top of the message`() {
        // Arrange
        val testSubject = pinnedMessage()

        // Act
        val bounds = pillBounds(drawn(testSubject))

        // Assert
        val expectedCentre = testSubject.width / 2
        assertThat(bounds.centerX()).isBetween(expectedCentre - CENTRE_TOLERANCE, expectedCentre + CENTRE_TOLERANCE)
        assertThat(bounds.top).isLessThan(testSubject.height / 4)
    }

    @Test
    fun `the zoom label should be as tall as a pill`() {
        // Arrange
        val testSubject = pinnedMessage()

        // Act
        val bounds = pillBounds(drawn(testSubject))

        // Assert
        val expectedHeight = (LABEL_HEIGHT_DP * density).toInt()
        assertThat(bounds.height()).isBetween(expectedHeight - EDGE_TOLERANCE, expectedHeight + EDGE_TOLERANCE)
    }

    @Test
    fun `the zoom label should be gone about a second after the pinch`() {
        // Arrange
        val testSubject = pinnedMessage()
        idleFor(LABEL_VISIBLE_TIME_MS / 2)

        // Act
        assertThat(pixels(drawn(testSubject))).contains(themeColor(LABEL_SURFACE))
        idleFor(LABEL_VISIBLE_TIME_MS / 2 + LABEL_FADE_OUT_TIME_MS * 2)

        // Assert
        assertThat(pixels(drawn(testSubject))).doesNotContain(themeColor(LABEL_SURFACE))
    }

    /** A message with the size it is drawn with. */
    private fun message(): MessageWebView {
        val testSubject = MessageWebView(context)
        configure(testSubject)
        // Robolectric suppresses ViewGroup.layout(), so the frame is set through the setters of View.
        testSubject.setRight(VIEW_WIDTH)
        testSubject.setBottom(VIEW_HEIGHT)

        return testSubject
    }

    /** [message] after a pinch on it, so it shows the zoom label. */
    private fun pinnedMessage(): MessageWebView {
        val testSubject = message()
        pinch(testSubject, distances = listOf(300f, 600f, 900f))

        return testSubject
    }

    private fun drawn(webView: MessageWebView): Bitmap {
        val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
        webView.draw(Canvas(bitmap))

        return bitmap
    }

    /** The two fingers are [distance] apart after another step, starting with a touch down. */
    private fun pinch(webView: MessageWebView, distances: List<Float>) {
        val firstDistance = distances.first()
        webView.dispatchTouchEvent(pinchMotionEvent(MotionEvent.ACTION_DOWN, firstX = 0f, secondX = firstDistance))
        webView.dispatchTouchEvent(
            pinchMotionEvent(MotionEvent.ACTION_POINTER_DOWN, firstX = 0f, secondX = firstDistance),
        )

        distances.drop(1).forEach { distance ->
            webView.dispatchTouchEvent(pinchMotionEvent(MotionEvent.ACTION_MOVE, firstX = 0f, secondX = distance))
        }
    }

    private fun pinchMotionEvent(
        action: Int,
        firstX: Float,
        secondX: Float,
    ): MotionEvent {
        val pointerProperties = Array(2) { index ->
            MotionEvent.PointerProperties().apply {
                id = index
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val pointerCoords = Array(2) { index ->
            MotionEvent.PointerCoords().apply {
                x = if (index == 0) firstX else secondX
            }
        }

        return MotionEvent.obtain(
            0L,
            0L,
            action,
            2,
            pointerProperties,
            pointerCoords,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0,
        )
    }

    private fun idleFor(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }

    private fun themeColor(attr: Int): Int {
        return MaterialColors.getColor(View(context), attr)
    }

    private fun pixels(bitmap: Bitmap): List<Int> {
        return (0 until bitmap.height).flatMap { y -> (0 until bitmap.width).map { x -> bitmap.getPixel(x, y) } }
    }

    /** The pixels of the pill background; the rounded corners and the text on it are of other colours. */
    private fun pillBounds(bitmap: Bitmap): Bounds {
        val pillColor = themeColor(LABEL_SURFACE)
        val painted = pixels(bitmap).withIndex().filter { (_, pixel) -> pixel == pillColor }

        return Bounds(
            left = painted.minOfOrNull { it.index % bitmap.width } ?: 0,
            right = painted.maxOfOrNull { it.index % bitmap.width } ?: 0,
            top = painted.minOfOrNull { it.index / bitmap.width } ?: 0,
            bottom = painted.maxOfOrNull { it.index / bitmap.width } ?: 0,
        )
    }

    private fun configure(webView: MessageWebView) {
        try {
            webView.configure(
                WebViewConfig(
                    useDarkMode = false,
                    autoFitWidth = false,
                    textZoom = 100,
                ),
            )
        } catch (_: UnsupportedOperationException) {
            // Robolectric's WebView provider can reject the AndroidX dark-mode WebSettings API.
        }
    }

    private data class Bounds(val left: Int, val right: Int, val top: Int, val bottom: Int) {
        fun centerX(): Int = (left + right) / 2

        fun height(): Int = bottom - top
    }

    private companion object {
        const val DEFAULT_ZOOM_FACTOR = 1f
    }
}
