package net.thunderbird.feature.mail.message.composer.internal.richtext

import net.thunderbird.feature.mail.message.composer.richtext.Alignment
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange

/**
 * A line of the message text: the text between two line breaks. [end] is the position of the line break or the end
 * of the text; the break itself does not belong to the line.
 */
internal data class TextLine(val start: Int, val end: Int) {
    val isEmpty: Boolean
        get() = start == end
}

/**
 * A line of the text with the paragraph formatting that holds it. [paragraph] is null for a line that has none.
 */
internal data class ParagraphLine(val line: TextLine, val paragraph: ParagraphRange?) {
    val block: BlockType
        get() = paragraph?.block ?: BlockType.Normal

    val level: Int
        get() = paragraph?.level ?: 0

    val alignment: Alignment
        get() = paragraph?.alignment ?: Alignment.Start
}

/**
 * The lines of [text] in the order they stand in it. An empty line is a line like every other one, so that it can
 * be part of a list or of a quote.
 */
internal fun textLines(text: String): List<TextLine> {
    val lines = mutableListOf<TextLine>()
    var start = 0

    for (index in text.indices) {
        if (text[index] == LINE_BREAK) {
            lines.add(TextLine(start = start, end = index))
            start = index + 1
        }
    }
    lines.add(TextLine(start = start, end = text.length))

    return lines
}

/**
 * The formatting [paragraph] gives to [line]. A paragraph stands for whole lines, so a range that begins or ends
 * inside one of them holds that whole line; the range that begins last wins, because it is the innermost one.
 */
internal fun paragraphOf(line: TextLine, paragraphs: List<ParagraphRange>): ParagraphRange? {
    return paragraphs.filter { it.start <= line.end && line.start <= it.end }.maxWithOrNull(ORDER)
}

private val ORDER = compareBy<ParagraphRange>({ it.start }, { it.end })

/**
 * One [ParagraphRange] per run of lines that share their formatting: every range is clipped to the text and moved
 * to the borders of the lines it touches, a range that formats a line the way an unformatted line is formatted is
 * dropped, and lines that share their formatting are merged into one range.
 *
 * The writer uses it for the paragraphs it is given and the reader for the ones it found, so that both sides of the
 * conversion work with whole lines and without ranges of their own that only differ in how they were written.
 */
internal fun normalizeParagraphs(text: String, paragraphs: List<ParagraphRange>): List<ParagraphRange> {
    val normalized = mutableListOf<ParagraphRange>()

    for (line in textLines(text)) {
        val formatting = paragraphOf(line, paragraphs)?.takeUnless { it.isDefault() }
        val last = normalized.lastOrNull()
        if (formatting == null) continue

        if (last != null && last.end + 1 == line.start && last.sameFormatting(formatting)) {
            normalized[normalized.lastIndex] = last.copy(end = line.end)
        } else {
            normalized.add(formatting.copy(start = line.start, end = line.end))
        }
    }

    return normalized
}

/**
 * Whether this is a list, whose level is the depth of the nesting the HTML shows.
 */
internal fun BlockType.isList(): Boolean {
    return this == BlockType.BulletList || this == BlockType.NumberedList
}

/**
 * Whether this is the formatting a line without an entry in the paragraphs of the text has.
 */
internal fun ParagraphRange.isDefault(): Boolean {
    return block == BlockType.Normal && level == 0 && alignment == Alignment.Start
}

/**
 * Whether both ranges format their lines the same way, however different the lines are.
 */
internal fun ParagraphRange.sameFormatting(other: ParagraphRange): Boolean {
    return block == other.block && level == other.level && alignment == other.alignment
}

internal const val LINE_BREAK = '\n'
