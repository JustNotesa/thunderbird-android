package net.thunderbird.feature.mail.message.composer.internal.richtext

import com.fleeksoft.ksoup.nodes.Element
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.FONT_COLOR_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.FONT_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.FORMAT_BY_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.HEADING_TAGS
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.LARGE_HEADING_LEVEL
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.STYLE_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.richtext.ColorKind
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

/**
 * What an element says about the formatting of the text inside it: the format its tag is, the styles it sets and the
 * colour of a `<font>`.
 */
internal object HtmlStyles {

    fun entriesOf(element: Element, tag: String, headings: Boolean): List<StyleEntry> {
        val entries = parseStyle(element.attr(STYLE_ATTRIBUTE)).entries.toMutableList()

        if (tag == FONT_TAG) {
            parseColor(element.attr(FONT_COLOR_ATTRIBUTE))?.let { entries.add(ColorStyle(ColorKind.Text, it)) }
        }
        FORMAT_BY_TAG[tag]?.let { entries.add(FormatStyle(it)) }
        if (headings && tag in HEADING_TAGS) entries.addAll(headingsOf(tag))

        return entries
    }

    /**
     * A heading is read as bold text; the two biggest ones as large text as well.
     */
    private fun headingsOf(tag: String): List<StyleEntry> {
        val entries = mutableListOf<StyleEntry>(FormatStyle(TextFormat.Bold))
        val level = tag.getOrNull(1)?.digitToInt() ?: 0
        if (level in 1..LARGE_HEADING_LEVEL) {
            entries.add(FormatStyle(TextFormat.SizeLarge))
        }
        return entries
    }
}
