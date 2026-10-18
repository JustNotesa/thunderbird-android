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
import com.fsck.k9.ui.R
import com.fsck.k9.view.MessageTextZoom
import com.fsck.k9.view.MessageWebView
import net.thunderbird.core.android.testing.RobolectricTest
import net.thunderbird.core.android.webkit.WebViewConfig
import org.junit.After
import org.junit.Test

private const val FIELD_WIDTH = 400
private const val FIELD_HEIGHT = 200
private const val IMAGE_WIDTH = 400
private const val IMAGE_HEIGHT = 200
private const val TEXT_SIZE = 20f
private const val PREVIEW_BASE_TEXT_ZOOM = 120
private const val MIN_PERCENT = 70
private const val MAX_PERCENT = 250
private const val UNCHANGED_PERCENT = 100
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
 * Two fingers over the message area change the size the whole of it is shown with, a single finger keeps on doing what
 * it did. The message itself stays as it is.
 */
class EditorZoomTest : RobolectricTest() {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val parent = FakeParentView(context)
    private val editText = EditText(context).apply {
        layoutParams = ViewGroup.LayoutParams(FIELD_WIDTH, FIELD_HEIGHT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE)
    }
    private val signature = textField()
    private val quote = textField()
    private val subject = textField()
    private val signaturePreview = MessageWebView(context)
    private val quotePreview = MessageWebView(context)
    private val zoomLabel = TextView(context).apply { visibility = View.GONE }
    private val zoomIndicator = FrameLayout(context).apply {
        visibility = View.GONE
        addView(zoomLabel)
    }
    private val pictures = mutableMapOf<String, Drawable>()
    private val images = InlineImages(
        store = InlineImageStore { contentId -> pictures[contentId] },
        limits = InlineImageLimits(maxWidth = { FIELD_WIDTH }, maxHeight = FIELD_HEIGHT),
    )
    private var changesAfter = 0
    private val zoom = EditorZoom(zoomIndicator, zoomLabel)
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
    private val normalSignatureTextSize = signature.textSize
    private val normalQuoteTextSize = quote.textSize
    private val normalSubjectTextSize = subject.textSize

    init {
        parent.addView(editText)

        // The order the composer uses: the message area is registered first, the previews are configured afterwards.
        zoom.addTarget(TextViewZoomTarget(editText))
        zoom.addTarget(TextViewZoomTarget(signature))
        zoom.addTarget(TextViewZoomTarget(quote))
        zoom.addTarget(WebViewZoomTarget(signaturePreview, zoom))
        zoom.addTarget(WebViewZoomTarget(quotePreview, zoom))
        configurePreview(signaturePreview)
        configurePreview(quotePreview)
        listOf(signaturePreview, quotePreview).forEach { it.layout(0, 0, FIELD_WIDTH, FIELD_HEIGHT) }
    }

    @After
    fun tearDown() {
        MessageTextZoom.update(DEFAULT_ZOOM_FACTOR)
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
        assertThat(zoom.percent).isEqualTo(UNCHANGED_PERCENT)
        assertThat(editText.textSize).isEqualTo(normalTextSize)
    }

    @Test
    fun `every part of the message area should scale with the same factor`() {
        // Arrange
        startPinch()

        // Act
        moveFingers(span = BASE_SPAN * 2)

        // Assert
        assertThat(signature.textSize).isEqualTo(normalSignatureTextSize * 2)
        assertThat(quote.textSize).isEqualTo(normalQuoteTextSize * 2)
        assertThat(signaturePreview.settings.textZoom).isEqualTo(PREVIEW_BASE_TEXT_ZOOM * 2)
        assertThat(quotePreview.settings.textZoom).isEqualTo(PREVIEW_BASE_TEXT_ZOOM * 2)
    }

    @Test
    fun `the subject should stay the size it is`() {
        // Arrange
        startPinch()

        // Act
        moveFingers(span = BASE_SPAN * 2)

        // Assert
        assertThat(subject.textSize).isEqualTo(normalSubjectTextSize)
    }

    @Test
    fun `two fingers over a preview should zoom the message area`() {
        // Arrange
        pinch(signaturePreview)

        // Act
        moveFingers(span = BASE_SPAN * 2, view = signaturePreview)

        // Assert
        assertThat(zoom.percent).isEqualTo(200)
        assertThat(editText.textSize).isEqualTo(normalTextSize * 2)
    }

    @Test
    fun `two fingers over a preview should leave the zoom of the message viewer alone`() {
        // Arrange
        pinch(signaturePreview)

        // Act
        moveFingers(span = BASE_SPAN * 2, view = signaturePreview)

        // Assert
        assertThat(MessageTextZoom.factor).isEqualTo(DEFAULT_ZOOM_FACTOR)
    }

    @Test
    fun `a preview should keep the zoom of the message area when it is configured again`() {
        // Arrange
        pinch(signaturePreview)
        moveFingers(span = BASE_SPAN * 2, view = signaturePreview)
        liftFingers(view = signaturePreview)

        // Act
        configurePreview(quotePreview)

        // Assert
        assertThat(quotePreview.settings.textZoom).isEqualTo(PREVIEW_BASE_TEXT_ZOOM * 2)
    }

