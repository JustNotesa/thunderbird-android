package com.fsck.k9.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import androidx.annotation.ColorInt
import com.fsck.k9.ui.R
import kotlin.math.roundToInt

/**
 * The account a message was received on, marked by a dot of its colour. It stands in for the account label when the
 * subject is too long to show the label on the lines the subject is collapsed to.
 *
 * The dot is the same size and in the same colour as the dot in front of the name in the label.
 */
internal class AccountDotSpan(
    context: Context,
    @ColorInt accountColor: Int,
) : ReplacementSpan() {
    private val dotSize = context.resources.getDimensionPixelSize(R.dimen.messageViewAccountLabelDotSize)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accountColor }

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        if (fm != null) {
            // The dot is smaller than the subject's line and sits inside it, so the line keeps its height.
            val fontMetrics = paint.fontMetrics
            fm.ascent = fontMetrics.ascent.roundToInt()
            fm.descent = fontMetrics.descent.roundToInt()
        }

        return dotSize
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
        val centreY = lineCentre(y, paint)

        canvas.drawCircle(x + dotSize / 2f, centreY, dotSize / 2f, dotPaint)
    }

    /** The middle of the line of the subject, which the dot is centred on. */
    private fun lineCentre(baseline: Int, paint: Paint): Float {
        val fontMetrics = paint.fontMetrics

        return baseline + (fontMetrics.ascent + fontMetrics.descent) / 2f
    }
}
