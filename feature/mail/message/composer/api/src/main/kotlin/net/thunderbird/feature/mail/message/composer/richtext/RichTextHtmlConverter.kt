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
 * Converts message text the user formatted in the composer into the HTML the message is sent as.
 */
fun interface RichTextHtmlConverter {
    /**
     * @param text the message text as the user typed it, with `\n` line breaks
     * @param ranges the formatted parts of [text]; they may overlap
     * @return an HTML fragment for the body of the message
     */
    fun toHtmlFragment(text: String, ranges: List<FormattedRange>): String
}
