package com.fsck.k9.view

import android.content.Context
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.WebView
import android.widget.FrameLayout
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isTrue
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.After
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class MessageWebViewTouchHandlerTest : RobolectricTest() {
    private val context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        MessageTextZoom.update(DEFAULT_ZOOM_FACTOR)
    }

    @Test
    fun aSecondPointerKeepsTheParentsFromIntercepting() {
        val parent = FakeParentView(context)
        val webView = WebView(context)
        parent.addView(webView)
        val testSubject = createTestSubject(webView)

        testSubject.onTouchEvent(pinchMotionEvent(MotionEvent.ACTION_DOWN, firstX = 0f, secondX = 300f))
        testSubject.onTouchEvent(pinchMotionEvent(MotionEvent.ACTION_POINTER_DOWN, firstX = 0f, secondX = 300f))

        assertThat(parent.isInterceptDisallowed).isTrue()
    }

    @Test
    fun liftingAFingerKeepsTheParentsFromIntercepting() {
        val parent = FakeParentView(context)
        val webView = WebView(context)
        parent.addView(webView)
        val testSubject = createTestSubject(webView)

        testSubject.onTouchEvent(pinchMotionEvent(MotionEvent.ACTION_DOWN, firstX = 0f, secondX = 300f))
        testSubject.onTouchEvent(pinchMotionEvent(MotionEvent.ACTION_POINTER_DOWN, firstX = 0f, secondX = 300f))
        testSubject.onTouchEvent(pinchMotionEvent(MotionEvent.ACTION_POINTER_UP, firstX = 0f, secondX = 300f))
        testSubject.onTouchEvent(pinchMotionEvent(MotionEvent.ACTION_MOVE, firstX = 0f, secondX = 300f))

        assertThat(parent.isInterceptDisallowed).isTrue()
    }

    @Test
    fun endingAGestureReleasesTheParents() {
        val parent = FakeParentView(context)
        val webView = WebView(context)
        parent.addView(webView)
        val testSubject = createTestSubject(webView)

        testSubject.onTouchEvent(pinchMotionEvent(MotionEvent.ACTION_DOWN, firstX = 0f, secondX = 300f))
        testSubject.onTouchEvent(pinchMotionEvent(MotionEvent.ACTION_POINTER_DOWN, firstX = 0f, secondX = 300f))
        testSubject.onTouchEvent(pinchMotionEvent(MotionEvent.ACTION_UP, firstX = 0f, secondX = 300f))

        assertThat(parent.isInterceptDisallowed).isFalse()
    }

    @Test
    fun aHorizontalDragIsAllowedEvenWhenTheWebViewCouldScrollSideways() {
        val parent = FakeParentView(context)
        val webView = FakeWebView(context)
        parent.addView(webView)
        val testSubject = createTestSubject(webView)

        testSubject.onTouchEvent(motionEvent(MotionEvent.ACTION_DOWN))
        testSubject.onTouchEvent(motionEvent(MotionEvent.ACTION_MOVE, x = 500f))

        assertThat(parent.isInterceptDisallowed).isFalse()
    }

    @Test
    fun aVerticalDragLeavesTheParentsAlone() {
        val parent = FakeParentView(context)
        val webView = FakeWebView(context)
        parent.addView(webView)
        val testSubject = createTestSubject(webView)

        testSubject.onTouchEvent(motionEvent(MotionEvent.ACTION_DOWN))
        testSubject.onTouchEvent(motionEvent(MotionEvent.ACTION_MOVE, y = 500f))

        assertThat(parent.isInterceptDisallowed).isFalse()
    }

    @Test
    fun spreadingTwoFingersEnlargesTheText() {
        var zoomFactor = DEFAULT_ZOOM_FACTOR
        val testSubject = createTestSubject(WebView(context)) { zoomFactor = it }

        pinch(testSubject, distances = listOf(300f, 600f, 900f))

        assertThat(zoomFactor).isGreaterThan(1f)
    }

    @Test
    fun pinchingKeepsTheTextWithinTheZoomRange() {
        var zoomFactor = DEFAULT_ZOOM_FACTOR
        val testSubject = createTestSubject(WebView(context)) { zoomFactor = it }

        pinch(testSubject, distances = listOf(300f, 600f, 6000f))

        assertThat(zoomFactor).isEqualTo(3f)
    }

    @Test
    fun aSecondPinchStartsAtTheTextSizeTheFirstPinchLeft() {
        var zoomFactor = DEFAULT_ZOOM_FACTOR
        val testSubject = createTestSubject(WebView(context)) { zoomFactor = it }

        pinch(testSubject, distances = listOf(300f, 600f, 900f))
        MessageTextZoom.update(zoomFactor)
        pinch(testSubject, distances = listOf(300f, 600f, 900f))

        assertThat(zoomFactor).isGreaterThan(1.5f)
    }

    @Test
    fun theMessageWebViewHandsItsTouchesToTheTouchHandler() {
        val parent = FakeParentView(context)
        val webView = MessageWebView(context)
        parent.addView(webView)

        try {
            webView.configure(WebViewConfig(useDarkMode = false, autoFitWidth = false, textZoom = 100))
        } catch (_: UnsupportedOperationException) {
            // Robolectric's WebView provider can reject the AndroidX dark-mode WebSettings API.
        }

        webView.dispatchTouchEvent(pinchMotionEvent(MotionEvent.ACTION_DOWN, firstX = 0f, secondX = 300f))
        webView.dispatchTouchEvent(pinchMotionEvent(MotionEvent.ACTION_POINTER_DOWN, firstX = 0f, secondX = 300f))

        assertThat(parent.isInterceptDisallowed).isTrue()
    }

    /** A pinch in which the two fingers are one [distance] after another apart, starting with a touch down. */
    private fun pinch(testSubject: MessageWebViewTouchHandler, distances: List<Float>) {
        val firstDistance = distances.first()
        testSubject.onTouchEvent(pinchMotionEvent(MotionEvent.ACTION_DOWN, firstX = 0f, secondX = firstDistance))
        testSubject.onTouchEvent(
            pinchMotionEvent(MotionEvent.ACTION_POINTER_DOWN, firstX = 0f, secondX = firstDistance),
        )

        distances.drop(1).forEach { distance ->
            testSubject.onTouchEvent(pinchMotionEvent(MotionEvent.ACTION_MOVE, firstX = 0f, secondX = distance))
        }
    }

    private fun createTestSubject(
        webView: WebView,
        onTextZoomChanged: (Float) -> Unit = {},
    ): MessageWebViewTouchHandler {
        return MessageWebViewTouchHandler(webView, onTextZoomChanged)
    }

    private fun motionEvent(
        action: Int,
        x: Float = 0f,
        y: Float = 0f,
    ): MotionEvent {
        val pointerProperties = arrayOf(pointerProperties(id = 0))
        val pointerCoords = arrayOf(pointerCoords(x, y))

        return obtainMotionEvent(action, pointerProperties, pointerCoords)
    }

    private fun pinchMotionEvent(
        action: Int,
        firstX: Float,
        secondX: Float,
    ): MotionEvent {
        val pointerProperties = arrayOf(pointerProperties(id = 0), pointerProperties(id = 1))
        val pointerCoords = arrayOf(pointerCoords(firstX, 0f), pointerCoords(secondX, 0f))

        return obtainMotionEvent(action, pointerProperties, pointerCoords)
    }

    private fun obtainMotionEvent(
        action: Int,
        pointerProperties: Array<MotionEvent.PointerProperties>,
        pointerCoords: Array<MotionEvent.PointerCoords>,
    ): MotionEvent {
        return MotionEvent.obtain(
            0L,
            0L,
            action,
            pointerProperties.size,
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

    private fun pointerProperties(id: Int): MotionEvent.PointerProperties {
        return MotionEvent.PointerProperties().apply {
            this.id = id
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
    }

    private fun pointerCoords(x: Float, y: Float): MotionEvent.PointerCoords {
        return MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
        }
    }

    private class FakeWebView(context: Context) : WebView(context) {
        override fun canScrollHorizontally(direction: Int): Boolean = true
    }

    private class FakeParentView(context: Context) : FrameLayout(context) {
        var isInterceptDisallowed: Boolean = false
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
