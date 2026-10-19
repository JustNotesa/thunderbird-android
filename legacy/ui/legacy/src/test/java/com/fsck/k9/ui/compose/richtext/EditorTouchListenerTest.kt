package com.fsck.k9.ui.compose.richtext

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Looper
import android.text.Spanned
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import java.time.Duration

private const val FIELD_WIDTH = 400
private const val FIELD_HEIGHT = 200
private const val IMAGE_WIDTH = 400
private const val IMAGE_HEIGHT = 200
private const val TEXT = "Hello Jane"
private const val CURSOR_BEFORE = 5
private const val TAP_X = 300f
private const val FIRST_FINGER_X = 50f
private const val SWIPE_X = FIRST_FINGER_X - 80f
private const val PINCH_START_SPAN = 250f
private const val BASE_SPAN = 500f
private const val LINE_Y = 100f
private const val ACTION_DOWN = MotionEvent.ACTION_DOWN
private const val ACTION_POINTER_DOWN = MotionEvent.ACTION_POINTER_DOWN
private const val ACTION_POINTER_UP = MotionEvent.ACTION_POINTER_UP
private const val ACTION_MOVE = MotionEvent.ACTION_MOVE
private const val ACTION_UP = MotionEvent.ACTION_UP
private const val ACTION_CANCEL = MotionEvent.ACTION_CANCEL

/**
 * The finger that goes down over the message text is held back from the field for as long as a tap may take: the second
 * finger of a pinch is then still able to arrive before the field places a cursor and raises the keyboard.
 */
class EditorTouchListenerTest : RobolectricTest() {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val editText = RecordingEditText(context).apply {
        layoutParams = ViewGroup.LayoutParams(FIELD_WIDTH, FIELD_HEIGHT)
    }
    private val zoomLabel = TextView(context)
    private val zoomIndicator = FrameLayout(context).apply { addView(zoomLabel) }
    private val zoom = EditorZoom(zoomIndicator, zoomLabel)
    private val pictures = mutableMapOf<String, Drawable>()
    private val images = InlineImages(
        store = InlineImageStore { contentId -> pictures[contentId] },
        limits = InlineImageLimits(maxWidth = { FIELD_WIDTH }, maxHeight = FIELD_HEIGHT),
    )
    private var changesAfter = 0
    private val testSubject = EditorTouchListener(
        editText = editText,
        zoom = zoom,
        delegate = InlineImageResizer(
            editText = editText,
            onBeforeChange = {},
            onChange = { changesAfter++ },
        ),
    )

    init {
        // The composer lets the field have this listener, so the field only sees what the listener gives it.
        editText.setOnTouchListener(testSubject)
    }

    @Test
    fun `a pinch should not reach the text field`() {
        // Arrange
        showText(TEXT)
        editText.setSelection(CURSOR_BEFORE)
        startPinch()
        moveFingers(span = BASE_SPAN * 2)

        // Act
        liftFingers()

        // Assert
        assertThat(editText.hasFocus()).isFalse()
        assertThat(editText.selectionStart).isEqualTo(CURSOR_BEFORE)
        assertThat(editText.touched).isEmpty()
    }

    @Test
    fun `a tap should focus the text field and place the cursor`() {
        // Arrange
        showText(TEXT)

        // Act
        on(ACTION_DOWN, TAP_X)
        on(ACTION_UP, TAP_X)

        // Assert
        assertThat(editText.hasFocus()).isTrue()
        assertThat(editText.selectionStart).isEqualTo(TEXT.length)
        assertThat(editText.touched).containsExactly(ACTION_DOWN, ACTION_UP)
    }

    @Test
    fun `a finger that waits out the time a tap may take should still reach the text field`() {
        // Arrange
        on(ACTION_DOWN, FIRST_FINGER_X)
        val beforeTheTapTimeout = editText.touched.toList()

        // Act
        waitOutTapTimeout()

        // Assert
        assertThat(beforeTheTapTimeout).isEmpty()
        assertThat(editText.touched).containsExactly(ACTION_DOWN)
    }

    @Test
    fun `a swipe over an image should resize it after the finger has reached the text field`() {
        // Arrange
        showImage()
        on(ACTION_DOWN, FIRST_FINGER_X)

        // Act
        on(ACTION_MOVE, SWIPE_X)

        // Assert: the field was given the finger before the delegate took the gesture over, so its cancel belongs.
        assertThat(editText.touched).containsExactly(ACTION_DOWN, ACTION_CANCEL)
        assertThat(widthOfImage()).isEqualTo(80)
        assertThat(changesAfter).isEqualTo(0)
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

    private fun liftFingers() {
        on(ACTION_POINTER_UP, FIRST_FINGER_X, FIRST_FINGER_X + BASE_SPAN * 2)
        on(ACTION_UP, FIRST_FINGER_X)
    }

    private fun waitOutTapTimeout() {
        val tapTimeout = ViewConfiguration.getTapTimeout().toLong()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(tapTimeout))
    }

    private fun showImage() {
        pictures["a"] = picture(IMAGE_WIDTH, IMAGE_HEIGHT)
        showText(IMAGE_CHARACTER.toString())
        editText.text.setSpan(images.spanFor("a", widthPercent = null), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun showText(text: String) {
        editText.setText(text)
        editText.measure(
            View.MeasureSpec.makeMeasureSpec(FIELD_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(FIELD_HEIGHT, View.MeasureSpec.EXACTLY),
        )
        editText.layout(0, 0, FIELD_WIDTH, FIELD_HEIGHT)
    }

    private fun on(action: Int, vararg xs: Float) {
        val event = motionEvent(action, *xs)

        try {
            editText.dispatchTouchEvent(event)
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

    /**
     * Keeps what it is given, so that a test can see whether the field has seen a gesture at all.
     */
    private class RecordingEditText(context: Context) : EditText(context) {
        val touched = mutableListOf<Int>()

        override fun onTouchEvent(event: MotionEvent): Boolean {
            touched += event.actionMasked

            return super.onTouchEvent(event)
        }
    }
}
