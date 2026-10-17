package net.thunderbird.feature.mail.message.composer.internal.richtext

import net.thunderbird.feature.mail.message.composer.richtext.ColorKind
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

/**
 * The names the HTML of the composer is written from and read by: its tags, its attributes and the tags it knows
 * enough of to read.
 */
internal object HtmlNames {

    const val DIV_START = """<div dir="auto">"""
    const val DIV_END = "</div>"
    const val NON_BREAKING_SPACE = "&nbsp;"
    const val NON_BREAKING_SPACE_CHARACTER = '\u00A0'
    const val OBJECT_REPLACEMENT_CHARACTER = '\uFFFC'

    const val SPAN_TAG = "span"
    const val ANCHOR_TAG = "a"
    const val IMAGE_TAG = "img"
    const val FONT_TAG = "font"
    const val BR_TAG = "br"
    const val PRE_TAG = "pre"
    const val STYLE_ATTRIBUTE = "style"
    const val HREF_ATTRIBUTE = "href"
    const val SOURCE_ATTRIBUTE = "src"
    const val ALT_ATTRIBUTE = "alt"
    const val DIRECTION_ATTRIBUTE = "dir"
    const val COLOR_PROPERTY = "color"
    const val FONT_COLOR_ATTRIBUTE = "color"
    const val BACKGROUND_COLOR_PROPERTY = "background-color"
    const val IMAGE_STYLE_PROPERTY = "max-width: 100%"
    const val CONTENT_ID_PREFIX = "cid:"
    const val RGB_PREFIX = "rgb("

    val IMAGE_STYLE = IMAGE_STYLE_PROPERTY.filterNot { it.isWhitespace() }
    val IMAGE_ATTRIBUTES = setOf(SOURCE_ATTRIBUTE, ALT_ATTRIBUTE, STYLE_ATTRIBUTE)

    // What a script or a style file says is not text of the message.
    val DROPPED_TAGS = setOf("script", "style", "head", "title")

    val BLOCK_TAGS = setOf("div", "p")
    val LENIENT_BLOCK_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "pre", "ul", "ol")
    val TABLE_TAGS = setOf("table", "tr", "thead", "tbody", "tfoot", "caption")
    val TABLE_CELL_TAGS = setOf("td", "th")

    // Tags that carry nothing the composer would show differently from their text.
    val HARMLESS_INLINE_TAGS = setOf(
        "small",
        "big",
        "sup",
        "sub",
        "code",
        "mark",
        "abbr",
        "cite",
        "q",
        "label",
        "center",
        "section",
        "article",
        "header",
        "footer",
        "main",
        "nav",
    )

    val FORMAT_BY_TAG = mapOf(
        "b" to TextFormat.Bold,
        "strong" to TextFormat.Bold,
        "i" to TextFormat.Italic,
        "em" to TextFormat.Italic,
        "u" to TextFormat.Underline,
        "s" to TextFormat.Strikethrough,
        "strike" to TextFormat.Strikethrough,
        "del" to TextFormat.Strikethrough,
    )
    val FORMAT_TAGS = FORMAT_BY_TAG.keys

    val KNOWN_TAGS = BLOCK_TAGS + LENIENT_BLOCK_TAGS + TABLE_TAGS + TABLE_CELL_TAGS + HARMLESS_INLINE_TAGS +
        FORMAT_TAGS + setOf(BR_TAG, SPAN_TAG, ANCHOR_TAG, IMAGE_TAG, FONT_TAG)

    val COLOR_BY_PROPERTY = mapOf(
        COLOR_PROPERTY to ColorKind.Text,
        BACKGROUND_COLOR_PROPERTY to ColorKind.Highlight,
    )

    // Sizes and fonts every mail client knows, so the message looks the same without any font being sent along.
    val STYLE_BY_FORMAT = mapOf(
        TextFormat.SizeSmall to "font-size: small",
        TextFormat.SizeLarge to "font-size: large",
        TextFormat.SizeHuge to "font-size: x-large",
        TextFormat.FontSerif to "font-family: serif",
        TextFormat.FontMonospace to "font-family: monospace",
    )
    val FORMAT_BY_STYLE = STYLE_BY_FORMAT.entries.associate { (format, style) ->
        style.filterNot { it.isWhitespace() } to format
    }

    /**
     * The attributes of [tag] the composer models. Every other attribute of it is something it would have to drop.
     */
    fun modelledAttributes(tag: String): Set<String> = when {
        tag in BLOCK_TAGS -> setOf(DIRECTION_ATTRIBUTE)
        tag == ANCHOR_TAG -> setOf(HREF_ATTRIBUTE)
        tag == IMAGE_TAG -> IMAGE_ATTRIBUTES
        tag == FONT_TAG -> setOf(FONT_COLOR_ATTRIBUTE)
        else -> setOf(STYLE_ATTRIBUTE)
    }
}
