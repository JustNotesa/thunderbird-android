package com.fsck.k9.ui.compose.richtext

import android.text.Editable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import net.thunderbird.feature.mail.message.composer.richtext.ColorKind
import net.thunderbird.feature.mail.message.composer.richtext.ColorRange

// The colours of the model have no alpha; the spans need one.
private const val COLOR_OPAQUE = 0xFF000000.toInt()
private const val COLOR_WITHOUT_ALPHA = 0xFFFFFF

/**
 * Gives the text of the message composer a text or highlight colour and reads it back.
 *
 * The colour is kept as the standard Android spans, so the text field shows it without further help.
 */
internal object ColorFormatter {
    /**
     * Gives the selected text or, without a selection, what is typed next the [kind] colour [color], which is 0xRRGGBB.
     * `null` removes the colour of that kind again.
     */
    fun setColor(text: Editable, kind: ColorKind, color: Int?, selectionStart: Int, selectionEnd: Int) {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)

        text.removeColor(kind, start, end)
        if (color != null) {
            setColorSpan(text, kind, color, start, end)
        }
    }

    /**
     * The colour the whole selection has or, without a selection, text typed at the cursor gets, as 0xRRGGBB. `null`
     * when there is none, or when the selection does not have it all the same.
     */
    fun activeColor(text: Spanned, kind: ColorKind, selectionStart: Int, selectionEnd: Int): Int? {
        val start = minOf(selectionStart, selectionEnd)
        val end = maxOf(selectionStart, selectionEnd)

        return if (start == end) {
            text.colorSpans(kind)
                .firstOrNull { span -> text.wouldFormatTextTypedAt(span, start) }
                ?.colorWithoutAlpha()
        } else {
            text.colorSpans(kind)
                .filter { span -> text.getSpanStart(span) <= start && end <= text.getSpanEnd(span) }
                .map { span -> span.colorWithoutAlpha() }
                .distinct()
                .singleOrNull()
        }
    }
}

/**
 * Puts the [kind] colour [color], which is 0xRRGGBB, on `start..end`.
 */
internal fun setColorSpan(text: Editable, kind: ColorKind, color: Int, start: Int, end: Int) {
    text.setSpan(createColorSpan(kind, COLOR_OPAQUE or color), start, end, getSpanFlagsFor(start, end))
}

/**
 * Removes the [kind] colour from `start..end`. The colour of the text next to that range stays.
 */
internal fun Editable.removeColor(kind: ColorKind, start: Int, end: Int) {
    for (span in colorSpans(kind)) {
        val spanStart = getSpanStart(span)
        val spanEnd = getSpanEnd(span)
        if (spanEnd < start || spanStart > end) continue

        removeSpan(span)
        if (spanStart < start) {
            setSpan(createColorSpan(kind, span.colorValue()), spanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (spanEnd > end) {
            setSpan(createColorSpan(kind, span.colorValue()), end, spanEnd, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
        }
    }
}

/**
 * The colours of the text, merged so that no two ranges of the same kind and colour touch or overlap.
 */
internal fun Spanned.colorRanges(): List<ColorRange> {
    val ranges = ColorKind.entries
        .flatMap { kind ->
            colorSpans(kind).map { span ->
                ColorRange(
                    start = getSpanStart(span),
                    end = getSpanEnd(span),
                    kind = kind,
                    color = span.colorWithoutAlpha(),
                )
            }
        }
        .filter { range -> range.start < range.end }

    // Merged per kind and colour: ranges of another kind or colour may lie between two that touch.
    return ranges
        .groupBy { range -> range.kind to range.color }
        .values
        .flatMap { group -> group.sortedBy { range -> range.start }.mergedTouching() }
        .sortedWith(compareBy({ it.start }, { it.kind }, { it.end }))
}

private fun List<ColorRange>.mergedTouching(): List<ColorRange> {
    val merged = mutableListOf<ColorRange>()
    for (range in this) {
        val last = merged.lastOrNull()
        if (last != null && range.start <= last.end) {
            merged[merged.lastIndex] = last.copy(end = maxOf(last.end, range.end))
        } else {
            merged.add(range)
        }
    }

    return merged
}

internal fun Spanned.colorSpans(kind: ColorKind): List<CharacterStyle> {
    return when (kind) {
        ColorKind.Text -> getSpans(0, length, ForegroundColorSpan::class.java)
        ColorKind.Highlight -> getSpans(0, length, BackgroundColorSpan::class.java)
    }.filter { span -> !isComposingSpan(span) }
}

internal fun createColorSpan(kind: ColorKind, color: Int): CharacterStyle {
    return when (kind) {
        ColorKind.Text -> ForegroundColorSpan(color)
        ColorKind.Highlight -> BackgroundColorSpan(color)
    }
}

internal fun CharacterStyle.colorValue(): Int {
    return when (this) {
        is ForegroundColorSpan -> foregroundColor
        is BackgroundColorSpan -> backgroundColor
        else -> 0
    }
}

internal fun CharacterStyle.colorWithoutAlpha(): Int {
    return colorValue() and COLOR_WITHOUT_ALPHA
}
