package com.fsck.k9.ui.compose.richtext

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.text.Spanned
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import net.thunderbird.core.android.testing.RobolectricTest
import net.thunderbird.feature.mail.message.composer.richtext.InlineImage
import org.junit.Test

private const val FIELD_WIDTH = 400
private const val FIELD_HEIGHT = 200
private const val IMAGE_WIDTH = 400
private const val IMAGE_HEIGHT = 200
private const val ON_IMAGE_X = 100f
private const val ON_IMAGE_Y = 100f

/**
 * Setting the width of an inline image by swiping sideways over it in the message text.
 */
class InlineImageResizerTest : RobolectricTest() {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val editText = EditText(context).apply {
        layoutParams = ViewGroup.LayoutParams(FIELD_WIDTH, FIELD_HEIGHT)
    }
    private val pictures = mutableMapOf<String, Drawable>()
    private val images = InlineImages(
        store = InlineImageStore { contentId -> pictures[contentId] },
        limits = InlineImageLimits(maxWidth = { FIELD_WIDTH }, maxHeight = FIELD_HEIGHT),
    )
    private var changesBefore = 0
    private var changesAfter = 0
    private val testSubject = InlineImageResizer(
        editText = editText,
        onBeforeChange = { changesBefore++ },
        onChange = { changesAfter++ },
    )

    @Test
    fun `a swipe to the side over an image should set its width`() {
        // Arrange
        showImage()
        on(editText, MotionEvent.ACTION_DOWN, ON_IMAGE_X, ON_IMAGE_Y)

        // Act
        val handled = on(editText, MotionEvent.ACTION_MOVE, ON_IMAGE_X - 80f, ON_IMAGE_Y)

        // Assert
        assertThat(handled).isTrue()
        assertThat(widthOfImage()).isEqualTo(80)
    }

    @Test
    fun `a swipe to the side over an image should widen it further`() {
        // Arrange
        showImage(widthPercent = 40)
        on(editText, MotionEvent.ACTION_DOWN, ON_IMAGE_X, ON_IMAGE_Y)

        // Act
        on(editText, MotionEvent.ACTION_MOVE, ON_IMAGE_X + 40f, ON_IMAGE_Y)

        // Assert
        assertThat(widthOfImage()).isEqualTo(50)
    }

    @Test
    fun `a swipe to the side should not take an image out of what it may be shown with`() {
        // Arrange
        showImage()
        on(editText, MotionEvent.ACTION_DOWN, ON_IMAGE_X, ON_IMAGE_Y)

        // Act
        on(editText, MotionEvent.ACTION_MOVE, ON_IMAGE_X + FIELD_WIDTH, ON_IMAGE_Y)
        on(editText, MotionEvent.ACTION_UP, ON_IMAGE_X + FIELD_WIDTH, ON_IMAGE_Y)

        // Assert
        assertThat(widthOfImage()).isEqualTo(100)
    }

    @Test
    fun `a swipe upwards over an image should leave it alone`() {
        // Arrange
        showImage()
        on(editText, MotionEvent.ACTION_DOWN, ON_IMAGE_X, ON_IMAGE_Y)

        // Act
        val handled = on(editText, MotionEvent.ACTION_MOVE, ON_IMAGE_X, ON_IMAGE_Y - 100f)

        // Assert
        assertThat(handled).isFalse()
        assertThat(widthOfImage()).isNull()
    }

    @Test
    fun `a movement too small to be a swipe should leave the image alone`() {
        // Arrange
        showImage()
        on(editText, MotionEvent.ACTION_DOWN, ON_IMAGE_X, ON_IMAGE_Y)

        // Act
        val handled = on(editText, MotionEvent.ACTION_MOVE, ON_IMAGE_X + 2f, ON_IMAGE_Y)

        // Assert
        assertThat(handled).isFalse()
        assertThat(widthOfImage()).isNull()
    }

    @Test
    fun `a swipe beside an image should leave it alone`() {
        // Arrange
        showImage(width = FIELD_WIDTH / 2)
        on(editText, MotionEvent.ACTION_DOWN, x = 350f, y = ON_IMAGE_Y)

        // Act
        val handled = on(editText, MotionEvent.ACTION_MOVE, x = 250f, y = ON_IMAGE_Y)

        // Assert
        assertThat(handled).isFalse()
        assertThat(widthOfImage()).isNull()
    }

