package com.fsck.k9.ui.compose.richtext

import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.URLSpan
import net.thunderbird.feature.mail.message.composer.richtext.ColorKind
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.LinkUrlPolicy
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

/**
 * Applies character formatting to the text of the message composer and reads it back.
 *
 * The formatting is kept as the standard Android spans, so the text field shows it without further help.
 */
internal object RichTextFormatter {
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
     * Gives the selected text or, without a selection, what is typed next the [format] of [group]. `null` removes
     * the formats of the group, which leaves the normal size or the default font.
     */
    fun select(text: Editable, group: TextFormat.Group, format: TextFormat?, selectionStart: Int, selectionEnd: Int) {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)

        for (groupFormat in TextFormat.of(group)) {
            text.removeFormat(groupFormat, start, end)
        }
        if (format != null) {
            text.setSpan(format.createSpan(), start, end, getSpanFlagsFor(start, end))
        }
    }

    /**
     * The format of [group] the whole selection has or, without a selection, text typed at the cursor gets. `null`
     * means the normal size or the default font.
     */
    fun activeFormat(text: Spanned, group: TextFormat.Group, selectionStart: Int, selectionEnd: Int): TextFormat? {
        return TextFormat.of(group).firstOrNull { format -> isActive(text, format, selectionStart, selectionEnd) }
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
     * Removes the character formats, the colours and the links from the selection. Formatting next to the selection
     * stays. Without a selection it removes what would apply to text typed at the cursor.
     */
    fun clearFormatting(text: Editable, selectionStart: Int, selectionEnd: Int) {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)

        for (format in TextFormat.entries) {
            text.removeFormat(format, start, end)
        }
        for (kind in ColorKind.entries) {
            text.removeColor(kind, start, end)
        }
        text.removeLinksIn(start, end)
    }

    /**
     * Builds the text to show in the text field from text and its formatted parts. The pictures of inline images
     * come from [images].
     */
    fun toSpanned(formattedText: FormattedText, images: InlineImages = InlineImages.NONE): Spanned {
        val text = SpannableStringBuilder(formattedText.text)
        for (range in formattedText.ranges) {
            val start = range.start.coerceIn(0, text.length)
            val end = range.end.coerceIn(0, text.length)
            if (start < end) {
                text.setSpan(range.format.createSpan(), start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
            }
        }
        for (range in formattedText.colors) {
            val start = range.start.coerceIn(0, text.length)
            val end = range.end.coerceIn(0, text.length)
            if (start < end) {
                setColorSpan(text, range.kind, range.color, start, end)
            }
        }
        for (range in formattedText.links) {
            val start = range.start.coerceIn(0, text.length)
            val end = range.end.coerceIn(0, text.length)
            // A link the composer refuses must not reach the editor, not even for a moment.
            val url = LinkUrlPolicy.normalize(range.url)
            if (start < end && url != null) {
                text.setSpan(URLSpan(url), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        text.setImageSpans(formattedText.images, images)
        ParagraphFormatter.applyParagraphs(text, formattedText.paragraphs)
        text.keepEndMarker()

        return text
    }

    /**
     * The text of the text field and its formatting as the model of the composer reads it, to save it as a draft.
     * A link the composer refuses is left out; its text stays. The end marker of the empty last line is no text of the
     * message and is left out with everything that belongs to it.
     */
    fun formattedText(text: Spanned): FormattedText {
        return FormattedText(
            text = text.toString(),
            ranges = formattedRanges(text),
            colors = text.colorRanges(),
            links = text.linkRanges(),
            paragraphs = ParagraphFormatter.paragraphs(text),
            images = text.inlineImages(),
        ).withoutEndMarkers()
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

    /**
     * Removes [format] from `start..end`. Formatting next to that range stays, and no longer grows into it.
     */
    private fun Editable.removeFormat(format: TextFormat, start: Int, end: Int) {
        for (span in formatSpans(format)) {
            val spanStart = getSpanStart(span)
            val spanEnd = getSpanEnd(span)
            if (spanEnd < start || spanStart > end) continue

            removeSpan(span)
            val remainingFormats = span.textFormats() - format
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
}
