package com.fsck.k9.ui.compose.richtext

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Spanned
import android.util.TypedValue
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test

private const val FIELD_WIDTH = 400
private const val FIELD_HEIGHT = 200
private const val IMAGE_WIDTH = 400
private const val IMAGE_HEIGHT = 200
private const val TEXT_SIZE = 20f
private const val MIN_PERCENT = 70
private const val MAX_PERCENT = 250
private const val FIRST_FINGER_X = 50f
private const val PINCH_START_SPAN = 250f
private const val BASE_SPAN = 500f
private const val LINE_Y = 100f
private const val ACTION_DOWN = MotionEvent.ACTION_DOWN
private const val ACTION_POINTER_DOWN = MotionEvent.ACTION_POINTER_DOWN
private const val ACTION_POINTER_UP = MotionEvent.ACTION_POINTER_UP
private const val ACTION_MOVE = MotionEvent.ACTION_MOVE
private const val ACTION_UP = MotionEvent.ACTION_UP

/**
 * Two fingers over the message text change the size it is shown with, a single finger keeps on doing what it did.
 */
class EditorZoomTest : RobolectricTest() {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val parent = FakeParentView(context)
    private val editText = EditText(context).apply {
        layoutParams = ViewGroup.LayoutParams(FIELD_WIDTH, FIELD_HEIGHT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE)
    }
    private val zoomIndicator = TextView(context).apply { visibility = View.GONE }
    private val pictures = mutableMapOf<String, Drawable>()
    private val images = InlineImages(
        store = InlineImageStore { contentId -> pictures[contentId] },
        limits = InlineImageLimits(maxWidth = { FIELD_WIDTH }, maxHeight = FIELD_HEIGHT),
    )
    private var changesAfter = 0
    private val zoom = EditorZoom(editText, zoomIndicator)
    private val testSubject = EditorTouchListener(
        editText = editText,
        zoom = zoom,
        delegate = InlineImageResizer(
            editText = editText,
            onBeforeChange = {},
            onChange = { changesAfter++ },
        ),
    )
    private val normalTextSize = editText.textSize

    init {
        parent.addView(editText)
    }

    @Test
    fun `spreading two fingers should make the text larger`() {
        // Arrange
        startPinch()

        // Act
        moveFingers(span = BASE_SPAN * 2)

        // Assert
        assertThat(zoom.percent).isEqualTo(200)
        assertThat(editText.textSize).isEqualTo(normalTextSize * 200 / 100f)
    }

    @Test
    fun `pinching two fingers together should make the text smaller`() {
        // Arrange
        startPinch()

        // Act
        moveFingers(span = BASE_SPAN / 2)

        // Assert
        assertThat(zoom.percent).isEqualTo(MIN_PERCENT)
        assertThat(editText.textSize).isEqualTo(normalTextSize * MIN_PERCENT / 100f)
    }

    @Test
    fun `a pinch should not take the text above the largest size it may have`() {
        // Arrange
        startPinch()

        // Act
        moveFingers(span = BASE_SPAN * 5)

        // Assert
        assertThat(zoom.percent).isEqualTo(MAX_PERCENT)
        assertThat(editText.textSize).isEqualTo(normalTextSize * MAX_PERCENT / 100f)
    }

    @Test
    fun `the text should keep the size it had before a pinch was taken back`() {
        // Arrange
        startPinch()
        moveFingers(span = BASE_SPAN * 2)
        liftFingers()

        // Act
        startPinch()
        moveFingers(span = BASE_SPAN / 2)

        // Assert
        assertThat(zoom.percent).isEqualTo(100)
        assertThat(editText.textSize).isEqualTo(normalTextSize)
    }

    @Test
    fun `moving the finger that is left after a pinch should not change the text`() {
        // Arrange
        startPinch()
        moveFingers(span = BASE_SPAN * 2)
        liftOneFinger()

        // Act
        on(ACTION_MOVE, FIRST_FINGER_X - 200f)

        // Assert
        assertThat(zoom.percent).isEqualTo(200)
    }

    @Test
    fun `a pinch should not be a swipe over an image`() {
        // Arrange
        showImage()
        startPinch()

        // Act
        moveFingers(span = BASE_SPAN * 3, firstFingerX = FIRST_FINGER_X - 100f)

        // Assert
        assertThat(widthOfImage()).isNull()
        assertThat(changesAfter).isEqualTo(0)
    }

    @Test
    fun `the view around the text should not scroll the pinch away`() {
        // Arrange
        startPinch()

        // Assert
        assertThat(parent.isInterceptDisallowed).isTrue()

        // Act
        liftFingers()

        // Assert
        assertThat(parent.isInterceptDisallowed).isFalse()
    }

    @Test
    fun `a swipe to the side over an image should still set its width`() {
        // Arrange
        showImage()
        on(ACTION_DOWN, FIRST_FINGER_X)

        // Act
        on(ACTION_MOVE, FIRST_FINGER_X - 80f)

        // Assert
        assertThat(widthOfImage()).isEqualTo(80)
        assertThat(changesAfter).isEqualTo(0)
    }

