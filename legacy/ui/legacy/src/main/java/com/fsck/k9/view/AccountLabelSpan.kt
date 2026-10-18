package com.fsck.k9.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import androidx.annotation.ColorInt
import com.fsck.k9.ui.R
import com.google.android.material.R as MaterialR
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The account a message belongs to, drawn as a small pill at the end of the subject line instead of on a row of its
 * own.
 *
 * The account colour appears as a dot in front of the account name; the name itself is written in the colour of the
 * surrounding text, so that it stays a label and does not become a badge.
 */
internal class AccountLabelSpan(
    context: Context,
    @ColorInt accountColor: Int,
) : ReplacementSpan() {
    private val resources = context.resources
    private val gapBefore = resources.getDimensionPixelSize(R.dimen.messageViewAccountLabelGapBefore)
    private val horizontalPadding = resources.getDimensionPixelSize(R.dimen.messageViewAccountLabelHorizontalPadding)
    private val verticalPadding = resources.getDimensionPixelSize(R.dimen.messageViewAccountLabelVerticalPadding)
    private val dotSize = resources.getDimensionPixelSize(R.dimen.messageViewAccountLabelDotSize)
    private val dotGap = resources.getDimensionPixelSize(R.dimen.messageViewAccountLabelDotGap)

    private val labelPaint = Paint().apply {
        isAntiAlias = true
        textSize = resources.getDimension(R.dimen.messageViewAccountLabelTextSize)
        color = ThemeUtils.getStyledColor(context, MaterialR.attr.colorOnSurfaceVariant)
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ThemeUtils.getStyledColor(context, MaterialR.attr.colorSurfaceContainerHigh)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accountColor }

    private val labelMetrics = labelPaint.fontMetrics
    private val labelContentHeight = max(labelMetrics.descent - labelMetrics.ascent, dotSize.toFloat())
    private val labelHeight = labelContentHeight.roundToInt() + 2 * verticalPadding

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        if (fm != null) {
            // The pill is smaller than the subject's line and sits inside it, so the line keeps its height.
            val fontMetrics = paint.fontMetrics
            fm.ascent = fontMetrics.ascent.roundToInt()
            fm.descent = fontMetrics.descent.roundToInt()
        }

        return gapBefore + pillWidth(text, start, end)
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
        val pillLeft = x + gapBefore
        val pillTop = centreY - labelHeight / 2f

        canvas.drawRoundRect(
            pillLeft,
            pillTop,
            pillLeft + pillWidth(text, start, end),
            pillTop + labelHeight,
            labelHeight / 2f,
            labelHeight / 2f,
            pillPaint,
        )
        canvas.drawCircle(pillLeft + horizontalPadding + dotSize / 2f, centreY, dotSize / 2f, dotPaint)

        val textBaseline = centreY - (labelMetrics.ascent + labelMetrics.descent) / 2f
        val textLeft = pillLeft + horizontalPadding + dotSize + dotGap
        canvas.drawText(text, start, end, textLeft, textBaseline, labelPaint)
    }

    /** The width of the pill: the name in the label's size, the dot in front of it and the padding around both. */
    private fun pillWidth(text: CharSequence?, start: Int, end: Int): Int {
        val textWidth = labelPaint.measureText(text ?: "", start, end).roundToInt()

        return horizontalPadding + dotSize + dotGap + textWidth + horizontalPadding
    }

    /** The middle of the line of the subject, which the pill is centred on. */
    private fun lineCentre(baseline: Int, paint: Paint): Float {
        val fontMetrics = paint.fontMetrics

        return baseline + (fontMetrics.ascent + fontMetrics.descent) / 2f
    }
}
