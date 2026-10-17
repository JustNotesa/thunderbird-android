package net.thunderbird.feature.mail.message.composer.internal.richtext

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.ANCHOR_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.BLOCK_ELEMENTS
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.BR_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.CONTENT_ID_PREFIX
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.DROPPED_TAGS
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.HREF_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.IMAGE_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.NON_BREAKING_SPACE_CHARACTER
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.OBJECT_REPLACEMENT_CHARACTER
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.PRE_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.SOURCE_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.TABLE_CELL_TAGS
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.TABLE_TAGS
import net.thunderbird.feature.mail.message.composer.richtext.ColorRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.InlineImage
import net.thunderbird.feature.mail.message.composer.richtext.LinkRange
import net.thunderbird.feature.mail.message.composer.richtext.LinkUrlPolicy
import net.thunderbird.feature.mail.message.composer.richtext.RichTextHtmlConverter.LenientResult
import net.thunderbird.feature.mail.message.composer.richtext.RichTextHtmlConverter.LostContent

/**
 * Walks the HTML of a message and collects the model out of it. The same walk serves both readers; [lenient] lets
 * it survive HTML the composer did not write itself, keeping the text and reporting what it had to drop.
 */
internal class HtmlReader(private val lenient: Boolean) {
    private val text = StringBuilder()
    private val ranges = mutableListOf<FormattedRange>()
    private val colors = mutableListOf<ColorRange>()
    private val links = mutableListOf<LinkRange>()
    private val paragraphs = ParagraphReader()
    private val images = mutableListOf<InlineImage>()
    private val lost = mutableSetOf<LostContent>()
    private var insidePre = false
    private var lineStart = 0

    // A block element ends the line it stands on; the text that follows it stands on a line of its own.
    private var blockEnded = false

    fun read(html: String): LenientResult {
        appendNodes(Ksoup.parseBodyFragment(html).body().childNodes())
        finishLine()

        val wholeText = text.toString().trimmed()

        return LenientResult(
            formattedText = FormattedText(
                text = wholeText,
                ranges = mergeRanges(ranges),
                colors = mergeColors(colors),
                links = mergeLinks(links),
                paragraphs = paragraphs.collect(wholeText),
                images = images.sortedBy { it.position },
            ),
            lost = lost,
        )
    }

    private fun String.trimmed(): String {
        return if (lenient) trimEnd { it == ' ' || it == '\n' } else this
    }

    private fun appendNodes(nodes: List<Node>) {
        for (node in nodes) {
            when (node) {
                is TextNode -> appendText(node.getWholeText())
                is Element -> appendElement(node)
                else -> Unit
            }
        }
    }

    /**
     * The text of a run of characters, as HTML shows it: whitespace is one space, and not at all at the start of a
     * line. Text that follows a block stands on a line of its own; text that only continues the line it is on does
     * not, however many tags it passes on its way.
     */
    private fun appendText(chunk: String) {
        if (!lenient) {
            if (blockEnded) breakBeforeBlock()
            paragraphs.startLine()
            text.append(chunk.replace(NON_BREAKING_SPACE_CHARACTER, ' '))
            return
        }

        for (character in chunk) {
            when {
                !character.isWhitespace() -> {
                    if (blockEnded) breakBeforeBlock()
                    paragraphs.startLine()
                    text.append(character)
                }

                insidePre -> text.append(if (character == NON_BREAKING_SPACE_CHARACTER) ' ' else character)

                text.isNotEmpty() && !text.endsWith('\n') && !text.endsWith(' ') -> text.append(' ')
            }
        }
    }

    /**
     * A list, a quote and a block of plain text each stand for lines of their own, which the paragraphs of the model
     * say as much about as the character formats of the text inside them say about its words.
     */
    private fun appendElement(element: Element) {
        val tag = element.normalName()

        // What a script or a style file says is not text of the message.
        if (tag in DROPPED_TAGS) return

        if (tag == BR_TAG) {
            // A break right after a block is an empty line: the block ends its own line first.
            if (blockEnded) breakBeforeBlock()
            appendNewLine()
            return
        }

        if (lenient) HtmlLoss.note(element, tag, lost)
        if (blockEnded || startsNewLine(tag)) breakBeforeBlock()

        val start = text.length
        val link = if (tag == ANCHOR_TAG) LinkUrlPolicy.normalize(element.attr(HREF_ATTRIBUTE)) else null
        val wasInsidePre = insidePre
        insidePre = wasInsidePre || tag == PRE_TAG
        paragraphs.enter(element, tag)

        if (tag == IMAGE_TAG) readImage(element, start) else appendNodes(element.childNodes())

        paragraphs.leave()
        insidePre = wasInsidePre
        blockEnded = tag in BLOCK_ELEMENTS

        val end = text.length
        if (end > start) {
            for (entry in HtmlStyles.entriesOf(element, tag, headings = lenient)) {
                when (entry) {
                    is FormatStyle -> ranges.add(FormattedRange(start = start, end = end, format = entry.format))

                    is ColorStyle ->
                        colors.add(ColorRange(start = start, end = end, kind = entry.kind, color = entry.color))
                }
            }
            if (link != null) links.add(LinkRange(start = start, end = end, url = link))
        }
    }

    private fun readImage(element: Element, position: Int) {
        val source = element.attr(SOURCE_ATTRIBUTE)
        val contentId = source.removePrefix(CONTENT_ID_PREFIX)
        if (!source.startsWith(CONTENT_ID_PREFIX) || contentId.isEmpty()) return

        paragraphs.startLine()
        text.append(OBJECT_REPLACEMENT_CHARACTER)
        images.add(InlineImage(position = position, contentId = contentId))
    }

    private fun startsNewLine(tag: String): Boolean {
        return tag in BLOCK_ELEMENTS || (lenient && (tag in TABLE_TAGS || tag in TABLE_CELL_TAGS))
    }

    /**
     * What stands behind a block of lines stands on a line of its own. The break waits for the text that follows, so
     * that a message that ends with a block does not end with a line that is not there.
     */
    private fun breakBeforeBlock() {
        // A space before the break is a space HTML would not show.
        if (lenient && text.endsWith(' ')) text.setLength(text.length - 1)
        if (text.isEmpty() || text.endsWith('\n')) return

        endLine()
        text.append('\n')
        lineStart = text.length
        blockEnded = false
    }

    private fun appendNewLine() {
        // A space before the break is a space HTML would not show.
        if (lenient && text.endsWith(' ')) text.setLength(text.length - 1)

        paragraphs.startLine()
        endLine()
        text.append('\n')
        lineStart = text.length
        blockEnded = false
    }

    /**
     * The line that ends here is the one between the last line break and this place.
     */
    private fun endLine() {
        paragraphs.addLine(start = lineStart, end = text.length)
    }

    /**
     * The last line of the message is the one that no line break ends.
     */
    private fun finishLine() {
        if (text.isNotEmpty() && !text.endsWith('\n')) paragraphs.addLine(start = lineStart, end = text.length)
    }
}
