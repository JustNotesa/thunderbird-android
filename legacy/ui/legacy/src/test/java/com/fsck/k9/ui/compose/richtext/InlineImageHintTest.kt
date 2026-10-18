package com.fsck.k9.ui.compose.richtext

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Looper
import android.os.SystemClock
import android.text.Spanned
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import java.time.Duration
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test
import org.robolectric.Shadows.shadowOf

private const val FIELD_WIDTH = 400
private const val FIELD_HEIGHT = 200
private const val IMAGE_WIDTH = 400
private const val IMAGE_HEIGHT = 200
private const val ON_IMAGE_X = 100f
private const val ON_IMAGE_Y = 100f

/**
 * The hint that tells the user the width of an inline image can be set by swiping sideways over it: it shows up when
 * the user taps an image, and goes away again by itself.
 */
class InlineImageHintTest : RobolectricTest() {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val editText = EditText(context).apply {
        layoutParams = ViewGroup.LayoutParams(FIELD_WIDTH, FIELD_HEIGHT)
    }
    private val pictures = mutableMapOf<String, Drawable>()
    private val images = InlineImages(
        store = InlineImageStore { contentId -> pictures[contentId] },
        limits = InlineImageLimits(maxWidth = { FIELD_WIDTH }, maxHeight = FIELD_HEIGHT),
    )
    private val downTime = SystemClock.uptimeMillis()
    private val testSubject = InlineImageResizer(
        editText = editText,
        onBeforeChange = {},
        onChange = {},
        hints = InlineImageHintAnimator(editText),
    )

    @Test
    fun `a tap on an image should show the hint`() {
        // Arrange
        showImage()

        // Act
        tap()

        // Assert
        assertThat(imageSpan().hintProgress).isNotNull()
    }

    @Test
    fun `the hint should be gone when it has played`() {
        // Arrange
        showImage()
        tap()

        // Act
        idle(HINT_GONE_AFTER)

        // Assert
        assertThat(imageSpan().hintProgress).isNull()
    }

    @Test
    fun `a hint should have a finger that drags the arrow while it is there`() {
        // Arrange
        showImage()
        tap()

        // Act
        val finger = imageSpan().hintFinger

        // Assert
        assertThat(finger).isNotNull()

        // Act: when the hint has played, there is nothing left to drag
        idle(HINT_GONE_AFTER)

        // Assert
        assertThat(imageSpan().hintFinger).isNull()
    }

    @Test
    fun `a tap on the text beside an image should not show the hint`() {
        // Arrange
        showText("Hello Jane")

        // Act
        on(editText, MotionEvent.ACTION_DOWN, 10f, 10f, downTime)
        on(editText, MotionEvent.ACTION_UP, 10f, 10f, downTime)

        // Assert
        assertThat(imageSpanOrNull()?.hintProgress).isNull()
    }

    @Test
    fun `a swipe over an image should not show the hint`() {
        // Arrange
        showImage()

        // Act
        on(editText, MotionEvent.ACTION_DOWN, ON_IMAGE_X, ON_IMAGE_Y, downTime)
        on(editText, MotionEvent.ACTION_MOVE, ON_IMAGE_X - 80f, ON_IMAGE_Y, downTime + 50)
        on(editText, MotionEvent.ACTION_UP, ON_IMAGE_X - 80f, ON_IMAGE_Y, downTime + 60)

        // Assert
        assertThat(imageSpan().hintProgress).isNull()
    }

    @Test
    fun `a scroll over an image should not show the hint`() {
        // Arrange
        showImage()

        // Act: the finger goes up, which scrolls the text field instead of resizing the image
        on(editText, MotionEvent.ACTION_DOWN, ON_IMAGE_X, ON_IMAGE_Y, downTime)
        on(editText, MotionEvent.ACTION_MOVE, ON_IMAGE_X, ON_IMAGE_Y - 80f, downTime + 50)
        on(editText, MotionEvent.ACTION_UP, ON_IMAGE_X, ON_IMAGE_Y - 80f, downTime + 60)

        // Assert
        assertThat(imageSpan().hintProgress).isNull()
    }

    @Test
    fun `a swipe over an image should take the hint away`() {
        // Arrange
        showImage()
        tap()

        // Act
        on(editText, MotionEvent.ACTION_DOWN, ON_IMAGE_X, ON_IMAGE_Y, downTime)
        on(editText, MotionEvent.ACTION_MOVE, ON_IMAGE_X - 80f, ON_IMAGE_Y, downTime + 50)

        // Assert
        assertThat(imageSpan().hintProgress).isNull()
    }

    @Test
    fun `a touch that lasted too long should not show the hint`() {
        // Arrange
        showImage()

        // Act
        on(editText, MotionEvent.ACTION_DOWN, ON_IMAGE_X, ON_IMAGE_Y, downTime)
        on(editText, MotionEvent.ACTION_UP, ON_IMAGE_X, ON_IMAGE_Y, downTime + TOO_LONG)

        // Assert
        assertThat(imageSpan().hintProgress).isNull()
    }

    @Test
    fun `a tap on an image should stay with the text field`() {
        // Arrange
        showImage()

        // Act
        val handled = on(editText, MotionEvent.ACTION_DOWN, ON_IMAGE_X, ON_IMAGE_Y, downTime)
        val handledOnUp = on(editText, MotionEvent.ACTION_UP, ON_IMAGE_X, ON_IMAGE_Y, downTime)

        // Assert
        assertThat(handled).isFalse()
        assertThat(handledOnUp).isFalse()
    }

    private fun showImage(width: Int = IMAGE_WIDTH, height: Int = IMAGE_HEIGHT) {
        pictures["a"] = picture(width, height)
        showText(IMAGE_CHARACTER.toString())
        editText.text.setSpan(images.spanFor("a"), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun showText(text: String) {
        editText.setText(text)
        editText.measure(
            View.MeasureSpec.makeMeasureSpec(FIELD_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(FIELD_HEIGHT, View.MeasureSpec.EXACTLY),
        )
        editText.layout(0, 0, FIELD_WIDTH, FIELD_HEIGHT)
    }

    private fun tap() {
        on(editText, MotionEvent.ACTION_DOWN, ON_IMAGE_X, ON_IMAGE_Y, downTime)
        on(editText, MotionEvent.ACTION_UP, ON_IMAGE_X, ON_IMAGE_Y, downTime)
    }

    private fun on(view: View, action: Int, x: Float, y: Float, time: Long): Boolean {
        val event = MotionEvent.obtain(downTime, time, action, x, y, 0)

        return try {
            testSubject.onTouch(view, event)
        } finally {
            event.recycle()
        }
    }

    private fun idle(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }

    private fun imageSpan(): InlineImageSpan {
        return editText.text.getSpans(0, editText.text.length, InlineImageSpan::class.java).single()
    }

    private fun imageSpanOrNull(): InlineImageSpan? {
        return editText.text.getSpans(0, editText.text.length, InlineImageSpan::class.java).firstOrNull()
    }

    private fun picture(width: Int, height: Int): Drawable {
        return BitmapDrawable(context.resources, Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
    }

    private companion object {
        // Longer than the hint lasts, whether it is animated or shown as it is.
        const val HINT_GONE_AFTER = 3000L

        // Longer than the resizer accepts a finger to be down and still call it a tap.
        const val TOO_LONG = 1000L
    }
}
