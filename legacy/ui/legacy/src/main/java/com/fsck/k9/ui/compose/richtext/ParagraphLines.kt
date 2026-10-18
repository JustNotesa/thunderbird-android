package com.fsck.k9.ui.compose.richtext

import android.text.Layout
import android.text.Spanned
import android.text.style.AlignmentSpan
import android.text.style.LeadingMarginSpan
import android.text.style.ParagraphStyle
import net.thunderbird.feature.mail.message.composer.richtext.Alignment
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange

/**
 * The start of the line that contains [position].
 */
internal fun Spanned.lineStartAt(position: Int): Int {
    val at = position.coerceIn(0, length)

    return toString().lastIndexOf('\n', at - 1) + 1
}

/**
 * The end of the line that contains [position], before its line break.
 */
internal fun Spanned.lineEndAt(position: Int): Int {
    val lineBreak = toString().indexOf('\n', position.coerceIn(0, length))

    return if (lineBreak < 0) length else lineBreak
}

/**
 * The lines the selection touches, as ranges without their line break. A selection that ends at the start of a line
 * doesn't count that line as touched.
 */
internal fun Spanned.lineRanges(selectionStart: Int, selectionEnd: Int): List<IntRange> {
    val start = minOf(selectionStart, selectionEnd).coerceIn(0, length)
    val end = maxOf(selectionStart, selectionEnd).coerceIn(0, length)
    val lastLineStart = if (end > start) lineStartAt(end - 1) else lineStartAt(start)

    return linesFrom(lineStartAt(start), lastLineStart)
}

/**
 * The lines a paragraph range of the model covers, as ranges without their line break: from the line that holds its
 * start to the line that holds its end.
 *
 * A range that ends where a line begins covers that line too - the empty line at the end of the text, for which the
 * model has no line break of its own. A range that ends at the end of a line doesn't reach into the line behind it:
 * that one starts behind the line break, at end + 1.
 */
internal fun Spanned.paragraphLineRanges(start: Int, end: Int): List<IntRange> {
    val from = start.coerceIn(0, length)
    val to = end.coerceIn(from, length)

    return linesFrom(lineStartAt(from), lineStartAt(to))
}

/**
 * The lines from the one that starts at [firstLineStart] to the one that starts at [lastLineStart].
 */
private fun Spanned.linesFrom(firstLineStart: Int, lastLineStart: Int): List<IntRange> {
    val lines = mutableListOf<IntRange>()
    var lineStart = firstLineStart
    while (true) {
        val lineEnd = lineEndAt(lineStart)
        lines.add(lineStart..lineEnd)

        val nextLineStart = lineEnd + 1
        if (lineStart >= lastLineStart || nextLineStart > length) break
        lineStart = nextLineStart
    }

    return lines
}

/**
 * The paragraph spans of the composer that format the line starting at [lineStart]. While the text is edited, a span
 * may reach over several lines for a moment; a line belongs to the span that covers its start and starts last.
 */
internal fun Spanned.paragraphSpansAt(lineStart: Int): List<ParagraphStyle> {
    val position = lineStart.coerceIn(0, length)
    val covering = paragraphSpans(position, minOf(position + 1, length)).filter { span -> covers(span, position) }
    val margin = covering.filterIsInstance<LeadingMarginSpan>().maxByOrNull { span -> getSpanStart(span) }
    val alignment = covering.filterIsInstance<AlignmentSpan.Standard>().maxByOrNull { span -> getSpanStart(span) }

    return listOfNotNull(margin, alignment)
}

/**
 * Whether [span] formats the line that starts at [position]. A span over nothing formats only its own position: the
 * empty line at the end of the text.
 */
private fun Spanned.covers(span: Any, position: Int): Boolean {
    val start = getSpanStart(span)
    val end = getSpanEnd(span)

    return start <= position && (position < end || start == end)
}

/**
 * All paragraph spans of the composer in `start..end`.
 */
internal fun Spanned.paragraphSpans(start: Int, end: Int): List<ParagraphStyle> {
    val from = start.coerceIn(0, length)
    val to = end.coerceIn(from, length)

    val margins = getSpans(from, to, LeadingMarginSpan::class.java).filter { it.isComposerParagraphSpan() }
    val alignments = getSpans(from, to, AlignmentSpan.Standard::class.java).toList()

    return margins + alignments
}

internal fun LeadingMarginSpan.isComposerParagraphSpan(): Boolean {
    return this is BulletListSpan || this is NumberedListSpan || this is QuoteBlockSpan || this is IndentSpan
}

/**
 * The paragraph formatting of the line that starts at [lineStart], [BlockType.Normal] and no indent when it has none.
 */
internal fun Spanned.paragraphRangeAt(lineStart: Int): ParagraphRange {
    val spans = paragraphSpansAt(lineStart)
    val margin = spans.filterIsInstance<LeadingMarginSpan>().firstOrNull()
    val alignment = spans.filterIsInstance<AlignmentSpan.Standard>().firstOrNull()

    return ParagraphRange(
        start = lineStart,
        end = lineEndAt(lineStart),
        block = margin.blockType(),
        level = margin.indentLevel(),
        alignment = alignment.alignment(),
    )
}
