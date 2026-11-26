package net.thunderbird.feature.mail.message.composer.signature

/**
 * A formatting the user applies to the selected part of an HTML source text.
 */
enum class HtmlSourceFormat(val openTag: String, val closeTag: String) {
    Bold("<b>", "</b>"),
    Italic("<i>", "</i>"),
    Underline("<u>", "</u>"),
    Strikethrough("<s>", "</s>"),
    SizeSmall("<span style=\"font-size:0.8em\">", "</span>"),
    SizeLarge("<span style=\"font-size:1.25em\">", "</span>"),
    SizeHuge("<span style=\"font-size:1.6em\">", "</span>"),
    FontSerif("<span style=\"font-family:serif\">", "</span>"),
    FontMonospace("<span style=\"font-family:monospace\">", "</span>"),
}

/**
 * The text of an HTML source field with the selection (cursor if [selectionStart] equals [selectionEnd]).
 */
data class HtmlSourceText(val text: String, val selectionStart: Int, val selectionEnd: Int) {
    /**
     * Wraps the selection in the tags of [format]. Without a selection the tags are inserted around the cursor, so the
     * user types between them.
     */
    fun withFormat(format: HtmlSourceFormat): HtmlSourceText {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)

        val newText = text.substring(0, start) + format.openTag + text.substring(start, end) +
            format.closeTag + text.substring(end)
        val newStart = start + format.openTag.length

        return HtmlSourceText(newText, newStart, newStart + (end - start))
    }

    /**
     * Inserts [html] at the cursor, replacing the selection, and puts the cursor behind it.
     */
    fun withInserted(html: String): HtmlSourceText {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val newEnd = start + html.length

        return HtmlSourceText(text.substring(0, start) + html + text.substring(end), newEnd, newEnd)
    }
}
