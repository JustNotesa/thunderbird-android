package net.thunderbird.feature.mail.message.composer.internal.richtext

import com.fleeksoft.ksoup.nodes.Element
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.INDENT_PX
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.STYLE_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.richtext.Alignment
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange

// What the style of an element says about the lines inside it: where they are placed and how far a plain paragraph
// is indented.

/**
 * The value of a property of the style of [element], if it sets it.
 */
internal fun styleOf(element: Element, property: String): String? {
    return element.attr(STYLE_ATTRIBUTE)
        .split(';')
        .map { it.substringBefore(':').trim().lowercase() to it.substringAfter(':', "").trim().lowercase() }
        .firstOrNull { it.first == property }
        ?.second
}

/**
 * Where the lines of a block are placed, if the style of the element says it.
 */
internal fun alignmentOf(value: String?): Alignment? {
    return when {
        value == null -> null
        value == "center" -> Alignment.Center
        value in HtmlNames.END_ALIGNMENTS -> Alignment.End
        value in HtmlNames.START_ALIGNMENTS -> Alignment.Start
        else -> null
    }
}

/**
 * How many levels a plain paragraph is indented, if the style of the element says it.
 */
internal fun levelOf(value: String?): Int? {
    val pixels = value?.removeSuffix(PX)?.toIntOrNull() ?: return null
    return (pixels / INDENT_PX).coerceIn(0, ParagraphRange.MAX_LEVEL)
}

private const val PX = "px"
