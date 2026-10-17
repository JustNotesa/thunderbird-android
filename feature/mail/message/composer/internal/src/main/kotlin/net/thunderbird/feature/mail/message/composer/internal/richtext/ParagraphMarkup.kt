package net.thunderbird.feature.mail.message.composer.internal.richtext

import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.BLOCKQUOTE_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.DIV_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.INDENT_PX
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.LIST_ITEM_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.MARGIN_LEFT_PROPERTY
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.NO_MARKER_STYLE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.QUOTE_STYLE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.STYLE_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.TEXT_ALIGN_PROPERTY
import net.thunderbird.feature.mail.message.composer.richtext.Alignment
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange

/**
 * The opening tags of the block elements the composer writes. Every style it writes holds nothing but the
 * declarations the model knows, so that [canRepresent] recognises them.
 */
internal object ParagraphMarkup {

    /**
     * A `<blockquote>` of a quote block, with the style Gmail uses for it.
     */
    fun quoteStart(): String = elementStart(BLOCKQUOTE_TAG, QUOTE_STYLE)

    /**
     * A `<li>` of a list item. A list item that only stands for the indent of its level has no marker of its own.
     */
    fun listItemStart(alignment: Alignment, markerless: Boolean): String {
        val declarations = buildList {
            if (markerless) add(NO_MARKER_STYLE)
            if (alignment != Alignment.Start) add(alignmentStyle(alignment))
        }
        return if (declarations.isEmpty()) {
            "<$LIST_ITEM_TAG>"
        } else {
            elementStart(LIST_ITEM_TAG, declarations.joinToString(STYLE_SEPARATOR))
        }
    }

    /**
     * A `<div>` around the lines of a plain paragraph that is indented or placed to one side, and nothing at all for
     * the paragraph that is formatted the way an unformatted one is.
     */
    fun paragraphStart(paragraph: ParagraphRange?): String {
        val declarations = buildList {
            if (paragraph != null && paragraph.block == BlockType.Normal && paragraph.level > 0) {
                add("$MARGIN_LEFT_PROPERTY: ${INDENT_PX * paragraph.level}px")
            }
            if (paragraph != null && paragraph.alignment != Alignment.Start) add(alignmentStyle(paragraph.alignment))
        }
        return if (declarations.isEmpty()) "" else elementStart(DIV_TAG, declarations.joinToString(STYLE_SEPARATOR))
    }

    private fun elementStart(tag: String, style: String): String = """<$tag $STYLE_ATTRIBUTE="$style">"""

    private fun alignmentStyle(alignment: Alignment): String {
        return "$TEXT_ALIGN_PROPERTY: ${if (alignment == Alignment.End) "right" else "center"}"
    }

    private const val STYLE_SEPARATOR = "; "
}

/**
 * The tag a list of [block] is written as.
 */
internal fun listTagOf(block: BlockType): String {
    return if (block == BlockType.NumberedList) HtmlNames.ORDERED_LIST_TAG else HtmlNames.UNORDERED_LIST_TAG
}
