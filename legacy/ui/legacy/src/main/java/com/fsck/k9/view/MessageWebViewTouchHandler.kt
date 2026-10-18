package com.fsck.k9.view

import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.webkit.WebView

private const val DEFAULT_ZOOM_FACTOR = 1f

/**
 * Keeps the views around a [MessageWebView] from taking over gestures that belong to the WebView: the
 * [NonLockingScrollView] that scrolls the message as a whole and the view pager that switches between messages.
 *
 * Pinching the message changes its text size, so from the moment the second pointer touches the screen until the
 * gesture ends the parents have to stay out of the way. With one finger the parents are left alone, so a horizontal
 * swipe switches to the next message.
 */
internal class MessageWebViewTouchHandler(
    private val webView: WebView,
    private val onTextZoomChanged: (Float) -> Unit,
) {
    private val scaleGestureDetector = ScaleGestureDetector(webView.context, ScaleListener())

    private var isZoomGesture = false

    fun onTouchEvent(event: MotionEvent) {
        scaleGestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> startGesture()
            MotionEvent.ACTION_POINTER_DOWN -> startZoomGesture()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endGesture()
        }
    }

    private fun startGesture() {
        isZoomGesture = false
    }

    private fun startZoomGesture() {
        isZoomGesture = true
        setDisallowInterceptTouchEvent(true)
    }

    private fun endGesture() {
        isZoomGesture = false
        setDisallowInterceptTouchEvent(false)
    }

    private fun setDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Walking up from the WebView covers both the scroll view and the view pager.
        webView.parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var startFactor = DEFAULT_ZOOM_FACTOR
        private var scaleFactor = DEFAULT_ZOOM_FACTOR

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            startFactor = MessageTextZoom.factor
            scaleFactor = DEFAULT_ZOOM_FACTOR

            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            onTextZoomChanged(calculateZoomFactor(startFactor, scaleFactor))

            return true
        }
    }
}
