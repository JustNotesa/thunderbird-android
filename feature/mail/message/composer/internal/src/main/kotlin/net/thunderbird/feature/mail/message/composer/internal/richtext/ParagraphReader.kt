package net.thunderbird.feature.mail.message.composer.internal.richtext

import com.fleeksoft.ksoup.nodes.Element
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.BLOCKQUOTE_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.MARGIN_LEFT_PROPERTY
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.ORDERED_LIST_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.TEXT_ALIGN_PROPERTY
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.UNORDERED_LIST_TAG
import net.thunderbird.feature.mail.message.composer.richtext.Alignment
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange

/**
 * Collects the paragraph formatting of the lines a reader walks through: a list, a quote, an indentation and an
 * alignment say how the lines inside them are formatted, and every line takes the formatting that holds it where
 * it ends.
 */
internal class ParagraphReader {
    private val lines = mutableListOf<ParagraphRange?>()
    private val entered = ArrayDeque<ParagraphState>()
    private var state = ParagraphState()

    // The formatting that held the line being read, as it was when the line got its first text.
    private var lineFormatting: ParagraphState? = null

    /**
     * Remembers the formatting outside [element] and takes on the formatting it adds, for the lines inside it.
     */
    fun enter(element: Element, tag: String) {
        entered.addLast(state)
        state = state.enteredBy(element, tag)
    }

    fun leave() {
        state = entered.removeLast()
    }

    /**
     * The line that is being read takes the formatting that holds it now.
     */
    fun startLine() {
        if (lineFormatting == null) lineFormatting = state
    }

    /**
     * Records the line between [start] and [end] with the formatting that held it, or as unformatted when nothing
     * held it.
     */
    fun addLine(start: Int, end: Int) {
        lines.add(lineFormatting?.paragraph(start = start, end = end))
        lineFormatting = null
    }

    /**
     * The formatting of the lines of [text], merged as the model keeps it.
     */
    fun collect(text: String): List<ParagraphRange> {
        return normalizeParagraphs(text, lines.filterNotNull())
    }
}

/**
 * The formatting that holds the lines the reader stands in: what the innermost list or quote makes of them, how far
 * a paragraph is indented and where it is placed. A list or a quote is one level deeper than the ones around it,
 * however deep the nesting is. A list item that shows no marker is the item of a level that has no line of its own,
 * so that the list inside it stands at the level the line it holds is written at.
 */
private data class ParagraphState(
    val block: BlockType = BlockType.Normal,
    val level: Int = 0,
    val alignment: Alignment = Alignment.Start,
    val depth: Int = 0,
    val quoteDepth: Int = 0,
) {
    fun enteredBy(element: Element, tag: String): ParagraphState {
        return when (tag) {
            UNORDERED_LIST_TAG -> copy(
                block = BlockType.BulletList,
                level = depth.coerceAtMost(ParagraphRange.MAX_LEVEL),
                depth = depth + 1,
            )

            ORDERED_LIST_TAG -> copy(
                block = BlockType.NumberedList,
                level = depth.coerceAtMost(ParagraphRange.MAX_LEVEL),
                depth = depth + 1,
            )

            BLOCKQUOTE_TAG -> copy(
                block = BlockType.Quote,
                level = quoteDepth.coerceAtMost(ParagraphRange.MAX_LEVEL),
                quoteDepth = quoteDepth + 1,
            )

            else -> withStyleOf(element)
        }
    }

    /**
     * What the style of the element says about its lines: where they are placed, and how far a plain paragraph is
     * indented. A style of a list or a quote belongs to its nesting, not to the lines inside it.
     */
    private fun withStyleOf(element: Element): ParagraphState {
        val alignment = alignmentOf(styleOf(element, TEXT_ALIGN_PROPERTY))
        val indent = levelOf(styleOf(element, MARGIN_LEFT_PROPERTY))
        return when {
            alignment != null && indent != null && block == BlockType.Normal ->
                copy(alignment = alignment, level = indent)

            alignment != null -> copy(alignment = alignment)

            indent != null && block == BlockType.Normal -> copy(level = indent)

            else -> this
        }
    }

    fun paragraph(start: Int, end: Int): ParagraphRange? {
        val range = ParagraphRange(start = start, end = end, block = block, level = level, alignment = alignment)
        return range.takeUnless { it.isDefault() }
    }
}
