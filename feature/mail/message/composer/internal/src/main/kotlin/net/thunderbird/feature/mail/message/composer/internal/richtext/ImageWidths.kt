package net.thunderbird.feature.mail.message.composer.internal.richtext

import com.fleeksoft.ksoup.nodes.Element
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.IMAGE_STYLE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.PERCENT_UNIT
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.STYLE_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.WIDTH_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.WIDTH_PROPERTY
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.WIDTH_STYLE_PREFIX
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.WIDTH_STYLE_SUFFIX
import net.thunderbird.feature.mail.message.composer.richtext.InlineImage

/**
 * The width an image is shown with, as a percentage of the width of the message.
 *
 * The composer writes the width into the style of the image, around the limit that keeps the image from being wider
 * than the message. The width of another client is read as long as it is a percentage, whether it stands in the style
 * or in the width attribute; one outside of [InlineImage.MIN_WIDTH_PERCENT]..[InlineImage.MAX_WIDTH_PERCENT] is
 * clamped into it. A width in pixels or in any other unit is a width the composer has no model for, so the image
 * keeps the size it has of its own.
 */
internal object ImageWidths {

    /**
     * The width the style of an image says, `null` for the natural width of the image.
     */
    fun ofStyle(style: String): Int? {
        return style.declarations()
            .firstOrNull { (property, _) -> property == WIDTH_PROPERTY }
            ?.second
            ?.let { percentOf(it) }
    }

    /**
     * The width a client has put into the width attribute of an image, `null` if it put none the composer can show.
     */
    fun ofAttribute(value: String): Int? {
        return percentOf(value)
    }

    /**
     * Whether the style of an image is one the composer writes: the natural width of the image, or a width in percent
     * together with the limit that keeps it from being wider than the message.
     */
    fun isRepresentableStyle(style: String): Boolean {
        val normalized = style.normalizedStyle()
        if (normalized.isEmpty() || normalized == IMAGE_STYLE) return true

        val width = normalized.chosenWidth()

        return width != null && isInRange(width)
    }

    /**
     * The width an image says in its own HTML: from the style the composer writes it in, and, of the HTML of another
     * client, from the width attribute as well. An image whose width the composer has no model for keeps the size it
     * has of its own.
     */
    fun ofImage(element: Element, lenient: Boolean): Int? {
        val fromStyle = ofStyle(element.attr(STYLE_ATTRIBUTE))
        if (fromStyle != null) return fromStyle

        return if (lenient) ofAttribute(element.attr(WIDTH_ATTRIBUTE)) else null
    }

    private fun String.normalizedStyle(): String {
        return filterNot { it.isWhitespace() }.lowercase().removeSuffix(";")
    }

    /**
     * The width out of a style of the form `width: N%; max-width: 100%`, `null` for any other style.
     */
    private fun String.chosenWidth(): String? {
        if (!startsWith(WIDTH_STYLE_PREFIX) || !endsWith(WIDTH_STYLE_SUFFIX)) return null

        return removePrefix(WIDTH_STYLE_PREFIX)
            .removeSuffix(WIDTH_STYLE_SUFFIX)
            .removeSuffix(PERCENT_UNIT)
            .takeIf { it.isNotEmpty() }
    }

    private fun percentOf(value: String): Int? {
        val percent = value.trim().removeSuffix(PERCENT_UNIT).toIntOrNull() ?: return null

        return percent.takeIf { value.trim().endsWith(PERCENT_UNIT) }
            ?.coerceIn(InlineImage.MIN_WIDTH_PERCENT, InlineImage.MAX_WIDTH_PERCENT)
    }

    private fun isInRange(value: String): Boolean {
        val percent = value.toIntOrNull() ?: return false

        return percent in InlineImage.MIN_WIDTH_PERCENT..InlineImage.MAX_WIDTH_PERCENT
    }
}
