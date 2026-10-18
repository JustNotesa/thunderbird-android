package com.fsck.k9.view

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView
import com.fsck.k9.core.BuildConfig
import com.fsck.k9.mailstore.AttachmentResolver
import kotlin.math.roundToInt
import net.thunderbird.core.android.common.view.showInDarkMode
import net.thunderbird.core.android.common.view.showInLightMode
import net.thunderbird.core.android.webkit.ThunderbirdWebViewSettings
import net.thunderbird.legacy.logging.Log
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val DEFAULT_TEXT_ZOOM = 100

// The range MessageTextZoom zooms in; a zoom from outside is kept inside it as well.
private const val MIN_TEXT_ZOOM_PERCENT = 50
private const val MAX_TEXT_ZOOM_PERCENT = 300
private const val ZOOM_LABEL_TEXT_SIZE_SP = 14f
private const val ZOOM_LABEL_VISIBLE_TIME_MS = 2000L
private const val ZOOM_LABEL_MARGIN_DP = 8f
private const val ZOOM_LABEL_PADDING_DP = 6f

class MessageWebView : WebView, KoinComponent, ThunderbirdWebViewSettings {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    private val webViewClientFactory: WebViewClientFactory by inject()
    private val touchHandler = MessageWebViewTouchHandler(this, ::changeTextZoom)
    private val zoomLabelBackgroundPaint = Paint().apply {
        color = Color.parseColor("#B0000000")
    }
    private val zoomLabelTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = ZOOM_LABEL_TEXT_SIZE_SP * resources.displayMetrics.scaledDensity
        isAntiAlias = true
    }
    private val hideZoomLabelRunnable = Runnable {
        zoomLabelText = null
        invalidate()
    }

    /** The text size of the message without the zoom the reader chose by pinching. */
    private var baseTextZoom = DEFAULT_TEXT_ZOOM

    /** The text size shown while pinching, or `null` while there is nothing to show. */
    private var zoomLabelText: String? = null

    /**
     * Told about every touch as long as it is set, and takes the pinch instead of [touchHandler]: in the composer the
     * previews of the signature and of the quote are zoomed together with the message text around them, so this
     * WebView's own text zoom has to stay out of that gesture. `null` while the message viewer is in charge of it.
     */
    var pinchListener: PinchListener? = null

    /** The size the text is shown in, in percent of [baseTextZoom]. */
    private var zoomPercent: Int = DEFAULT_TEXT_ZOOM

    override var loadWithOverviewMode: Boolean
        get() = settings.loadWithOverviewMode
        set(value) {
            settings.loadWithOverviewMode = value
        }

    /**
     * Puts the text of this WebView in [percent] of the size it was configured with, which is how the composer scales
     * a preview with the message text around it. The size it was configured with stays the one this is measured from,
     * and it is put back when this WebView is configured again.
     */
    fun setTextZoomPercent(percent: Int) {
        zoomPercent = percent.coerceIn(MIN_TEXT_ZOOM_PERCENT, MAX_TEXT_ZOOM_PERCENT)
        settings.textZoom = (baseTextZoom * zoomPercent / DEFAULT_TEXT_ZOOM)
            .coerceIn(MIN_TEXT_ZOOM_PERCENT, MAX_TEXT_ZOOM_PERCENT)
    }

    override fun blockNetworkData(shouldBlockNetworkData: Boolean) {
        // Images with content: URIs will not be blocked, nor will network images that are already in the WebView cache.
        try {
            settings.blockNetworkLoads = shouldBlockNetworkData
        } catch (e: SecurityException) {
            Log.e(e, "Failed to unblock network loads. Missing INTERNET permission?")
        }
    }

    override fun configure(config: WebViewConfig) {
        // The scroll view and the view pager both want multi-touch moves, which is exactly what the text zoom needs.
        setOnTouchListener { _, event ->
            val listener = pinchListener
            if (listener == null) {
                touchHandler.onTouchEvent(event)
                false
            } else {
                listener.onPinch(event)
            }
        }

        isVerticalScrollBarEnabled = true
        scrollBarStyle = SCROLLBARS_INSIDE_OVERLAY
        isLongClickable = true

        // Do not force a view-specific hardware layer. Long messages can make this WebView taller than the
        // device's maximum GPU texture size, causing WebView to crash while creating the layer.
        setLayerType(LAYER_TYPE_NONE, null)

        with(settings) {
            // Pinching changes the text size, so the WebView's own magnifier would only fight that gesture.
            setSupportZoom(false)
            builtInZoomControls = false
            useWideViewPort = true

            if (config.autoFitWidth) {
                loadWithOverviewMode = true
            }

            javaScriptEnabled = false
            loadsImagesAutomatically = true

            overScrollMode = OVER_SCROLL_NEVER

            // Values range from smaller than default (1.0) to double size: 0.85, 1.0, 1.15, 1.3, 1.5, 1.8, 2.0
            val fontScale = Resources.getSystem().configuration.fontScale
            baseTextZoom = (config.textZoom * fontScale).roundToInt()
            if (pinchListener == null) {
                textZoom = MessageTextZoom.textZoom(baseTextZoom)
            } else {
                // A WebView that is zoomed with the text around it keeps that zoom, whatever the reader chose.
                setTextZoomPercent(zoomPercent)
            }
        }

        // Disable network images by default. This is overridden by preferences.
        blockNetworkData(true)
        setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        // Last, so a WebView provider without support for dark mode still gets the settings above.
        configureDarkLightMode(this, config)
    }

    private fun configureDarkLightMode(
        webView: WebView,
        config: WebViewConfig,
    ) {
        if (config.useDarkMode) {
            webView.showInDarkMode()
        } else {
            webView.showInLightMode()
        }
    }

    /** A pinch has changed the text size of the message: [factor] is the new zoom factor. */
    private fun changeTextZoom(factor: Float) {
        MessageTextZoom.update(factor)
        settings.textZoom = MessageTextZoom.textZoom(baseTextZoom)

        zoomLabelText = "${settings.textZoom} %"
        removeCallbacks(hideZoomLabelRunnable)
        postDelayed(hideZoomLabelRunnable, ZOOM_LABEL_VISIBLE_TIME_MS)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val label = zoomLabelText ?: return
        val density = resources.displayMetrics.density
        val margin = ZOOM_LABEL_MARGIN_DP * density
        val padding = ZOOM_LABEL_PADDING_DP * density
        val textHeight = zoomLabelTextPaint.textSize
        val labelWidth = zoomLabelTextPaint.measureText(label) + 2 * padding
        val labelHeight = textHeight + 2 * padding
        val left = width - margin - labelWidth
        val top = margin

        canvas.drawRoundRect(
            left,
            top,
            left + labelWidth,
            top + labelHeight,
            padding,
            padding,
            zoomLabelBackgroundPaint,
        )
        canvas.drawText(label, left + padding, top + padding + textHeight, zoomLabelTextPaint)
    }

    fun displayHtmlContentWithInlineAttachments(
        htmlText: String,
        attachmentResolver: AttachmentResolver?,
        onPageFinishedListener: OnPageFinishedListener?,
    ) {
        val webViewClient = webViewClientFactory.create(attachmentResolver, onPageFinishedListener)
        setWebViewClient(webViewClient)
        loadDataWithBaseURL("about:blank", htmlText, "text/html", "utf-8", null)
        resumeTimers()
    }

    fun interface OnPageFinishedListener {
        fun onPageFinished()
    }
}

/**
 * Takes the pinch over a [MessageWebView] that is zoomed together with the text around it, e.g. a preview in the
 * composer, and returns whether the gesture was one, so that the WebView is left alone while it is.
 */
fun interface PinchListener {
    fun onPinch(event: MotionEvent): Boolean
}
