package com.fsck.k9.ui.compose.richtext

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.style.ImageSpan
import android.widget.EditText
import net.thunderbird.feature.mail.message.composer.richtext.InlineImage
import kotlin.math.cos
import kotlin.math.roundToInt

/** The character in the text that stands for an inline image. */
internal const val IMAGE_CHARACTER = '￼'

private const val PLACEHOLDER_WIDTH = 240
private const val PLACEHOLDER_HEIGHT = 160
private const val PLACEHOLDER_COLOR = 0x33808080

// The label that shows the width of an image while it is being resized: white on a dark ground, on top of the image.
private const val PERCENT = " %"
private const val LABEL_TEXT_SIZE_FRACTION = 0.4f
private const val LABEL_PADDING_FRACTION = 0.2f
private const val PADDING_SIDES = 2f
private const val LABEL_TEXT_COLOR = 0xFFFFFFFF.toInt()
private const val LABEL_BACKGROUND_COLOR = 0xB3000000.toInt()

// The hint that shows the user the image can be resized by swiping sideways over it: a translucent dark pill in the
// middle of the image with a white arrow on it whose heads move outward and back.
private const val HINT_PILL_COLOR = 0x99000000.toInt()
private const val HINT_ARROW_COLOR = 0xFFFFFFFF.toInt()
private const val HINT_PILL_WIDTH_FRACTION = 0.5f
private const val HINT_PILL_MAX_WIDTH_DP = 160f
private const val HINT_PILL_HEIGHT_DP = 40f
private const val HINT_ARROW_REACH = 0.28f
private const val HINT_ARROW_STRETCH = 0.14f
private const val HINT_ARROW_HEAD = 0.15f
private const val HINT_ARROW_STROKE_DP = 2f
private const val HINT_FADE_IN = 0.15f
private const val HINT_FADE_OUT = 0.3f
private const val HINT_STRETCHES = 2f
private const val FULL_TURN = 6.2831855f
private const val FULL_ALPHA = 255

/**
 * Gives the editor the pictures of the inline images of a message.
 */
fun interface InlineImageStore {
    /**
     * The picture of the inline image with this Content-ID, or `null` if it is not available (yet). The store tells
     * the editor when that changes.
     */
    fun drawableFor(contentId: String): Drawable?
}

/**
 * Where an inline image may be drawn: its size is limited to [maxWidth] pixels (the width of the text) and
 * [maxHeight] pixels, keeping the proportions. [maxWidth] may still be unknown before the text field is laid out,
 * which is `0`.
 */
internal class InlineImageLimits(val maxWidth: () -> Int, val maxHeight: Int) {
    companion object {
        // An image never takes more of the screen's height than this, so there is room to write around it.
        private const val MAX_HEIGHT_FRACTION = 0.6f

        /** The limits for images in [editText]: the width of its text, and 60 % of the screen's height. */
        fun of(editText: EditText): InlineImageLimits {
            val screenHeight = editText.resources.displayMetrics.heightPixels

            return InlineImageLimits(
                maxWidth = { editText.width - editText.paddingLeft - editText.paddingRight },
                maxHeight = (screenHeight * MAX_HEIGHT_FRACTION).toInt(),
            )
        }
    }
}

/**
 * Creates the spans of the inline images of a text, with the pictures [store] has and a placeholder for the others.
 */
internal class InlineImages(private val store: InlineImageStore, private val limits: InlineImageLimits) {
    fun spanFor(contentId: String, widthPercent: Int? = null): InlineImageSpan {
        val drawable = store.drawableFor(contentId)
            ?: return InlineImageSpan.placeholder(contentId, limits, widthPercent)

        return InlineImageSpan(drawable, contentId, limits, widthPercent = widthPercent)
    }

    companion object {
        /** For text whose pictures are not available, e.g. in tests: every image is a placeholder. */
        val NONE = InlineImages({ null }, InlineImageLimits(maxWidth = { 0 }, maxHeight = Int.MAX_VALUE))
    }
}

/**
 * An inline image in the text, over its [IMAGE_CHARACTER]. [contentId] is the Content-ID of the attachment that holds
 * the image. A [drawable] that is not the picture itself shows that it is missing.
 */
