package com.fsck.k9.ui.compose.richtext

import android.text.Spannable
import android.text.Spanned
import android.text.style.AlignmentSpan
import net.thunderbird.feature.mail.message.composer.richtext.Alignment
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange

/**
 * Gives the line [line] the paragraph formatting of [paragraph] and leaves every other line as it looks.
 *
 * Before that, the lines around it get one set of spans each again: while the text is edited a span may reach over
 * several lines, and changing one of them must not change the others.
 */
internal fun Spannable.setParagraphSpans(line: IntRange, paragraph: ParagraphRange) {
    val region = spannedRegion(line.first, line.last)
    val lineStarts = lineStartsIn(region)
    val formats = lineStarts.map { lineStart ->
        if (lineStart == line.first) paragraph else paragraphRangeAt(lineStart)
    }

    for (span in paragraphSpans(region.first, region.last)) {
        removeSpan(span)
    }
    lineStarts.zip(formats).forEach { (lineStart, format) -> writeLine(lineStart, format) }
}

/**
 * Takes the paragraph formatting away from the line that starts at [lineStart].
 */
internal fun Spannable.removeParagraphSpans(lineStart: Int) {
    val end = lineEndAt(lineStart)
    setParagraphSpans(lineStart..end, ParagraphRange(lineStart, end))
}

/**
 * The lines from [start] to [end] together with all lines a paragraph span in them reaches over.
 */
private fun Spanned.spannedRegion(start: Int, end: Int): IntRange {
    var regionStart = lineStartAt(start)
    var regionEnd = minOf(lineEndAt(end) + 1, length)
    var grown = true
    while (grown) {
        val spans = paragraphSpans(regionStart, regionEnd)
        val newStart = minOf(regionStart, spans.minOfOrNull { getSpanStart(it) } ?: regionStart)
        val newEnd = maxOf(regionEnd, spans.maxOfOrNull { getSpanEnd(it) } ?: regionEnd)
        grown = newStart != regionStart || newEnd != regionEnd
        regionStart = lineStartAt(newStart)
        regionEnd = newEnd
    }

    return regionStart..regionEnd
}

private fun Spanned.lineStartsIn(region: IntRange): List<Int> {
    val starts = mutableListOf(region.first)
    var position = lineEndAt(region.first)
    // The line that starts where the region ends is not part of it, unless it is the empty one at the end of the text:
    // its spans were not removed, so writing them again would stack them.
    while (position < length && (position + 1 < region.last || position + 1 == length)) {
        starts.add(position + 1)
        position = lineEndAt(position + 1)
    }

    return starts
}

/**
 * Puts the spans of [paragraph] on the line that starts at [lineStart]. They reach over the line break of the line,
 * so that text typed at its end belongs to them; the empty line at the end of the text gets spans over nothing.
 */
private fun Spannable.writeLine(lineStart: Int, paragraph: ParagraphRange) {
    val lineEnd = lineEndAt(lineStart)
    val spanEnd = if (lineEnd < length) lineEnd + 1 else length
    val marginSpan = when (paragraph.block) {
        BlockType.Normal -> if (paragraph.level > 0) IndentSpan(paragraph.level) else null
        BlockType.BulletList -> BulletListSpan(paragraph.level)
        BlockType.NumberedList -> NumberedListSpan(paragraph.level)
        BlockType.Quote -> QuoteBlockSpan(paragraph.level)
    }

    marginSpan?.let { setSpan(it, lineStart, spanEnd, Spanned.SPAN_PARAGRAPH) }
    if (paragraph.alignment != Alignment.Start) {
        setSpan(AlignmentSpan.Standard(paragraph.alignment.toAlign()), lineStart, spanEnd, Spanned.SPAN_PARAGRAPH)
    }
}
