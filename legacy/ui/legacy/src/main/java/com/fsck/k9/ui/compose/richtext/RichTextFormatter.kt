package com.fsck.k9.ui.compose.richtext

import android.graphics.Typeface
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

/**
 * Applies character formatting to the text of the message composer and reads it back.
 *
 * The formatting is kept as the standard Android spans, so the text field shows it without further help.
 */
internal object RichTextFormatter {
    private val SPAN_FLAGS_INCLUDING_START = setOf(Spanned.SPAN_INCLUSIVE_INCLUSIVE, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
    private val SPAN_FLAGS_INCLUDING_END = setOf(Spanned.SPAN_INCLUSIVE_INCLUSIVE, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)

    /**
     * Switches [format] on or off for the selected text. Without a selection it changes what is typed next at the
     * cursor.
     */
    fun toggle(text: Editable, format: TextFormat, selectionStart: Int, selectionEnd: Int) {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val wasActive = isActive(text, format, start, end)

        text.removeFormat(format, start, end)
        if (!wasActive) {
            // A span that includes its end keeps growing while the user types at its end. An empty one has to include
            // its start as well, otherwise there is no position at which typing would be part of it.
            val flags = if (start == end) Spanned.SPAN_INCLUSIVE_INCLUSIVE else Spanned.SPAN_EXCLUSIVE_INCLUSIVE
            text.setSpan(format.createSpan(), start, end, flags)
        }
    }

    /**
     * Whether the whole selection has [format], or, without a selection, whether text typed at the cursor gets it.
     */
    fun isActive(text: Spanned, format: TextFormat, selectionStart: Int, selectionEnd: Int): Boolean {
        val start = minOf(selectionStart, selectionEnd)
        val end = maxOf(selectionStart, selectionEnd)

        return if (start == end) {
            text.formatSpans(format).any { span -> text.wouldFormatTextTypedAt(span, start) }
        } else {
            text.formattedRanges(format).any { it.start <= start && end <= it.end }
        }
    }

    /**
     * Builds the text to show in the text field from text and its formatted parts.
     */
    fun toSpanned(formattedText: FormattedText): Spanned {
        val text = SpannableStringBuilder(formattedText.text)
        for (range in formattedText.ranges) {
            val start = range.start.coerceIn(0, text.length)
            val end = range.end.coerceIn(0, text.length)
            if (start < end) {
                text.setSpan(range.format.createSpan(), start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
            }
        }

        return text
    }

    /**
     * The formatted parts of [text], merged so that no two ranges of the same format touch or overlap.
     */
    fun formattedRanges(text: Spanned): List<FormattedRange> {
        return TextFormat.entries.flatMap { format -> text.formattedRanges(format) }
    }

    private fun Spanned.formattedRanges(format: TextFormat): List<FormattedRange> {
        val ranges = formatSpans(format)
            .map { span -> getSpanStart(span) to getSpanEnd(span) }
            .filter { (start, end) -> start < end }
            .sortedBy { (start, _) -> start }

        val merged = mutableListOf<FormattedRange>()
        for ((start, end) in ranges) {
            val last = merged.lastOrNull()
            if (last != null && start <= last.end) {
                merged[merged.lastIndex] = last.copy(end = maxOf(last.end, end))
            } else {
                merged.add(FormattedRange(start, end, format))
            }
        }

        return merged
    }

    private fun Spanned.wouldFormatTextTypedAt(span: CharacterStyle, position: Int): Boolean {
        val spanStart = getSpanStart(span)
        val spanEnd = getSpanEnd(span)
        val pointFlags = getSpanFlags(span) and Spanned.SPAN_POINT_MARK_MASK
        val includesStart = pointFlags in SPAN_FLAGS_INCLUDING_START
        val includesEnd = pointFlags in SPAN_FLAGS_INCLUDING_END

        return when (position) {
            spanEnd -> includesEnd && (spanStart < spanEnd || includesStart)
            spanStart -> includesStart
            else -> position in (spanStart + 1) until spanEnd
        }
    }

    /**
     * Removes [format] from `start..end`. Formatting next to that range stays, and no longer grows into it.
     */
    private fun Editable.removeFormat(format: TextFormat, start: Int, end: Int) {
        for (span in formatSpans(format)) {
            val spanStart = getSpanStart(span)
            val spanEnd = getSpanEnd(span)
            if (spanEnd < start || spanStart > end) continue

            removeSpan(span)
            val remainingFormats = span.formats() - format
            // Put back what the removed span did besides the format being removed, e.g. the italic of a bold italic.
            for (remainingFormat in remainingFormats) {
                setSpan(remainingFormat.createSpan(), spanStart, spanEnd, getSpanFlagsFor(spanStart, spanEnd))
            }
            if (spanStart < start) {
                setSpan(format.createSpan(), spanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (spanEnd > end) {
                setSpan(format.createSpan(), end, spanEnd, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
            }
        }
    }

    private fun getSpanFlagsFor(start: Int, end: Int): Int {
        return if (start == end) Spanned.SPAN_INCLUSIVE_INCLUSIVE else Spanned.SPAN_EXCLUSIVE_INCLUSIVE
    }

    private fun Spanned.formatSpans(format: TextFormat): List<CharacterStyle> {
        return getSpans(0, length, CharacterStyle::class.java)
            // The keyboard underlines the word being typed with a span of its own.
            .filter { span -> getSpanFlags(span) and Spanned.SPAN_COMPOSING == 0 }
            .filter { span -> format in span.formats() }
    }

    private fun CharacterStyle.formats(): Set<TextFormat> {
        return when (this) {
            is StyleSpan -> when (style) {
                Typeface.BOLD -> setOf(TextFormat.Bold)
                Typeface.ITALIC -> setOf(TextFormat.Italic)
                Typeface.BOLD_ITALIC -> setOf(TextFormat.Bold, TextFormat.Italic)
                else -> emptySet()
            }

            is UnderlineSpan -> setOf(TextFormat.Underline)
            else -> emptySet()
        }
    }

    private fun TextFormat.createSpan(): CharacterStyle {
        return when (this) {
            TextFormat.Bold -> StyleSpan(Typeface.BOLD)
            TextFormat.Italic -> StyleSpan(Typeface.ITALIC)
            TextFormat.Underline -> UnderlineSpan()
        }
    }
}
