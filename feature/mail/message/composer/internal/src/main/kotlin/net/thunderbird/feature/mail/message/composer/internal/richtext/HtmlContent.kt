package net.thunderbird.feature.mail.message.composer.internal.richtext

import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.ANCHOR_TAG
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.BACKGROUND_COLOR_PROPERTY
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.COLOR_PROPERTY
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.OBJECT_REPLACEMENT_CHARACTER
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.SPAN_TAG
import net.thunderbird.feature.mail.message.composer.richtext.ColorKind
import net.thunderbird.feature.mail.message.composer.richtext.ColorRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.InlineImage
import net.thunderbird.feature.mail.message.composer.richtext.LinkRange
import net.thunderbird.feature.mail.message.composer.richtext.LinkUrlPolicy

/**
 * The formatted text as the runs of HTML it becomes. Every boundary of a range, a colour, a link and of an image
 * starts a run, so that a tag never has to reach across a boundary of its own.
 */
internal class HtmlContent(formattedText: FormattedText) {
    private val text = formattedText.text
    private val ranges = formattedText.ranges.mapNotNull { it.clipTo(text.length) }
    private val colors = formattedText.colors.mapNotNull { it.clipTo(text.length) }
    private val writtenLinks = formattedText.links
        .mapNotNull { it.clipTo(text.length) }
        .mapNotNull { link -> LinkUrlPolicy.normalize(link.url)?.let { link.copy(url = it) } }
    private val links = (writtenLinks + foundLinks()).distinct().sortedWith(compareBy({ it.start }, { it.end }))
    private val images = formattedText.images.associateBy { it.position }

    fun boundaries(): List<Int> {
        return (
            ranges.flatMap { listOf(it.start, it.end) } +
                colors.flatMap { listOf(it.start, it.end) } +
                links.flatMap { listOf(it.start, it.end) } +
                images.keys.flatMap { listOf(it, it + 1) } +
                listOf(0, text.length)
            ).distinct().sorted()
    }

    fun writeRun(builder: StringBuilder, start: Int, end: Int) {
        val formats = ranges.filter { it.start <= start && end <= it.end }.map { it.format }.distinct().sorted()
        val textColor = colors.firstOrNull { it.kind == ColorKind.Text && it.start <= start && end <= it.end }
        val highlight = colors.firstOrNull { it.kind == ColorKind.Highlight && it.start <= start && end <= it.end }

        // A link stands around every run it covers, so that its text stays one link however it is formatted.
        links.filter { it.start == start }.forEach { builder.append(anchorTag(it)) }
        formats.forEach { builder.append(it.openingTag) }
        if (textColor != null) builder.append(colorTag(COLOR_PROPERTY, textColor.color))
        if (highlight != null) builder.append(colorTag(BACKGROUND_COLOR_PROPERTY, highlight.color))

        builder.appendContent(start, end)

        if (highlight != null) builder.append("</$SPAN_TAG>")
        if (textColor != null) builder.append("</$SPAN_TAG>")
        formats.asReversed().forEach { builder.append(it.closingTag) }
        links.filter { it.end == end }.forEach { builder.append("</$ANCHOR_TAG>") }
    }

    /**
     * An image stands for the character that holds it, and is written inside the tags of its run, so that the run
     * reads back the same.
     */
    private fun StringBuilder.appendContent(start: Int, end: Int) {
        val image = images[start]
        if (image != null && end == start + 1 && text[start] == OBJECT_REPLACEMENT_CHARACTER) {
            append(imageTag(image.contentId))
        } else {
            appendHtmlEncoded(text, start, end)
        }
    }

    /**
     * Text that is a web address becomes a link, but only where the user did not make one: a link of the text is
     * the link the user chose. A web address that only reaches into such a link is left as text, because two links
     * that overlap would have to cross.
     */
    private fun foundLinks(): List<LinkRange> {
        return AutoLinker.findLinks(text).filterNot { candidate ->
            writtenLinks.any { candidate.start < it.end && it.start < candidate.end }
        }
    }

    private fun FormattedRange.clipTo(length: Int): FormattedRange? {
        return clippedBounds(start, end, length)?.let { (start, end) -> copy(start = start, end = end) }
    }

    private fun ColorRange.clipTo(length: Int): ColorRange? {
        return clippedBounds(start, end, length)?.let { (start, end) -> copy(start = start, end = end) }
    }

    private fun LinkRange.clipTo(length: Int): LinkRange? {
        return clippedBounds(start, end, length)?.let { (start, end) -> copy(start = start, end = end) }
    }

    private fun clippedBounds(start: Int, end: Int, length: Int): Pair<Int, Int>? {
        val clippedStart = start.coerceIn(0, length)
        val clippedEnd = end.coerceIn(0, length)
        return (clippedStart to clippedEnd).takeIf { clippedStart < clippedEnd }
    }
}
