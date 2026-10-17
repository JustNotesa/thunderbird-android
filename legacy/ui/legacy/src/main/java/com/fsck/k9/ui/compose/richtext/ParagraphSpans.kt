package com.fsck.k9.ui.compose.richtext

import android.graphics.Canvas
import android.graphics.Paint
import android.content.res.Resources
import android.text.Layout
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.ParagraphStyle
import kotlin.math.roundToInt

// One indentation step, the indent the composer indents by.
private const val INDENT_DP = 24

private const val BULLET_RADIUS_DP = 3f
private const val BULLET_STROKE_DP = 1.5f
private const val NUMBER_TEXT_SIZE_SP = 14f
private const val QUOTE_BAR_WIDTH_DP = 2f
private const val QUOTE_BAR_GAP_DP = 3f
private const val NUMBER_GAP_DP = 6f

private const val BULLET_COLOR = 0xFF000000.toInt()
private const val QUOTE_BAR_COLOR = 0xFFCCCCCC.toInt()

internal val DEFAULT_DENSITY: Float = Resources.getSystem().displayMetrics.density

/**
 * The indentation a line of nesting [level] has, in pixels.
 */
internal fun indentInPixels(level: Int, density: Float): Int {
    return ((level + 1) * INDENT_DP * density).roundToInt()
}

/**
 * One indentation step in pixels.
 */
internal fun indentStepInPixels(density: Float): Float = INDENT_DP * density

/**
 * The vertical middle of the line [baseline] is drawn on.
 */
internal fun lineCenter(baseline: Int, paragraphPaint: Paint): Float {
    // The ascent is negative: the middle of the letters lies above the baseline.
    return baseline + (paragraphPaint.descent() + paragraphPaint.ascent()) / 2f
}

/**
 * A line of a bulleted list on nesting [level].
 */
internal class BulletListSpan(
    val level: Int,
    private val density: Float = DEFAULT_DENSITY,
) : LeadingMarginSpan, ParagraphStyle {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BULLET_COLOR
        strokeWidth = BULLET_STROKE_DP * density
    }

    override fun getLeadingMargin(first: Boolean): Int {
        return indentInPixels(level, density)
    }

    override fun drawLeadingMargin(
        canvas: Canvas,
        paragraphPaint: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout?,
    ) {
        // The wrapped lines of the item are indented only, so the bullet belongs to the first line of the range.
        if (!first || (text as? Spanned)?.getSpanStart(this) != start) return

        // The bullet stands in the middle of the last indentation step, in front of the text.
        val radius = BULLET_RADIUS_DP * density
        val step = indentStepInPixels(density)
        paint.style = if (level % 2 == 0) Paint.Style.FILL else Paint.Style.STROKE
        canvas.drawCircle(x + dir * (level * step + step / 2), lineCenter(baseline, paragraphPaint), radius, paint)
    }
}

/**
 * A line of a numbered list on nesting [level]. Its number is counted while drawing, so that inserting or removing a
 * line renumbers the whole list.
 */
internal class NumberedListSpan(
    val level: Int,
    private val density: Float = DEFAULT_DENSITY,
) : LeadingMarginSpan, ParagraphStyle {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BULLET_COLOR
        textSize = NUMBER_TEXT_SIZE_SP * density
    }

    override fun getLeadingMargin(first: Boolean): Int {
        return indentInPixels(level, density)
    }

    override fun drawLeadingMargin(
        canvas: Canvas,
        paragraphPaint: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout?,
    ) {
        // The wrapped lines of the item are indented only, so the number belongs to the first line of the range.
        if (!first || (text as? Spanned)?.getSpanStart(this) != start) return

        // The number ends just in front of the text, so that the numbers of a list line up at their dot.
        val step = indentStepInPixels(density)
        paint.textAlign = if (dir > 0) Paint.Align.RIGHT else Paint.Align.LEFT
        val numberEnd = x + dir * ((level + 1) * step - NUMBER_GAP_DP * density)
        canvas.drawText("${itemNumber(text)}.", numberEnd, baseline.toFloat(), paint)
    }

    private fun itemNumber(text: CharSequence): Int {
        val spanned = text as? Spanned ?: return 1
        val start = spanned.getSpanStart(this)
        return if (start >= 0) spanned.numberedItemNumber(start, level) else 1
    }
}

/**
 * A line of a quote block on nesting [level].
 */
internal class QuoteBlockSpan(
    val level: Int,
    private val density: Float = DEFAULT_DENSITY,
) : LeadingMarginSpan, ParagraphStyle {

    private val paint = Paint().apply {
        color = QUOTE_BAR_COLOR
        strokeWidth = QUOTE_BAR_WIDTH_DP * density
    }

    override fun getLeadingMargin(first: Boolean): Int {
        return indentInPixels(level, density)
    }

    override fun drawLeadingMargin(
        canvas: Canvas,
        paragraphPaint: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout?,
    ) {
        // Every level of the quote has a bar of its own, at the start of its indentation step.
        val step = indentStepInPixels(density)
        for (barLevel in 0..level) {
            val barX = x + dir * (barLevel * step + QUOTE_BAR_GAP_DP * density)
            canvas.drawLine(barX, top.toFloat(), barX, bottom.toFloat(), paint)
        }
    }
}

/**
 * A normal line indented by [level].
 */
internal class IndentSpan(
    val level: Int,
    private val density: Float = DEFAULT_DENSITY,
) : LeadingMarginSpan, ParagraphStyle {

    override fun getLeadingMargin(first: Boolean): Int {
        return (level * INDENT_DP * density).roundToInt()
    }

    // An indent shifts the text to the right, there is nothing to draw in front of it.
    override fun drawLeadingMargin(
        canvas: Canvas,
        paragraphPaint: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout?,
    ) = Unit
}
