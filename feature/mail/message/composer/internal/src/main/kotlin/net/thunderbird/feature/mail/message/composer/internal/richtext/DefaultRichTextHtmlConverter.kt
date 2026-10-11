package net.thunderbird.feature.mail.message.composer.internal.richtext

import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.RichTextHtmlConverter
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

/**
 * Writes the text as one `<div dir="auto">`, like the plain text conversion does, and wraps every run of text that
 * shares the same formatting in the matching tags.
 */
internal class DefaultRichTextHtmlConverter : RichTextHtmlConverter {

    override fun toHtmlFragment(text: String, ranges: List<FormattedRange>): String {
        val validRanges = ranges.mapNotNull { it.clipTo(text.length) }
        val boundaries = (validRanges.flatMap { listOf(it.start, it.end) } + 0 + text.length).distinct().sorted()

        return buildString {
            append(DIV_START)
            boundaries.zipWithNext { start, end ->
                val formats = validRanges
                    .filter { it.start <= start && end <= it.end }
                    .map { it.format }
                    .distinct()
                    .sorted()

                formats.forEach { append("<${it.tag}>") }
                appendHtmlEncoded(text, start, end)
                formats.asReversed().forEach { append("</${it.tag}>") }
            }
            append(DIV_END)
        }
    }

    private fun FormattedRange.clipTo(length: Int): FormattedRange? {
        val clipped = copy(start = start.coerceIn(0, length), end = end.coerceIn(0, length))
        return clipped.takeIf { it.start < it.end }
    }

    private fun StringBuilder.appendHtmlEncoded(text: String, start: Int, end: Int) {
        for (index in start until end) {
            when (val character = text[index]) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\n' -> append("<br>")
                '\r' -> Unit
                ' ' -> append(if (text.isCollapsedSpaceAt(index)) NON_BREAKING_SPACE else " ")
                else -> append(character)
            }
        }
    }

    /**
     * HTML shows a space at the start of a line, and all but the first of several spaces in a row, as nothing.
     */
    private fun String.isCollapsedSpaceAt(index: Int): Boolean {
        return index == 0 || this[index - 1] == ' ' || this[index - 1] == '\n'
    }

    private val TextFormat.tag: String
        get() = when (this) {
            TextFormat.Bold -> "b"
            TextFormat.Italic -> "i"
            TextFormat.Underline -> "u"
        }

    private companion object {
        const val DIV_START = """<div dir="auto">"""
        const val DIV_END = "</div>"
        const val NON_BREAKING_SPACE = "&nbsp;"
    }
}