internal class InlineImageSpan(
    drawable: Drawable,
    val contentId: String,
    private val limits: InlineImageLimits,
    val isPlaceholder: Boolean = false,
    widthPercent: Int? = null,
) : ImageSpan(drawable, contentId, ALIGN_BOTTOM) {
    /**
     * The width the image is shown with, as a percentage of the width of the text, `null` for the size the image has
     * of its own. The user sets it by swiping sideways over the image.
     */
    var widthPercent: Int? = null
        set(value) {
            field = value?.coerceIn(InlineImage.MIN_WIDTH_PERCENT, InlineImage.MAX_WIDTH_PERCENT)
        }

    /** Whether the user is swiping over the image right now, which shows the width the gesture gives it. */
    var isResizing: Boolean = false

    /**
     * How far the hint that shows the user can resize this image by swiping sideways over it has come, `null` while no
     * hint is shown. The hint is drawn on top of the image and makes the arrow on it move outward and back.
     */
    var hintProgress: Float? = null

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hintBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hintArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val hintPath = Path()

    init {
        this.widthPercent = widthPercent
    }

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        fitDrawable()

        return super.getSize(paint, text, start, end, fm)
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        super.draw(canvas, text, start, end, x, top, y, bottom, paint)

        // ImageSpan draws the image at x with its bottom on the bottom of the line; the label goes on top of it.
        val imageY = (bottom - drawable.bounds.bottom).toFloat()
        if (isResizing) drawWidthLabel(canvas, paint.textSize, x, imageY)
        hintProgress?.let { drawHint(canvas, x, imageY, it) }
    }

    private fun fitDrawable() {
        val image = drawable
        val width = image.intrinsicWidth
        val height = image.intrinsicHeight
        if (width <= 0 || height <= 0) return

        val available = limits.maxWidth().takeIf { it > 0 } ?: width
        val percent = widthPercent
        val scale = if (percent == null) {
            // Without a width of its own the image keeps the size it has, as long as it fits.
            minOf(1f, available.toFloat() / width, limits.maxHeight.toFloat() / height)
        } else {
            val wanted = available * percent / InlineImage.MAX_WIDTH_PERCENT
            minOf(wanted.toFloat() / width, limits.maxHeight.toFloat() / height)
        }

        val scaledWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (height * scale).roundToInt().coerceAtLeast(1)
        image.setBounds(0, 0, scaledWidth, scaledHeight)
    }

    /**
     * The width the image would be sent with, shown on top of it while the user swipes, so that the gesture says what
     * it does before it is done. It is drawn in the text size of the text around the image.
     */
    private fun drawWidthLabel(canvas: Canvas, textSize: Float, imageX: Float, imageY: Float) {
        val percent = widthPercent ?: return
        if (textSize <= 0f) return

        val label = "$percent$PERCENT"
        val size = textSize * LABEL_TEXT_SIZE_FRACTION
        val padding = size * LABEL_PADDING_FRACTION
        labelPaint.textSize = size
        labelPaint.color = LABEL_TEXT_COLOR
        labelBackgroundPaint.color = LABEL_BACKGROUND_COLOR

        // The ground of the label leaves a padding of its own on every side of the text.
        val bounds = drawable.bounds
        val left = imageX + bounds.left
        val top = imageY + bounds.top
        val bottom = top + size + padding * PADDING_SIDES
        val right = left + labelPaint.measureText(label) + padding * PADDING_SIDES

        canvas.drawRect(left, top, right, bottom, labelBackgroundPaint)
        canvas.drawText(label, left + padding, bottom - padding, labelPaint)
    }

    /**
     * The hint that shows the user the image can be resized by swiping sideways over it: a dark pill in the middle of
     * the image with a white arrow on it that reaches further out and back while the hint is shown. [progress] is how
     * far the hint has come, from 0 to 1.
     */
    private fun drawHint(canvas: Canvas, imageX: Float, imageY: Float, progress: Float) {
        val bounds = drawable.bounds
        if (bounds.width() <= 0 || bounds.height() <= 0) return

        val width = minOf(bounds.width() * HINT_PILL_WIDTH_FRACTION, HINT_PILL_MAX_WIDTH_DP * DEFAULT_DENSITY)
        val height = HINT_PILL_HEIGHT_DP * DEFAULT_DENSITY
        val centerX = imageX + bounds.centerX()
        val centerY = imageY + bounds.centerY()
        val opacity = minOf(progress / HINT_FADE_IN, (1f - progress) / HINT_FADE_OUT).coerceIn(0f, 1f)
        val alpha = (FULL_ALPHA * opacity).roundToInt()

        hintBackgroundPaint.color = HINT_PILL_COLOR
        hintBackgroundPaint.alpha = alpha
        canvas.drawRoundRect(
            centerX - width / 2f,
            centerY - height / 2f,
            centerX + width / 2f,
            centerY + height / 2f,
            height / 2f,
            height / 2f,
            hintBackgroundPaint,
        )

        drawHintArrow(canvas, centerX, centerY, width, height, alpha, progress)
    }

    /** The double-headed arrow on the pill, its heads reaching outward and back as often as the progress goes by. */
    private fun drawHintArrow(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        pillWidth: Float,
        pillHeight: Float,
        alpha: Int,
        progress: Float,
    ) {
        val reach = pillWidth * (HINT_ARROW_REACH + HINT_ARROW_STRETCH * hintStretch(progress))
        val head = pillHeight * HINT_ARROW_HEAD

        hintPath.reset()
        hintPath.moveTo(centerX - reach, centerY)
        hintPath.lineTo(centerX + reach, centerY)
        hintPath.moveTo(centerX - reach, centerY - head)
        hintPath.lineTo(centerX - reach, centerY)
        hintPath.lineTo(centerX - reach, centerY + head)
        hintPath.moveTo(centerX + reach, centerY - head)
        hintPath.lineTo(centerX + reach, centerY)
        hintPath.lineTo(centerX + reach, centerY + head)

        hintArrowPaint.color = HINT_ARROW_COLOR
        hintArrowPaint.alpha = alpha
        hintArrowPaint.strokeWidth = pillHeight * HINT_ARROW_STROKE_DP / HINT_PILL_HEIGHT_DP
        canvas.drawPath(hintPath, hintArrowPaint)
    }

    /** How much of the pill is there at [progress]: it fades in at the beginning and out at the end. */
    private fun hintStretch(progress: Float): Float {
        val turn = progress * HINT_STRETCHES * FULL_TURN

        return (1f - cos(turn)) / 2f
    }

    companion object {
        /**
         * The picture shown for an image that is not available: a grey area of a fixed size.
         */
        fun placeholder(contentId: String, limits: InlineImageLimits, widthPercent: Int? = null): InlineImageSpan {
            val drawable = GradientDrawable().apply {
                setColor(PLACEHOLDER_COLOR)
                setSize(PLACEHOLDER_WIDTH, PLACEHOLDER_HEIGHT)
            }

            return InlineImageSpan(drawable, contentId, limits, isPlaceholder = true, widthPercent = widthPercent)
        }
    }
}
