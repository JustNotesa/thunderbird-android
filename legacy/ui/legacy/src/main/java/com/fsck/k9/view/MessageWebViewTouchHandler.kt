package com.fsck.k9.view

import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import kotlin.math.abs

/**
 * Keeps the views around a [MessageWebView] from taking over gestures that belong to the WebView: the
 * [NonLockingScrollView] that scrolls the message as a whole and the view pager that switches between messages.
 *
 * Pinch zoom needs both pointers, so from the moment the second one touches the screen until the gesture ends the
 * parents have to stay out of the way. The same holds for a horizontal drag once the content is zoomed in far enough
 * to be panned: that drag pans the message instead of switching to the next one.
 */
internal class MessageWebViewTouchHandler(private val webView: WebView) {
    private val touchSlop = ViewConfiguration.get(webView.context).scaledTouchSlop

    private var initialX = 0f
    private var initialY = 0f
    private var isZoomGesture = false

    fun onTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> startGesture(event)
            MotionEvent.ACTION_POINTER_DOWN -> startZoomGesture()
            MotionEvent.ACTION_MOVE -> updateGesture(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endGesture()
        }
    }

    private fun startGesture(event: MotionEvent) {
        initialX = event.x
        initialY = event.y
        isZoomGesture = false
    }

    private fun startZoomGesture() {
        isZoomGesture = true
        setDisallowInterceptTouchEvent(true)
    }

    private fun updateGesture(event: MotionEvent) {
        val shouldDisallow = shouldDisallowInterceptTouchEvent(
            pointerCount = event.pointerCount,
            isZoomGesture = isZoomGesture,
            isZoomedIn = isZoomedIn(),
            isHorizontalDrag = isHorizontalDrag(event),
        )

        // Never allow intercepting again from here. The flag is shared with TouchInterceptView and the scroll view,
        // and they are the ones that know about the gestures this WebView doesn't care about.
        if (shouldDisallow) {
            setDisallowInterceptTouchEvent(true)
        }
    }

    private fun endGesture() {
        isZoomGesture = false
        setDisallowInterceptTouchEvent(false)
    }

    private fun setDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Walking up from the WebView covers both the scroll view and the view pager.
        webView.parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    private fun isZoomedIn(): Boolean {
        return webView.canScrollHorizontally(1) || webView.canScrollHorizontally(-1)
    }

    private fun isHorizontalDrag(event: MotionEvent): Boolean {
        val deltaX = abs(event.x - initialX)
        val deltaY = abs(event.y - initialY)

        return deltaX > touchSlop && deltaX > deltaY
    }
}

/**
 * Whether the parents of the [MessageWebView] have to be told not to intercept the gesture in progress.
 *
 * [isZoomGesture] stays true from the moment a second pointer touched the screen until the gesture ends, also after
 * one of the pointers has been lifted again.
 */
internal fun shouldDisallowInterceptTouchEvent(
    pointerCount: Int,
    isZoomGesture: Boolean,
    isZoomedIn: Boolean,
    isHorizontalDrag: Boolean,
): Boolean {
    return isZoomGesture || pointerCount > 1 || (isZoomedIn && isHorizontalDrag)
}