    @Test
    fun `a tap on an image should still stay with the text field`() {
        // Arrange
        showImage()

        // Act
        val handled = on(ACTION_DOWN, FIRST_FINGER_X)

        // Assert
        assertThat(handled).isFalse()
    }

    @Test
    fun `a single finger should not zoom`() {
        // Arrange
        showText("Hello Jane")

        // Act
        on(ACTION_DOWN, FIRST_FINGER_X)
        on(ACTION_MOVE, FIRST_FINGER_X + 200f)
        on(ACTION_UP, FIRST_FINGER_X + 200f)

        // Assert
        assertThat(zoom.percent).isEqualTo(100)
        assertThat(editText.textSize).isEqualTo(normalTextSize)
        assertThat(zoomIndicator.text.toString()).isEqualTo("")
        assertThat(zoomIndicator.isVisible).isFalse()
    }

    @Test
    fun `the size the composer set should stay the size the zoom is measured from`() {
        // Act
        zoom.setPercent(150)

        // Assert
        assertThat(zoom.percent).isEqualTo(150)
        assertThat(editText.textSize).isEqualTo(normalTextSize * 150 / 100f)
    }

    @Test
    fun `a size outside the range the text may have should be taken into it`() {
        // Act
        zoom.setPercent(MAX_PERCENT + 100)

        // Assert
        assertThat(zoom.percent).isEqualTo(MAX_PERCENT)
        assertThat(editText.textSize).isEqualTo(normalTextSize * MAX_PERCENT / 100f)
    }

    @Test
    fun `the size the user is pinching to should be shown while the fingers are on the text`() {
        // Arrange
        startPinch()

        // Act
        moveFingers(span = BASE_SPAN * 2)

        // Assert
        assertThat(zoomIndicator.text.toString()).isEqualTo("200 %")
        assertThat(zoomIndicator.isVisible).isTrue()
    }

    @Test
    fun `pinching should leave the message as it is`() {
        // Arrange
        showText("Hello Jane")

        // Act
        startPinch()
        moveFingers(span = BASE_SPAN * 2)
        liftFingers()

        // Assert
        val formattedText = RichTextFormatter.formattedText(editText.text)
        assertThat(formattedText.text).isEqualTo("Hello Jane")
        assertThat(formattedText.hasFormatting()).isFalse()
    }

    @Test
    fun `a scale factor should be turned into a whole percent`() {
        // Act
        val percent = zoomPercent(startPercent = 100, scaleFactor = 1.234f)

        // Assert
        assertThat(percent).isEqualTo(123)
    }

    @Test
    fun `a scale factor should be measured from the size the last pinch left`() {
        // Act
        val percent = zoomPercent(startPercent = 50, scaleFactor = 2f)

        // Assert
        assertThat(percent).isEqualTo(100)
    }

    @Test
    fun `a scale factor should not take the text below the smallest size it may have`() {
        // Act
        val percent = zoomPercent(startPercent = 100, scaleFactor = 0.2f)

        // Assert
        assertThat(percent).isEqualTo(MIN_PERCENT)
    }

    @Test
    fun `a scale factor should not take the text above the largest size it may have`() {
        // Act
        val percent = zoomPercent(startPercent = 100, scaleFactor = 4f)

        // Assert
        assertThat(percent).isEqualTo(MAX_PERCENT)
    }

    private fun startPinch() {
        on(ACTION_DOWN, FIRST_FINGER_X)
        on(ACTION_POINTER_DOWN, FIRST_FINGER_X, FIRST_FINGER_X + PINCH_START_SPAN)

        // The first movement of a pinch is the distance the further ones are measured from.
        moveFingers(span = BASE_SPAN)
    }

    private fun moveFingers(span: Float, firstFingerX: Float = FIRST_FINGER_X) {
        on(ACTION_MOVE, firstFingerX, firstFingerX + span)
    }

    private fun liftOneFinger() {
        on(ACTION_POINTER_UP, FIRST_FINGER_X, FIRST_FINGER_X + BASE_SPAN)
    }

    private fun liftFingers() {
        liftOneFinger()
        on(ACTION_UP, FIRST_FINGER_X)
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

    private fun on(action: Int, vararg xs: Float): Boolean {
        val event = motionEvent(action, *xs)

        return try {
            testSubject.onTouch(editText, event)
        } finally {
            event.recycle()
        }
    }

    private fun motionEvent(action: Int, vararg xs: Float): MotionEvent {
        val pointerProperties = Array(xs.size) { index ->
            MotionEvent.PointerProperties().apply {
                id = index
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val pointerCoords = Array(xs.size) { index ->
            MotionEvent.PointerCoords().apply {
                x = xs[index]
                y = LINE_Y
            }
        }

        return MotionEvent.obtain(
            0L,
            0L,
            action,
            xs.size,
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

    private fun widthOfImage(): Int? {
        return editText.text.getSpans(0, editText.text.length, InlineImageSpan::class.java).single().widthPercent
    }

    private fun picture(width: Int, height: Int): Drawable {
        return BitmapDrawable(context.resources, Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
    }

    private class FakeParentView(context: Context) : FrameLayout(context) {
        var isInterceptDisallowed = false
            private set

        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            isInterceptDisallowed = disallowIntercept
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }
}
