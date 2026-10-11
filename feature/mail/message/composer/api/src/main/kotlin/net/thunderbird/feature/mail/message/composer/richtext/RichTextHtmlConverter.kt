package net.thunderbird.feature.mail.message.composer.richtext

/**
 * The character formatting the message composer supports.
 */
enum class TextFormat {
    Bold,
    Italic,
    Underline,
}

/**
 * A part of the message text that has a [format]. [start] is inclusive, [end] is exclusive.
 */
data class FormattedRange(
    val start: Int,
    val end: Int,
    val format: TextFormat,
)

/**
 * Message text with `\n` line breaks and the parts of it that are formatted.
 */
data class FormattedText(
    val text: String,
    val ranges: List<FormattedRange>,
)

/**
 * Converts message text the user formatted in the composer into the HTML the message is sent as, and back.
 */
interface RichTextHtmlConverter {
    /**
     * @param text the message text as the user typed it, with `\n` line breaks
     * @param ranges the formatted parts of [text]; they may overlap
     * @return an HTML fragment for the body of the message
     */
    fun toHtmlFragment(text: String, ranges: List<FormattedRange>): String

    /**
     * Reads back an HTML fragment written by [toHtmlFragment], e.g. from a draft that is opened again.
     */
    fun fromHtmlFragment(html: String): FormattedText

    /**
     * Whether [fromHtmlFragment] reads [html] without losing anything, i.e. whether it only contains text, line
     * breaks and the formatting the composer supports. HTML with links, images, tables or styles is not.
     */
    fun canRepresent(html: String): Boolean
}