    @Test
    fun `a pinch that ends close to the size the composer set should snap back to it`() {
        // Arrange
        startPinch()
        moveFingers(span = BASE_SPAN * 2)
        liftFingers()

        // Act: 98 % of the 200 % the last pinch left, which is close enough to mean the size the composer set.
        startPinch()
        moveFingers(span = BASE_SPAN * 0.49f)

        // Assert
        assertThat(zoom.percent).isEqualTo(UNCHANGED_PERCENT)
        assertThat(editText.textSize).isEqualTo(normalTextSize)
        assertThat(signature.textSize).isEqualTo(normalSignatureTextSize)
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
        assertThat(zoom.percent).isEqualTo(UNCHANGED_PERCENT)
        assertThat(editText.textSize).isEqualTo(normalTextSize)
        assertThat(zoomLabel.text.toString()).isEqualTo("")
        assertThat(zoomIndicator.isVisible).isFalse()
    }

    @Test
    fun `the size the composer set should stay the size the zoom is measured from`() {
        // Act
        zoom.setPercent(150)

        // Assert
        assertThat(zoom.percent).isEqualTo(150)
        assertThat(editText.textSize).isEqualTo(normalTextSize * 150 / 100f)
        assertThat(signaturePreview.settings.textZoom).isEqualTo(PREVIEW_BASE_TEXT_ZOOM * 150 / 100)
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
        assertThat(zoomLabel.text.toString()).isEqualTo(indicatorText(percent = 200))
        assertThat(zoomIndicator.isVisible).isTrue()
    }

    @Test
    fun `the size that is shown should say that the message itself stays as it is`() {
        // Assert
        assertThat(indicatorText(percent = 140)).isEqualTo("View 140 % · message unchanged")
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

    @Test
    fun `a size within a few percent of the one the composer set should snap back to it`() {
        assertThat(unchangedPercent(97)).isEqualTo(UNCHANGED_PERCENT)
        assertThat(unchangedPercent(103)).isEqualTo(UNCHANGED_PERCENT)
        assertThat(unchangedPercent(100)).isEqualTo(UNCHANGED_PERCENT)
    }

    @Test
    fun `a size further away from the one the composer set should be kept`() {
        assertThat(unchangedPercent(96)).isEqualTo(96)
        assertThat(unchangedPercent(104)).isEqualTo(104)
        assertThat(unchangedPercent(MIN_PERCENT)).isEqualTo(MIN_PERCENT)
    }

    private fun textField(): EditText {
        return EditText(context).apply {
            layoutParams = ViewGroup.LayoutParams(FIELD_WIDTH, FIELD_HEIGHT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE)
        }
    }

    private fun configurePreview(preview: MessageWebView) {
        try {
            preview.configure(
                WebViewConfig(useDarkMode = false, autoFitWidth = false, textZoom = PREVIEW_BASE_TEXT_ZOOM),
            )
        } catch (_: UnsupportedOperationException) {
            // Robolectric's WebView provider can reject the AndroidX dark-mode WebSettings API.
        }
    }

    private fun indicatorText(percent: Int): String {
        return context.getString(R.string.message_compose_zoom_indicator, percent)
    }

    private fun startPinch() {
        on(ACTION_DOWN, FIRST_FINGER_X)
        on(ACTION_POINTER_DOWN, FIRST_FINGER_X, FIRST_FINGER_X + PINCH_START_SPAN)

        // The first movement of a pinch is the distance the further ones are measured from.
        moveFingers(span = BASE_SPAN)
    }

    private fun pinch(view: View) {
        on(ACTION_DOWN, FIRST_FINGER_X, view = view)
        on(ACTION_POINTER_DOWN, FIRST_FINGER_X, FIRST_FINGER_X + PINCH_START_SPAN, view = view)
        moveFingers(span = BASE_SPAN, view = view)
    }

    private fun moveFingers(span: Float, firstFingerX: Float = FIRST_FINGER_X, view: View = editText) {
        on(ACTION_MOVE, firstFingerX, firstFingerX + span, view = view)
    }

    private fun liftOneFinger(view: View = editText) {
        on(ACTION_POINTER_UP, FIRST_FINGER_X, FIRST_FINGER_X + BASE_SPAN, view = view)
    }

    private fun liftFingers(view: View = editText) {
        liftOneFinger(view)
        on(ACTION_UP, FIRST_FINGER_X, view = view)
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

    private fun on(action: Int, vararg xs: Float, view: View = editText): Boolean {
        val event = motionEvent(action, *xs)

        return try {
            if (view is MessageWebView) {
                view.dispatchTouchEvent(event)
            } else {
                testSubject.onTouch(view, event)
            }
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

    private companion object {
        const val DEFAULT_ZOOM_FACTOR = 1f
    }
}
