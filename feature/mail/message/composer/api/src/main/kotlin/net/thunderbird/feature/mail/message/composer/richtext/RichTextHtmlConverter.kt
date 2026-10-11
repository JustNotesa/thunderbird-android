package net.thunderbird.feature.mail.message.composer.richtext

/**
 * The character formatting the message composer supports.
 *
 * Formats without a [group] are switched on and off independently. Of the formats of one [group] a piece of text
 * has at most one; having none of them means the normal size or the default font.
 */
enum class TextFormat(val group: Group? = null) {
    Bold,
    Italic,
    Underline,
    SizeSmall(Group.Size),
    SizeLarge(Group.Size),
    SizeHuge(Group.Size),
    FontSerif(Group.Font),
    FontMonospace(Group.Font),
    ;

    enum class Group {
        Size,
        Font,
    }

    companion object {
        /**
         * The formats that are switched on and off independently of each other.
         */
        val toggles: List<TextFormat> = entries.filter { it.group == null }

        fun of(group: Group): List<TextFormat> = entries.filter { it.group == group }
    }
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
     * breaks and the formatting the composer supports. HTML with links, images, tables or other styles is not.
     */
    fun canRepresent(html: String): Boolean
}
