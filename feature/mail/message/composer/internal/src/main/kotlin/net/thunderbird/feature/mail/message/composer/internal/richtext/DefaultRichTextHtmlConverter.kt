package net.thunderbird.feature.mail.message.composer.internal.richtext

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
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

    override fun fromHtmlFragment(html: String): FormattedText {
        val text = StringBuilder()
        val ranges = mutableListOf<FormattedRange>()
        appendNodes(Ksoup.parseBodyFragment(html).body().childNodes(), text, ranges)

        return FormattedText(text = text.toString(), ranges = ranges.sortedWith(compareBy({ it.start }, { it.format })))
    }

    override fun canRepresent(html: String): Boolean {
        return Ksoup.parseBodyFragment(html).body().getAllElements().drop(1).all { element ->
            val tag = element.normalName()
            val attributes = element.attributes().map { it.key.lowercase() }

            tag in REPRESENTABLE_TAGS && attributes.all { it == "dir" && tag in BLOCK_TAGS }
        }
    }

    private fun appendNodes(nodes: List<Node>, text: StringBuilder, ranges: MutableList<FormattedRange>) {
        for (node in nodes) {
            when (node) {
                is TextNode -> text.append(node.getWholeText().replace(NON_BREAKING_SPACE_CHARACTER, ' '))
                is Element -> appendElement(node, text, ranges)
                else -> Unit
            }
        }
    }

    private fun appendElement(element: Element, text: StringBuilder, ranges: MutableList<FormattedRange>) {
        val tag = element.normalName()
        if (tag == "br") {
            text.append('\n')
            return
        }

        // Every block starts on a line of its own.
        if (tag in BLOCK_TAGS && text.isNotEmpty() && !text.endsWith('\n')) {
            text.append('\n')
        }

        val start = text.length
        appendNodes(element.childNodes(), text, ranges)

        val format = FORMAT_BY_TAG[tag]
        if (format != null && start < text.length) {
            ranges.add(FormattedRange(start = start, end = text.length, format = format))
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
        const val NON_BREAKING_SPACE_CHARACTER = '\u00A0'

        val BLOCK_TAGS = setOf("div", "p")
        val FORMAT_BY_TAG = mapOf(
            "b" to TextFormat.Bold,
            "strong" to TextFormat.Bold,
            "i" to TextFormat.Italic,
            "em" to TextFormat.Italic,
            "u" to TextFormat.Underline,
        )
        val REPRESENTABLE_TAGS = BLOCK_TAGS + FORMAT_BY_TAG.keys + "br"
    }
}
