package com.fsck.k9.view

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
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

private const val DEFAULT_TEXT_ZOOM = 100
private const val ZOOM_LABEL_HEIGHT_DP = 32f
private const val ZOOM_LABEL_TEXT_SIZE_SP = 14f
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

    /** How opaque the zoom label is; the fade out animator lowers this from 1 to 0. */
    private var zoomLabelAlpha = 1f

    private val messageBackgroundColor = MaterialColors.getColor(this, R.attr.colorSurfaceContainerLowest)
    private val zoomLabelBackgroundPaint = Paint().apply {
        color = MaterialColors.getColor(this@MessageWebView, R.attr.colorSurfaceContainerHighest)
        isAntiAlias = true
    }
    private val zoomLabelTextPaint = Paint().apply {
        color = MaterialColors.getColor(this@MessageWebView, R.attr.colorOnSurface)
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

    override fun blockNetworkData(shouldBlockNetworkData: Boolean) {
        // Images with content: URIs will not be blocked, nor will network images that are already in the WebView cache.
        try {
            settings.blockNetworkLoads = shouldBlockNetworkData
        } catch (e: SecurityException) {
            Log.e(e, "Failed to unblock network loads. Missing INTERNET permission?")
        }
    }

    override fun configure(config: WebViewConfig) {
        // The message and the space below it are one surface, so the WebView has to carry the same colour as the view
        // around it. Otherwise a short message sits on a patch of a different colour.
        setBackgroundColor(messageBackgroundColor)

        // The scroll view and the view pager both want multi-touch moves, which is exactly what the text zoom needs.
        setOnTouchListener { _, event ->
            touchHandler.onTouchEvent(event)
            false
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
            textZoom = MessageTextZoom.textZoom(baseTextZoom)
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
        setWebViewClient(attachmentResolver, onPageFinishedListener)
        setHtmlContent(htmlText)
    }

    private fun setWebViewClient(
        attachmentResolver: AttachmentResolver?,
        onPageFinishedListener: OnPageFinishedListener?,
    ) {
        val webViewClient = webViewClientFactory.create(attachmentResolver, onPageFinishedListener)
        setWebViewClient(webViewClient)
    }

    private fun setHtmlContent(htmlText: String) {
        loadDataWithBaseURL("about:blank", htmlText, "text/html", "utf-8", null)
        resumeTimers()
    }

    fun interface OnPageFinishedListener {
        fun onPageFinished()
    }
}
