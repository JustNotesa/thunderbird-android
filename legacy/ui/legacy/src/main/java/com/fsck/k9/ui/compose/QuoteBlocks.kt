package com.fsck.k9.ui.compose

import net.thunderbird.feature.mail.message.composer.richtext.Alignment
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange

private const val BULLET_MARKER = "• "
private const val LIST_INDENT = "  "

/**
 * One line of a text with the paragraph formatting it has. [marker] is what is written in front of the line.
 */
private data class QuoteLine(
    val start: Int,
    val block: BlockType,
    val level: Int,
    val alignment: Alignment,
    val marker: String = "",
)

/**
 * This text, a quoted message, as the lines of a quote block: every line becomes a [BlockType.Quote] line. A line
 * that was a quote already moves one level deeper, so the quote keeps its structure below the new one. A quote block
 * cannot be a list as well, so the items of a list get their bullet or number as text instead of losing it.
 *
 * Empty lines around the text are dropped.
 */
internal fun FormattedText.asQuoteBlock(): FormattedText {
    val window = withoutSurroundingBlankLines()
    val lines = window.quoteLines()
    val markerShift = MarkerShift(lines.map { line -> line.start to line.marker.length })

    val text = buildString {
        window.text.split('\n').forEachIndexed { index, lineText ->
            if (index > 0) append('\n')
            append(lines[index].marker).append(lineText)
        }
    }

    return window.remapped(markerShift::start, markerShift::end)
        .copy(text = text, paragraphs = lines.toParagraphs(text))
}

/**
 * This text with all of its formatting moved [offset] characters towards the end.
 */
internal fun FormattedText.shifted(offset: Int): FormattedText = remapped({ it + offset }, { it + offset })

/**
 * [first], then [separator], then [second], with the formatting of both where their text ends up.
 */
internal fun joinText(first: FormattedText, separator: String, second: FormattedText): FormattedText {
    val moved = second.shifted(first.text.length + separator.length)

    return FormattedText(
        text = first.text + separator + second.text,
        ranges = first.ranges + moved.ranges,
        colors = first.colors + moved.colors,
        links = first.links + moved.links,
        paragraphs = first.paragraphs + moved.paragraphs,
        images = first.images + moved.images,
    )
}

private fun FormattedText.quoteLines(): List<QuoteLine> {
    val numbers = mutableMapOf<Int, Int>()
    var start = 0

    return text.split('\n').map { lineText ->
        val paragraph = paragraphs.firstOrNull { range -> start in range.start..range.end }
        val block = paragraph?.block ?: BlockType.Normal
        val level = paragraph?.level ?: 0
        val line = QuoteLine(
            start = start,
            block = if (block == BlockType.Quote) block else BlockType.Quote,
            level = if (block == BlockType.Quote) level + 1 else 0,
            alignment = paragraph?.alignment ?: Alignment.Start,
            marker = listMarker(block, level, numbers),
        )
        start += lineText.length + 1
        line
    }
}

// Numbers count the items of the list at one level; a line that is not an item of that list starts the count anew.
private fun listMarker(block: BlockType, level: Int, numbers: MutableMap<Int, Int>): String {
    numbers.keys.removeAll { numberedLevel -> numberedLevel > level || block != BlockType.NumberedList }
    val indent = LIST_INDENT.repeat(level)

    return when (block) {
        BlockType.BulletList -> indent + BULLET_MARKER
        BlockType.NumberedList -> {
            val number = (numbers[level] ?: 0) + 1
            numbers[level] = number
            "$indent$number. "
        }
        else -> ""
    }
}

// The lines start where [text], the text with the markers, has them.
private fun List<QuoteLine>.toParagraphs(text: String): List<ParagraphRange> {
    val result = mutableListOf<ParagraphRange>()
    var start = 0

    zip(text.split('\n')).forEach { (line, lineText) ->
        val lineEnd = start + lineText.length
        val previous = result.lastOrNull()
        if (previous != null && previous.level == line.level && previous.alignment == line.alignment) {
            result[result.lastIndex] = previous.copy(end = lineEnd)
        } else {
            result += ParagraphRange(start, lineEnd, line.block, line.level, line.alignment)
        }
        start = lineEnd + 1
    }

    return result
}

private fun FormattedText.withoutSurroundingBlankLines(): FormattedText {
    val from = text.length - text.trimStart('\n').length
    val to = text.trimEnd('\n').length.coerceAtLeast(from)

    return slice(from, to)
}

private fun FormattedText.slice(from: Int, to: Int): FormattedText {
    val kept = copy(
        ranges = ranges.filter { range -> range.end > from && range.start < to },
        colors = colors.filter { range -> range.end > from && range.start < to },
        links = links.filter { range -> range.end > from && range.start < to },
        paragraphs = paragraphs.filter { range -> range.end >= from && range.start <= to },
        images = images.filter { image -> image.position in from until to },
    )

    val move = { position: Int -> position.coerceIn(from, to) - from }

    return kept.copy(text = text.substring(from, to)).remapped(move, move)
}

/** The formatting of this text with every start moved by [start] and every end by [end]; the text stays. */
private fun FormattedText.remapped(start: (Int) -> Int, end: (Int) -> Int): FormattedText {
    return copy(
        ranges = ranges.map { range -> range.copy(start = start(range.start), end = end(range.end)) },
        colors = colors.map { range -> range.copy(start = start(range.start), end = end(range.end)) },
        links = links.map { range -> range.copy(start = start(range.start), end = end(range.end)) },
        paragraphs = paragraphs.map { range -> range.copy(start = start(range.start), end = end(range.end)) },
        images = images.map { image -> image.copy(position = start(image.position)) },
    )
}

/**
 * Where text moves when something is written in front of some of its lines: [markers] are the start of a line and
 * the length of what is written in front of it.
 */
private class MarkerShift(private val markers: List<Pair<Int, Int>>) {
    /** The new position of the character at [position]: after the marker of its line. */
    fun start(position: Int): Int = position + before(position)

    /** The new end of a range that ends before [end]: a marker of the line that follows is not part of it. */
    fun end(end: Int): Int = if (end <= 0) end else end + before(end - 1)

    private fun before(position: Int): Int {
        return markers.filter { (lineStart, _) -> lineStart <= position }.sumOf { (_, length) -> length }
    }
}
