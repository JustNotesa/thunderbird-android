package net.thunderbird.feature.mail.message.composer.internal.richtext

import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.ALT_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.ANCHOR_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.CONTENT_ID_PREFIX
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.HREF_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.IMAGE_STYLE_PROPERTY
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.IMAGE_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.NON_BREAKING_SPACE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.OBJECT_REPLACEMENT_CHARACTER
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.SOURCE_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.SPAN_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.STYLE_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.STYLE_BY_FORMAT
import net.thunderbird.feature.mail.message.composer.richtext.LinkRange
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

internal val TextFormat.openingTag: String
    get() = STYLE_BY_FORMAT[this]?.let { style -> """<$SPAN_TAG $STYLE_ATTRIBUTE="$style">""" } ?: "<$simpleTag>"

internal val TextFormat.closingTag: String
    get() = if (this in STYLE_BY_FORMAT) "</$SPAN_TAG>" else "</$simpleTag>"

private val TextFormat.simpleTag: String
    get() = when (this) {
        TextFormat.Bold -> "b"
        TextFormat.Italic -> "i"
        TextFormat.Underline -> "u"
        TextFormat.Strikethrough -> "s"
        else -> error("$this is written as a style")
    }

internal fun anchorTag(link: LinkRange): String {
    return """<$ANCHOR_TAG $HREF_ATTRIBUTE="${link.url.htmlAttributeEncoded()}">"""
}

/**
 * A colour as `#rrggbb`. The bits of an alpha channel the colour may come with are not part of it.
 */
internal fun colorTag(property: String, color: Int): String {
    val digits = (color and RGB_MASK).toString(HEX_RADIX).padStart(COLOR_DIGITS, '0')
    return """<$SPAN_TAG $STYLE_ATTRIBUTE="$property: #$digits">"""
}

internal fun imageTag(contentId: String): String {
    return """<$IMAGE_TAG $SOURCE_ATTRIBUTE="$CONTENT_ID_PREFIX${contentId.htmlAttributeEncoded()}" """ +
        """$ALT_ATTRIBUTE="" $STYLE_ATTRIBUTE="$IMAGE_STYLE_PROPERTY">"""
}

internal fun String.htmlAttributeEncoded(): String {
    return buildString {
        for (character in this@htmlAttributeEncoded) {
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(character)
            }
        }
    }
}

internal fun StringBuilder.appendHtmlEncoded(text: String, start: Int, end: Int) {
    for (index in start until end) {
        when (val character = text[index]) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\n' -> append("<br>")
            '\r' -> Unit
            ' ' -> append(if (text.isCollapsedSpaceAt(index)) NON_BREAKING_SPACE else " ")
            OBJECT_REPLACEMENT_CHARACTER -> Unit
            else -> append(character)
        }
    }
}

/**
 * HTML shows a space at the start of a line, and all but the first of several spaces in a row, as nothing.
 */
internal fun String.isCollapsedSpaceAt(index: Int): Boolean {
    return index == 0 || this[index - 1] == ' ' || this[index - 1] == '\n'
}
