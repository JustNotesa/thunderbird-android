package net.thunderbird.feature.mail.message.composer.internal.richtext

import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.BLOCKQUOTE_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.BR_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.DIV_END
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.LIST_ITEM_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.ParagraphMarkup.listItemStart
import net.thunderbird.feature.mail.message.composer.internal.richtext.ParagraphMarkup.paragraphStart
import net.thunderbird.feature.mail.message.composer.internal.richtext.ParagraphMarkup.quoteStart
import net.thunderbird.feature.mail.message.composer.richtext.Alignment
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange

/**
 * Writes the lines of a message as the block elements the composer uses, so that the recipient sees the lists, the
 * quotes, the indentation and the alignment the user wrote.
 *
 * Every line inside a list or a quote is written on its own between the tags of the block it belongs to: a character
 * tag never crosses the border of a `<li>`, a `<blockquote>` or a `<div>`, so that the reader finds the formatting
 * of a line again where it stands. A paragraph of plain text has no border of its own, so all of its lines are
 * written as one stretch of text.
 *
 * @param text the message text, which [paragraphs] covers
 * @param paragraphs the formatting of its lines, as [normalizeParagraphs] has prepared them
 * @param writeText writes the character formatting of the text between two positions
 */
internal class ParagraphWriter(
    private val text: String,
    paragraphs: List<ParagraphRange>,
    private val writeText: (StringBuilder, Int, Int) -> Unit,
) {
    private val lines = textLines(text).map { ParagraphLine(line = it, paragraph = paragraphOf(it, paragraphs)) }
    private var index = 0
    private var quoteDepth = 0

    fun write(builder: StringBuilder) {
        while (index < lines.size) {
            when {
                lines[index].block.isList() -> writeList(builder, parentLevel = NO_LIST_ABOVE)
                lines[index].block == BlockType.Quote -> writeQuote(builder)
                else -> writeParagraph(builder)
            }
            closeQuotes(builder)
        }
    }

    /**
     * A list is a `<ul>` or an `<ol>` of one `<li>` per line, and a line of a deeper level belongs to a list inside
     * the item above it. A level between the two that has no line of its own is a list of one item that shows no
     * marker, so that the line stands at the depth it is shown at, however deep it stands.
     */
    private fun writeList(builder: StringBuilder, parentLevel: Int) {
        val block = lines[index].block
        val tag = listTagOf(block)
        val level = lines[index].level
        val skipped = level - parentLevel - 1

        repeat(skipped) {
            builder.append("<$tag>")
            builder.append(listItemStart(Alignment.Start, markerless = true))
        }
        builder.append("<$tag>")

        while (index < lines.size && lines[index].block == block && lines[index].level == level) {
            writeItem(builder, level)
        }

        builder.append("</$tag>")
        repeat(skipped) {
            builder.append("</$LIST_ITEM_TAG>")
            builder.append("</$tag>")
        }
    }

    private fun writeItem(builder: StringBuilder, level: Int) {
        builder.append(listItemStart(lines[index].alignment, markerless = false))
        writeItemLine(builder)
        index++

        // A deeper list stands inside the item that leads to it.
        while (index < lines.size && lines[index].block.isList() && lines[index].level > level) {
            writeList(builder, parentLevel = level)
        }

        builder.append("</$LIST_ITEM_TAG>")
    }

    /**
     * A quote block is a `<blockquote>`, one level deeper than the one above it a blockquote inside that one. Its
     * lines are separated by `<br>`, as the lines of a plain paragraph are.
     */
    private fun writeQuote(builder: StringBuilder) {
        val level = lines[index].level
        val end = endOfRun()
        while (quoteDepth <= level) {
            builder.append(quoteStart())
            quoteDepth++
        }

        val start = paragraphStart(lines[index].paragraph)
        if (start.isNotEmpty()) builder.append(start)
        writeLines(builder, index, end)
        if (start.isNotEmpty()) builder.append(DIV_END)
        index = end
    }

    /**
     * A plain paragraph is written as the text of its lines, unless it is indented or placed to one side, which the
     * style of a `<div>` around its lines says. Nothing of the paragraph separates its lines, so the text of the
     * whole paragraph is written as one stretch and a format may reach from one of its lines into the next.
     */
    private fun writeParagraph(builder: StringBuilder) {
        val end = endOfRun()
        val start = paragraphStart(lines[index].paragraph)

        if (start.isEmpty()) {
            writeText(builder, lines[index].line.start, lines[end - 1].line.end)
            // A block starts on a line of its own anyway, so an empty line in front of it needs a break of its own.
            if (end < lines.size && lines[end - 1].line.isEmpty) builder.append("<$BR_TAG>")
        } else {
            builder.append(start)
            writeLines(builder, index, end)
            builder.append(DIV_END)
        }
        index = end
    }

    /**
     * The lines of a block that has borders of its own, each line written on its own and separated by a `<br>`.
     */
    private fun writeLines(builder: StringBuilder, from: Int, to: Int) {
        for (position in from until to) {
            if (position > from) builder.append("<$BR_TAG>")
            val line = lines[position].line
            writeText(builder, line.start, line.end)
        }
    }

    /**
     * An empty line of a list is a `<br>` of its own: a `<li>` that stands for nothing would not be a line at all
     * when it is read back.
     */
    private fun writeItemLine(builder: StringBuilder) {
        val line = lines[index].line
        if (line.isEmpty) builder.append("<$BR_TAG>") else writeText(builder, line.start, line.end)
    }

    /**
     * The end of the run of lines that share the formatting of the line the writer stands at.
     */
    private fun endOfRun(): Int {
        val paragraph = lines[index].paragraph
        var end = index + 1
        while (end < lines.size && sameFormatting(lines[end].paragraph, paragraph)) end++
        return end
    }

    private fun closeQuotes(builder: StringBuilder) {
        while (quoteDepth > 0) {
            builder.append("</$BLOCKQUOTE_TAG>")
            quoteDepth--
        }
    }
}

/**
 * Whether both paragraphs format their lines the same way; a null paragraph is a line without any formatting.
 */
private fun sameFormatting(one: ParagraphRange?, other: ParagraphRange?): Boolean {
    return when {
        one == null || other == null -> one == null && other == null
        else -> one.sameFormatting(other)
    }
}

/**
 * The level a list stands at when no list around it stands above it.
 */
private const val NO_LIST_ABOVE = -1
