package com.fsck.k9.view

import android.content.Context
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.WebView
import android.widget.FrameLayout
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class MessageWebViewTouchHandlerTest : RobolectricTest() {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun aSingleFingerOnPlainContentMayBeIntercepted() {
        val shouldDisallow = shouldDisallowInterceptTouchEvent(
            pointerCount = 1,
            isZoomGesture = false,
            isZoomedIn = false,
            isHorizontalDrag = false,
        )

        assertThat(shouldDisallow).isFalse()
    }

    @Test
    fun aSecondPointerIsNeverIntercepted() {
        val shouldDisallow = shouldDisallowInterceptTouchEvent(
            pointerCount = 2,
            isZoomGesture = false,
            isZoomedIn = false,
            isHorizontalDrag = false,
        )

        assertThat(shouldDisallow).isTrue()
    }

    @Test
    fun aFinishedZoomGestureIsNotInterceptedAnymore() {
        val shouldDisallow = shouldDisallowInterceptTouchEvent(
            pointerCount = 1,
            isZoomGesture = true,
            isZoomedIn = true,
            isHorizontalDrag = true,
        )

        assertThat(shouldDisallow).isTrue()
    }

    @Test
    fun aHorizontalDragOfZoomedInContentIsNotIntercepted() {
        val shouldDisallow = shouldDisallowInterceptTouchEvent(
            pointerCount = 1,
            isZoomGesture = false,
            isZoomedIn = true,
            isHorizontalDrag = true,
        )

        assertThat(shouldDisallow).isTrue()
    }

    @Test
    fun aVerticalDragOfZoomedInContentMayBeIntercepted() {
        val shouldDisallow = shouldDisallowInterceptTouchEvent(
            pointerCount = 1,
            isZoomGesture = false,
            isZoomedIn = true,
            isHorizontalDrag = false,
        )

        assertThat(shouldDisallow).isFalse()
    }

    @Test
    fun aHorizontalDragOfPlainContentMayBeIntercepted() {
        val shouldDisallow = shouldDisallowInterceptTouchEvent(
            pointerCount = 1,
            isZoomGesture = false,
            isZoomedIn = false,
            isHorizontalDrag = true,
        )

        assertThat(shouldDisallow).isFalse()
    }

    @Test
    fun aSecondPointerKeepsTheParentsFromIntercepting() {
        val parent = FakeParentView(context)
        val webView = FakeWebView(context)
        parent.addView(webView)
        val testSubject = MessageWebViewTouchHandler(webView)

        testSubject.onTouchEvent(motionEvent(MotionEvent.ACTION_DOWN))
        testSubject.onTouchEvent(motionEvent(MotionEvent.ACTION_POINTER_DOWN, pointerCount = 2))

        assertThat(parent.isInterceptDisallowed).isTrue()
    }

    @Test
    fun liftingAFingerKeepsTheParentsFromIntercepting() {
        val parent = FakeParentView(context)
        val webView = FakeWebView(context)
        parent.addView(webView)
        val testSubject = MessageWebViewTouchHandler(webView)

        testSubject.onTouchEvent(motionEvent(MotionEvent.ACTION_DOWN))
        testSubject.onTouchEvent(motionEvent(MotionEvent.ACTION_POINTER_DOWN, pointerCount = 2))
        testSubject.onTouchEvent(motionEvent(MotionEvent.ACTION_POINTER_UP, pointerCount = 2))
        testSubject.onTouchEvent(motionEvent(MotionEvent.ACTION_MOVE, x = 1f))

        assertThat(parent.isInterceptDisallowed).isTrue()
    }

    @Test
    fun endingAGestureReleasesTheParents() {
        val parent = FakeParentView(context)
        val webView = FakeWebView(context)
        parent.addView(webView)
        val testSubject = MessageWebViewTouchHandler(webView)

        testSubject.onTouchEvent(motionEvent(MotionEvent.ACTION_DOWN))
        testSubject.onTouchEvent(motionEvent(MotionEvent.ACTION_POINTER_DOWN, pointerCount = 2))
        testSubject.onTouchEvent(motionEvent(MotionEvent.ACTION_UP, pointerCount = 2))

        assertThat(parent.isInterceptDisallowed).isFalse()
    }

    @Test
    fun aHorizontalDragOfZoomedInContentKeepsTheParentsFromIntercepting() {
        val parent = FakeParentView(context)
        val webView = FakeWebView(context).apply { isZoomedIn = true }
        parent.addView(webView)
        val testSubject = MessageWebViewTouchHandler(webView)

        testSubject.onTouchEvent(motionEvent(MotionEvent.ACTION_DOWN))
        testSubject.onTouchEvent(motionEvent(MotionEvent.ACTION_MOVE, x = 500f))

        assertThat(parent.isInterceptDisallowed).isTrue()
    }

    @Test
    fun aVerticalDragLeavesTheParentsAlone() {
        val parent = FakeParentView(context)
        val webView = FakeWebView(context).apply { isZoomedIn = true }
        parent.addView(webView)
        val testSubject = MessageWebViewTouchHandler(webView)

        testSubject.onTouchEvent(motionEvent(MotionEvent.ACTION_DOWN))
        testSubject.onTouchEvent(motionEvent(MotionEvent.ACTION_MOVE, y = 500f))

        assertThat(parent.isInterceptDisallowed).isFalse()
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

        webView.dispatchTouchEvent(motionEvent(MotionEvent.ACTION_DOWN))
        webView.dispatchTouchEvent(motionEvent(MotionEvent.ACTION_POINTER_DOWN, pointerCount = 2))

        assertThat(parent.isInterceptDisallowed).isTrue()
    }

    private fun motionEvent(
        action: Int,
        pointerCount: Int = 1,
        x: Float = 0f,
        y: Float = 0f,
    ): MotionEvent {
        val pointerProperties = Array(pointerCount) { index ->
            MotionEvent.PointerProperties().apply {
                id = index
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val pointerCoords = Array(pointerCount) { index ->
            MotionEvent.PointerCoords().apply {
                this.x = x + index
                this.y = y + index
            }
        }

        return MotionEvent.obtain(
            0L,
            0L,
            action,
            pointerCount,
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

    private class FakeWebView(context: Context) : WebView(context) {
        var isZoomedIn: Boolean = false

        override fun canScrollHorizontally(direction: Int): Boolean = isZoomedIn
    }

    private class FakeParentView(context: Context) : FrameLayout(context) {
        var isInterceptDisallowed: Boolean = false
            private set

        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            isInterceptDisallowed = disallowIntercept
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }
}
