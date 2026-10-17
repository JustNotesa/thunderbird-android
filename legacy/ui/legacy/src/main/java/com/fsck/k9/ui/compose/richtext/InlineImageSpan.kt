package com.fsck.k9.ui.compose.richtext

import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.style.ImageSpan
import android.widget.EditText
import kotlin.math.roundToInt

/** The character in the text that stands for an inline image. */
internal const val IMAGE_CHARACTER = '￼'

private const val PLACEHOLDER_WIDTH = 240
private const val PLACEHOLDER_HEIGHT = 160
private const val PLACEHOLDER_COLOR = 0x33808080

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
    fun spanFor(contentId: String): InlineImageSpan {
        val drawable = store.drawableFor(contentId) ?: return InlineImageSpan.placeholder(contentId, limits)

        return InlineImageSpan(drawable, contentId, limits)
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
) : ImageSpan(drawable, contentId, ALIGN_BOTTOM) {
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        fitDrawable()

        return super.getSize(paint, text, start, end, fm)
    }

    private fun fitDrawable() {
        val image = drawable
        val width = image.intrinsicWidth
        val height = image.intrinsicHeight
        if (width <= 0 || height <= 0) return

        val maxWidth = limits.maxWidth().takeIf { it > 0 } ?: width
        val scale = minOf(1f, maxWidth.toFloat() / width, limits.maxHeight.toFloat() / height)
        val scaledWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (height * scale).roundToInt().coerceAtLeast(1)
        image.setBounds(0, 0, scaledWidth, scaledHeight)
    }

    companion object {
        /**
         * The picture shown for an image that is not available: a grey area of a fixed size.
         */
        fun placeholder(contentId: String, limits: InlineImageLimits): InlineImageSpan {
            val drawable = GradientDrawable().apply {
                setColor(PLACEHOLDER_COLOR)
                setSize(PLACEHOLDER_WIDTH, PLACEHOLDER_HEIGHT)
            }

            return InlineImageSpan(drawable, contentId, limits, isPlaceholder = true)
        }
    }
}
