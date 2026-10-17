package net.thunderbird.feature.mail.message.composer.internal.richtext

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.ANCHOR_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.BLOCK_TAGS
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.BR_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.CONTENT_ID_PREFIX
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.DROPPED_TAGS
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.FONT_COLOR_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.FONT_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.FORMAT_BY_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.HREF_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.IMAGE_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.KNOWN_TAGS
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.LENIENT_BLOCK_TAGS
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.NON_BREAKING_SPACE_CHARACTER
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.OBJECT_REPLACEMENT_CHARACTER
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.PRE_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.SOURCE_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.STYLE_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.TABLE_CELL_TAGS
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.TABLE_TAGS
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.modelledAttributes
import net.thunderbird.feature.mail.message.composer.richtext.ColorKind
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
    private val images = mutableListOf<InlineImage>()
    private val lost = mutableSetOf<LostContent>()
    private var insidePre = false

    fun read(html: String): LenientResult {
        appendNodes(Ksoup.parseBodyFragment(html).body().childNodes())

        return LenientResult(
            formattedText = FormattedText(
                text = text.toString().trimmed(),
                ranges = mergeRanges(ranges),
                colors = mergeColors(colors),
                links = mergeLinks(links),
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
     * line.
     */
    private fun appendText(chunk: String) {
        if (!lenient) {
            text.append(chunk.replace(NON_BREAKING_SPACE_CHARACTER, ' '))
            return
        }

        for (character in chunk) {
            when {
                !character.isWhitespace() -> text.append(character)
                insidePre -> text.append(if (character == NON_BREAKING_SPACE_CHARACTER) ' ' else character)
                text.isNotEmpty() && !text.endsWith('\n') && !text.endsWith(' ') -> text.append(' ')
            }
        }
    }

    private fun appendElement(element: Element) {
        val tag = element.normalName()

        // What a script or a style file says is not text of the message.
        if (tag in DROPPED_TAGS) return

        if (tag == BR_TAG) {
            appendNewLine()
            return
        }

        if (lenient) noteLostContent(element, tag)
        if (startsNewLine(tag)) appendNewLine()

        val start = text.length
        val link = if (tag == ANCHOR_TAG) LinkUrlPolicy.normalize(element.attr(HREF_ATTRIBUTE)) else null
        val wasInsidePre = insidePre
        insidePre = wasInsidePre || tag == PRE_TAG

        if (tag == IMAGE_TAG) readImage(element, start) else appendNodes(element.childNodes())

        insidePre = wasInsidePre

        val end = text.length
        if (end > start) {
            for (entry in stylesOf(element, tag)) {
                when (entry) {
                    is FormatStyle -> ranges.add(FormattedRange(start = start, end = end, format = entry.format))

                    is ColorStyle ->
                        colors.add(ColorRange(start = start, end = end, kind = entry.kind, color = entry.color))
                }
            }
            if (link != null) links.add(LinkRange(start = start, end = end, url = link))
        }
    }

    /**
     * What an element stands for: the format its tag is, and the styles it sets. A `<font>` carries a colour the
     * other ways of writing one are not read from.
     */
    private fun stylesOf(element: Element, tag: String): List<StyleEntry> {
        val entries = parseStyle(element.attr(STYLE_ATTRIBUTE)).entries.toMutableList()
        if (tag == FONT_TAG) {
            parseColor(element.attr(FONT_COLOR_ATTRIBUTE))?.let { entries.add(ColorStyle(ColorKind.Text, it)) }
        }
        FORMAT_BY_TAG[tag]?.let { entries.add(FormatStyle(it)) }
        return entries
    }

    private fun readImage(element: Element, position: Int) {
        val source = element.attr(SOURCE_ATTRIBUTE)
        val contentId = source.removePrefix(CONTENT_ID_PREFIX)
        if (!source.startsWith(CONTENT_ID_PREFIX) || contentId.isEmpty()) return

        text.append(OBJECT_REPLACEMENT_CHARACTER)
        images.add(InlineImage(position = position, contentId = contentId))
    }

    private fun noteLostContent(element: Element, tag: String) {
        when {
            tag in TABLE_TAGS -> lost.add(LostContent.Tables)

            tag == IMAGE_TAG ->
                if (!element.isInlineImage()) lost.add(LostContent.Images)

            else -> {
                if (!parseStyle(element.attr(STYLE_ATTRIBUTE)).known) lost.add(LostContent.Styles)
                if (element.attributes().any { it.key.lowercase() !in modelledAttributes(tag) }) {
                    lost.add(LostContent.Styles)
                }
                if (tag !in KNOWN_TAGS) lost.add(LostContent.Other)
            }
        }
    }

    private fun startsNewLine(tag: String): Boolean {
        val block = tag in BLOCK_TAGS ||
            (lenient && (tag in LENIENT_BLOCK_TAGS || tag in TABLE_TAGS || tag in TABLE_CELL_TAGS))
        return block && text.isNotEmpty() && !text.endsWith('\n')
    }

    private fun appendNewLine() {
        // A space before the break is a space HTML would not show.
        if (lenient && text.endsWith(' ')) text.setLength(text.length - 1)

        text.append('\n')
    }
}
