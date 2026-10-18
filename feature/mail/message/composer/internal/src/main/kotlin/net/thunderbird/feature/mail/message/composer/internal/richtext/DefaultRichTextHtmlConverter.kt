package net.thunderbird.feature.mail.message.composer.internal.richtext

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.ANCHOR_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.BLOCKQUOTE_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.BLOCK_TAGS
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.BR_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.CONTENT_ID_PREFIX
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.DIRECTION_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.DIV_END
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.DIV_START
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.FORMAT_TAGS
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.HREF_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.IMAGE_ATTRIBUTES
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.IMAGE_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.LIST_ITEM_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.LIST_TAGS
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.QUOTE_STYLE_COMPACT
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.SOURCE_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.SPAN_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.STYLE_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.LinkUrlPolicy
import net.thunderbird.feature.mail.message.composer.richtext.RichTextHtmlConverter
import net.thunderbird.feature.mail.message.composer.richtext.RichTextHtmlConverter.LenientResult

/**
 * Writes the text as one `<div dir="auto">`, like the plain text conversion does, and wraps every run of text that
 * shares the same formatting in the matching tags. Plain text that is a web address becomes a link, so that the
 * recipient sees the links the user sees while writing.
 *
 * The lines the user has given a paragraph formatting of their own are written as the lists, quotes, indentations
 * and alignments the user has chosen.
 *
 * [fromHtmlFragment] reads back what this writes; [fromHtmlFragmentLenient] also reads the HTML of a message the
 * composer did not write itself, keeping the text, the lines and everything the model can hold, and reporting what
 * it had to drop instead.
 */
internal class DefaultRichTextHtmlConverter : RichTextHtmlConverter {

    override fun toHtmlFragment(formattedText: FormattedText): String {
        val content = HtmlContent(formattedText)
        val paragraphs = normalizeParagraphs(formattedText.text, formattedText.paragraphs)

        return buildString {
            append(DIV_START)
            ParagraphWriter(formattedText.text, paragraphs, content::writeText).write(this)
            append(DIV_END)
        }
    }

    override fun fromHtmlFragment(html: String): FormattedText {
        return HtmlReader(lenient = false).read(html).formattedText
    }

    override fun fromHtmlFragmentLenient(html: String): LenientResult {
        return HtmlReader(lenient = true).read(html)
    }

    override fun canRepresent(html: String): Boolean {
        return Ksoup.parseBodyFragment(html).body().getAllElements().drop(1).all { isRepresentable(it) }
    }
}

/**
 * Whether the element is one the composer writes itself, so that reading it does not lose anything.
 */
private fun isRepresentable(element: Element): Boolean {
    return when (element.normalName()) {
        // A span is only known for the styles it sets.
        SPAN_TAG -> element.hasOnly(STYLE_ATTRIBUTE) && parseStyle(element.attr(STYLE_ATTRIBUTE)).known

        ANCHOR_TAG ->
            element.hasOnly(HREF_ATTRIBUTE) && LinkUrlPolicy.normalize(element.attr(HREF_ATTRIBUTE)) != null

        IMAGE_TAG -> element.isRepresentableImage()

        BR_TAG -> element.attributes().isEmpty()

        LIST_ITEM_TAG -> element.isRepresentableListItem()

        in LIST_TAGS -> element.attributes().isEmpty()

        BLOCKQUOTE_TAG -> element.isRepresentableQuote()

        in BLOCK_TAGS -> element.isRepresentableBlock()

        in FORMAT_TAGS -> element.attributes().isEmpty()

        else -> false
    }
}

/**
 * The elements the composer knows because it writes them itself.
 */
private fun Element.hasOnly(attribute: String): Boolean {
    return attributes().map { it.key.lowercase() } == listOf(attribute)
}

private fun Element.isRepresentableImage(): Boolean {
    val attributes = attributes().map { it.key.lowercase() }
    val source = attr(SOURCE_ATTRIBUTE)

    return attributes.all { it in IMAGE_ATTRIBUTES } &&
        source.startsWith(CONTENT_ID_PREFIX) &&
        source.length > CONTENT_ID_PREFIX.length &&
        ImageWidths.isRepresentableStyle(attr(STYLE_ATTRIBUTE))
}

/**
 * A list item is known for the marker it does not show and for the alignment of its text.
 */
private fun Element.isRepresentableListItem(): Boolean {
    val attributes = attributes().map { it.key.lowercase() }
    return attributes.all { it == STYLE_ATTRIBUTE } && isRepresentableItemStyle(attr(STYLE_ATTRIBUTE))
}

/**
 * A quote block is known for the style the composer gives it and for nothing else.
 */
private fun Element.isRepresentableQuote(): Boolean {
    val attributes = attributes().map { it.key.lowercase() }
    val style = attr(STYLE_ATTRIBUTE).filterNot { it.isWhitespace() }.lowercase()
    return attributes == listOf(STYLE_ATTRIBUTE) && style == QUOTE_STYLE_COMPACT
}

/**
 * A block of plain text is known for the direction of its text, for the indentation of its lines and for the
 * alignment of its lines.
 */
private fun Element.isRepresentableBlock(): Boolean {
    val attributes = attributes().map { it.key.lowercase() }
    return attributes.all { attribute ->
        attribute == DIRECTION_ATTRIBUTE ||
            (attribute == STYLE_ATTRIBUTE && isRepresentableParagraphStyle(attr(STYLE_ATTRIBUTE)))
    }
}

/**
 * An image that stands inside the text of the message, whatever else the attributes of it say.
 */
internal fun Element.isInlineImage(): Boolean {
    val source = attr(SOURCE_ATTRIBUTE)
    return source.startsWith(CONTENT_ID_PREFIX) && source.length > CONTENT_ID_PREFIX.length
}
