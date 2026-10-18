package com.fsck.k9.view

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isTrue
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.After
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class MessageWebViewTest : RobolectricTest() {
    private val context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        MessageTextZoom.update(DEFAULT_ZOOM_FACTOR)
    }

    @Test
    fun configure_shouldNotUseViewSpecificHardwareLayer() {
        val testSubject = MessageWebView(context).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        configure(testSubject)

        assertThat(testSubject.layerType).isEqualTo(View.LAYER_TYPE_NONE)
    }

    @Test
    fun configure_turnsOffTheZoomOfTheWebViewItself() {
        val testSubject = MessageWebView(context)

        configure(testSubject)

        assertThat(testSubject.settings.builtInZoomControls).isFalse()
        assertThat(testSubject.settings.useWideViewPort).isTrue()
    }

    @Test
    fun theChosenTextSizeIsKeptForTheNextMessage() {
        MessageTextZoom.update(2f)
        val testSubject = MessageWebView(context)

        configure(testSubject)

        assertThat(testSubject.settings.textZoom).isEqualTo(200)
    }

    @Test
    fun pinchingTheMessageChangesTheTextZoom() {
        val testSubject = MessageWebView(context)
        configure(testSubject)
        testSubject.layout(0, 0, VIEW_WIDTH, VIEW_HEIGHT)

        pinch(testSubject, distances = listOf(300f, 600f, 900f))

        assertThat(testSubject.settings.textZoom).isGreaterThan(100)
    }

    @Test
    fun aPinchListenerTakesTheGestureFromTheViewer() {
        var pinching = false
        val testSubject = MessageWebView(context).apply {
            pinchListener = PinchListener {
                pinching = true
                true
            }
        }
        configure(testSubject)
        testSubject.layout(0, 0, VIEW_WIDTH, VIEW_HEIGHT)

        pinch(testSubject, distances = listOf(300f, 600f, 900f))

        assertThat(pinching).isTrue()
        assertThat(MessageTextZoom.factor).isEqualTo(DEFAULT_ZOOM_FACTOR)
    }

    @Test
    fun theTextZoomSetFromOutsideIsTheOneKeptWhenTheWebViewIsConfiguredAgain() {
        val testSubject = MessageWebView(context).apply { pinchListener = PinchListener { false } }

        testSubject.setTextZoomPercent(150)
        configure(testSubject)

        assertThat(testSubject.settings.textZoom).isEqualTo(150)
    }

    /** A pinch in which the two fingers are one [distance] after another apart, starting with a touch down. */
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

    private companion object {
        const val DEFAULT_ZOOM_FACTOR = 1f
        const val VIEW_WIDTH = 1000
        const val VIEW_HEIGHT = 2000
    }
}
