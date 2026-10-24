package com.fsck.k9.view

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView
import com.fsck.k9.core.BuildConfig
import com.fsck.k9.mailstore.AttachmentResolver
import com.google.android.material.R
import com.google.android.material.color.MaterialColors
import kotlin.math.roundToInt
import net.thunderbird.core.android.common.view.showInDarkMode
import net.thunderbird.core.android.common.view.showInLightMode
import net.thunderbird.core.android.webkit.ThunderbirdWebViewSettings
import net.thunderbird.legacy.logging.Log
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val TAG = "MessageWebView"

private const val DEFAULT_TEXT_ZOOM = 100

// The range MessageTextZoom zooms in; a zoom from outside is kept inside it as well.
private const val MIN_TEXT_ZOOM_PERCENT = 50
private const val MAX_TEXT_ZOOM_PERCENT = 300
private const val ZOOM_LABEL_HEIGHT_DP = 32f
private const val ZOOM_LABEL_TEXT_SIZE_SP = 14f

// Used where the theme doesn't define the Material colours, e.g. a preview inflated with another theme.
private const val FALLBACK_SURFACE_COLOR = 0xFFFFFFFF.toInt()
private const val FALLBACK_LABEL_COLOR = 0xFFE3E3E3.toInt()
private const val FALLBACK_LABEL_TEXT_COLOR = 0xFF1B1B1B.toInt()
private const val ZOOM_LABEL_HORIZONTAL_PADDING_DP = 16f
private const val ZOOM_LABEL_TOP_MARGIN_DP = 8f
private const val ZOOM_LABEL_VISIBLE_TIME_MS = 1000L
private const val ZOOM_LABEL_FADE_OUT_DURATION_MS = 200L
private const val OPAQUE_ALPHA = 255

class MessageWebView : WebView, KoinComponent, ThunderbirdWebViewSettings {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    private val webViewClientFactory: WebViewClientFactory by inject()
    private val touchHandler = MessageWebViewTouchHandler(this, ::changeTextZoom)
    private val zoomLabelHandler = Handler(Looper.getMainLooper())

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
    /** How opaque the zoom label is; the fade out animator lowers this from 1 to 0. */
    private var zoomLabelAlpha = 1f

    private val messageBackgroundColor =
        MaterialColors.getColor(context, R.attr.colorSurfaceContainerLowest, FALLBACK_SURFACE_COLOR)
    private val zoomLabelBackgroundPaint = Paint().apply {
        color = MaterialColors.getColor(context, R.attr.colorSurfaceContainerHighest, FALLBACK_LABEL_COLOR)
        isAntiAlias = true
    }
    private val zoomLabelTextPaint = Paint().apply {
        color = MaterialColors.getColor(context, R.attr.colorOnSurface, FALLBACK_LABEL_TEXT_COLOR)
        textSize = ZOOM_LABEL_TEXT_SIZE_SP * resources.displayMetrics.scaledDensity
        isAntiAlias = true
    }
    private val zoomLabelFadeOut = ValueAnimator.ofFloat(1f, 0f).apply {
        duration = ZOOM_LABEL_FADE_OUT_DURATION_MS
        addUpdateListener { animation ->
            zoomLabelAlpha = animation.animatedValue as Float
            invalidate()
        }
    }
    private val fadeOutZoomLabelRunnable = Runnable { zoomLabelFadeOut.start() }
    private val hideZoomLabelRunnable = Runnable {
        zoomLabelText = null
        zoomLabelAlpha = 1f
        invalidate()
    }

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
            Log.e(TAG, e, "Failed to unblock network loads. Missing INTERNET permission?")
        }
    }

    override fun configure(config: WebViewConfig) {
        // The message and the space below it are one surface, so the WebView has to carry the same colour as the view
        // around it. Otherwise a short message sits on a patch of a different colour.
        setBackgroundColor(messageBackgroundColor)

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
        zoomLabelAlpha = 1f
        zoomLabelFadeOut.cancel()
        zoomLabelHandler.removeCallbacks(fadeOutZoomLabelRunnable)
        zoomLabelHandler.removeCallbacks(hideZoomLabelRunnable)
        zoomLabelHandler.postDelayed(fadeOutZoomLabelRunnable, ZOOM_LABEL_VISIBLE_TIME_MS)
        zoomLabelHandler.postDelayed(
            hideZoomLabelRunnable,
            ZOOM_LABEL_VISIBLE_TIME_MS + ZOOM_LABEL_FADE_OUT_DURATION_MS,
        )
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val label = zoomLabelText ?: return
        val density = resources.displayMetrics.density
        val labelHeight = ZOOM_LABEL_HEIGHT_DP * density
        val horizontalPadding = ZOOM_LABEL_HORIZONTAL_PADDING_DP * density
        val labelWidth = zoomLabelTextPaint.measureText(label) + 2 * horizontalPadding
        val alpha = (OPAQUE_ALPHA * zoomLabelAlpha).roundToInt()
        val left = (width - labelWidth) / 2
        val top = ZOOM_LABEL_TOP_MARGIN_DP * density

        zoomLabelBackgroundPaint.alpha = alpha
        zoomLabelTextPaint.alpha = alpha

        canvas.save()
        canvas.translate(left, top)
        canvas.drawRoundRect(
            0f,
            0f,
            labelWidth,
            labelHeight,
            labelHeight / 2,
            labelHeight / 2,
            zoomLabelBackgroundPaint,
        )
        canvas.drawText(label, horizontalPadding, (labelHeight + zoomLabelTextPaint.textSize) / 2, zoomLabelTextPaint)
        canvas.restore()
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
