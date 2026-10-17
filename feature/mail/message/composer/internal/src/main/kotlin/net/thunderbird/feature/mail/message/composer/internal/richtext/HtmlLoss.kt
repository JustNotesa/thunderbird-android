package net.thunderbird.feature.mail.message.composer.internal.richtext

import com.fleeksoft.ksoup.nodes.Element
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.BLOCKQUOTE_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.BLOCK_TAGS
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.CLASS_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.GMAIL_QUOTE_CLASS
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.IMAGE_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.KNOWN_TAGS
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.STYLE_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.TABLE_TAGS
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.modelledAttributes
import net.thunderbird.feature.mail.message.composer.richtext.RichTextHtmlConverter.LostContent

/**
 * Notes what a reader has to drop to keep the model: what it writes into the text and what it leaves out. Nothing is
 * dropped without being told, so that the user can decide what to do about it.
 */
internal object HtmlLoss {

    fun note(element: Element, tag: String, lost: MutableSet<LostContent>) {
        when {
            tag in TABLE_TAGS -> lost.add(LostContent.Tables)

            tag == IMAGE_TAG ->
                if (!element.isInlineImage()) lost.add(LostContent.Images)

            else -> noteStyle(element, tag, lost)
        }
    }

    /**
     * A style the composer has no model for and an attribute it does not know. The style of a quote block is kept,
     * because the quote itself is what the composer shows; the class Gmail puts around the quote of a message it
     * has quoted stands for nothing on its own.
     */
    private fun noteStyle(element: Element, tag: String, lost: MutableSet<LostContent>) {
        if (!isKeptStyle(element, tag)) lost.add(LostContent.Styles)

        val attributes = element.attributes().map { it.key.lowercase() }.toMutableList()
        if (isQuotedGmail(element)) attributes.remove(CLASS_ATTRIBUTE)
        if (attributes.any { it !in modelledAttributes(tag) }) lost.add(LostContent.Styles)

        if (tag !in KNOWN_TAGS) lost.add(LostContent.Other)
    }

    private fun isKeptStyle(element: Element, tag: String): Boolean {
        return tag == BLOCKQUOTE_TAG || parseStyle(element.attr(STYLE_ATTRIBUTE)).known
    }

    private fun isQuotedGmail(element: Element): Boolean {
        return element.normalName() in BLOCK_TAGS && element.classNames().contains(GMAIL_QUOTE_CLASS)
    }
}