    @Test
    fun `a swipe over text without an image should leave the text alone`() {
        // Arrange
        showText("Hello Jane")
        on(editText, MotionEvent.ACTION_DOWN, x = 10f, y = 10f)

        // Act
        val handled = on(editText, MotionEvent.ACTION_MOVE, x = 100f, y = 10f)

        // Assert
        assertThat(handled).isFalse()
        assertThat(editText.text.toString()).isEqualTo("Hello Jane")
    }

    @Test
    fun `a gesture should tell the format change once before it and once after it`() {
        // Arrange
        showImage()
        on(editText, MotionEvent.ACTION_DOWN, ON_IMAGE_X, ON_IMAGE_Y)

        // Act
        on(editText, MotionEvent.ACTION_MOVE, ON_IMAGE_X - 20f, ON_IMAGE_Y)
        on(editText, MotionEvent.ACTION_MOVE, ON_IMAGE_X - 40f, ON_IMAGE_Y)
        on(editText, MotionEvent.ACTION_UP, ON_IMAGE_X - 40f, ON_IMAGE_Y)

        // Assert
        assertThat(changesBefore).isEqualTo(1)
        assertThat(changesAfter).isEqualTo(1)
    }

    @Test
    fun `the gesture should be over when the finger is lifted`() {
        // Arrange
        showImage()
        on(editText, MotionEvent.ACTION_DOWN, ON_IMAGE_X, ON_IMAGE_Y)
        on(editText, MotionEvent.ACTION_MOVE, ON_IMAGE_X - 80f, ON_IMAGE_Y)

        // Act
        val handled = on(editText, MotionEvent.ACTION_UP, ON_IMAGE_X - 80f, ON_IMAGE_Y)

        // Assert
        assertThat(handled).isTrue()
        assertThat(imageSpan().isResizing).isFalse()
    }

    @Test
    fun `a tap on an image should stay with the text field`() {
        // Arrange
        showImage()

        // Act
        val handled = on(editText, MotionEvent.ACTION_DOWN, ON_IMAGE_X, ON_IMAGE_Y)

        // Assert
        assertThat(handled).isFalse()
    }

    @Test
    fun `the width the user set should be the width the message is saved with`() {
        // Arrange
        showImage()
        on(editText, MotionEvent.ACTION_DOWN, ON_IMAGE_X, ON_IMAGE_Y)
        on(editText, MotionEvent.ACTION_MOVE, ON_IMAGE_X - 80f, ON_IMAGE_Y)
        on(editText, MotionEvent.ACTION_UP, ON_IMAGE_X - 80f, ON_IMAGE_Y)

        // Act
        val images = RichTextFormatter.formattedText(editText.text).images

        // Assert
        assertThat(images).containsExactly(InlineImage(0, "a", 80))
    }

    private fun showImage(width: Int = IMAGE_WIDTH, height: Int = IMAGE_HEIGHT, widthPercent: Int? = null) {
        pictures["a"] = picture(width, height)
        showText(IMAGE_CHARACTER.toString())
        editText.text.setSpan(images.spanFor("a", widthPercent), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun showText(text: String) {
        editText.setText(text)
        editText.measure(
            View.MeasureSpec.makeMeasureSpec(FIELD_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(FIELD_HEIGHT, View.MeasureSpec.EXACTLY),
        )
        editText.layout(0, 0, FIELD_WIDTH, FIELD_HEIGHT)
    }

    private fun on(view: View, action: Int, x: Float, y: Float): Boolean {
        val event = MotionEvent.obtain(0, SystemClock.uptimeMillis(), action, x, y, 0)

        return try {
            testSubject.onTouch(view, event)
        } finally {
            event.recycle()
        }
    }

    private fun imageSpan(): InlineImageSpan {
        return editText.text.getSpans(0, editText.text.length, InlineImageSpan::class.java).single()
    }

    private fun widthOfImage(): Int? {
        return imageSpan().widthPercent
    }

    private fun picture(width: Int, height: Int): Drawable {
        return BitmapDrawable(context.resources, Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
    }
}
