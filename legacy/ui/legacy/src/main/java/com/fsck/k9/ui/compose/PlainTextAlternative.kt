package com.fsck.k9.ui.compose

import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange

private const val QUOTE_MARKER = "> "
private const val BULLET = "• "
private const val LIST_INDENT = "  "
private const val IMAGE_CHARACTER = "￼"

/**
 * The text of this message for its text/plain part: the lines of a quote start with `> ` once per level (a plain
 * reader has no other way to tell the quote from the answer), list items with their bullet or number, and the places
 * of inline images are dropped.
 */
fun FormattedText.toPlainTextAlternative(): String {
    val numbering = ListCounters()
    var lineStart = 0

    return text.split('\n').joinToString("\n") { line ->
        val paragraph = paragraphs.firstOrNull { lineStart >= it.start && lineStart <= it.end }
        lineStart += line.length + 1

        val content = line.replace(IMAGE_CHARACTER, "")
        prefix(paragraph, numbering, content) + content
    }
}

private fun prefix(paragraph: ParagraphRange?, numbering: ListCounters, content: String): String {
    val block = paragraph?.block ?: BlockType.Normal
    val level = paragraph?.level ?: 0
    if (block != BlockType.NumberedList) numbering.reset()

    return when (block) {
        BlockType.Quote -> {
            val quote = QUOTE_MARKER.repeat(level + 1)
            if (content.isEmpty()) quote.trimEnd() else quote
        }
        BlockType.BulletList -> LIST_INDENT.repeat(level) + BULLET
        BlockType.NumberedList -> LIST_INDENT.repeat(level) + "${numbering.next(level)}. "
        BlockType.Normal -> ""
    }
}

/**
 * The running numbers of the levels of a numbered list; a deeper level starts again from one after an item of a
 * higher level.
 */
private class ListCounters {
    private val counters = mutableListOf<Int>()

    fun next(level: Int): Int {
        while (counters.size > level + 1) counters.removeAt(counters.lastIndex)
        while (counters.size < level + 1) counters.add(0)
        counters[level] = counters[level] + 1
        return counters[level]
    }

    fun reset() {
        counters.clear()
    }
}
