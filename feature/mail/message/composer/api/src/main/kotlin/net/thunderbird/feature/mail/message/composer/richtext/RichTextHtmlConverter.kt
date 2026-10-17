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
    Strikethrough,
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
 * Text or highlight colour of a part of the text. [color] is 0xRRGGBB (no alpha).
 */
data class ColorRange(
    val start: Int,
    val end: Int,
    val kind: ColorKind,
    val color: Int,
)

/**
 * Whether a [ColorRange] colours the text itself or its background.
 */
enum class ColorKind {
    Text,
    Highlight,
}

/**
 * A link on a part of the text. [url] is absolute: http, https or mailto only.
 */
data class LinkRange(
    val start: Int,
    val end: Int,
    val url: String,
)

/**
 * Paragraph formatting. [start] is the start of the first line, [end] the end of the last line (exclusive, before
 * its `\n`). Paragraph ranges never overlap and always cover whole lines.
 */
data class ParagraphRange(
    val start: Int,
    val end: Int,
    val block: BlockType = BlockType.Normal,
    val level: Int = 0,
    val alignment: Alignment = Alignment.Start,
) {
    companion object {
        /**
         * The deepest indentation [level] may ask for.
         */
        const val MAX_LEVEL = 4
    }
}

/**
 * What a block of text is, aside from ordinary paragraphs.
 */
enum class BlockType {
    Normal,
    BulletList,
    NumberedList,
    Quote,
}

/**
 * How the lines of a block are placed relative to its own width.
 */
enum class Alignment {
    Start,
    Center,
    End,
}

/**
 * An image inside the text. The text holds one U+FFFC at [position]; [contentId] is the MIME Content-ID.
 */
data class InlineImage(
    val position: Int,
    val contentId: String,
)

/**
 * Message text with `\n` line breaks and the parts of it that are formatted.
 */
data class FormattedText(
    val text: String,
    val ranges: List<FormattedRange>,
    val colors: List<ColorRange> = emptyList(),
    val links: List<LinkRange> = emptyList(),
    val paragraphs: List<ParagraphRange> = emptyList(),
    val images: List<InlineImage> = emptyList(),
) {
    /**
     * Whether any part of the text is formatted; text without formatting can be sent as plain text.
     */
    fun hasFormatting(): Boolean {
        return ranges.isNotEmpty() || colors.isNotEmpty() || links.isNotEmpty() || paragraphs.isNotEmpty() ||
            images.isNotEmpty()
    }
}

/**
 * Converts message text the user formatted in the composer into the HTML the message is sent as, and back.
 */
interface RichTextHtmlConverter {
    /**
     * @param text the message text as the user typed it, with `\n` line breaks
     * @param ranges the formatted parts of [text]; they may overlap
     * @return an HTML fragment for the body of the message
     */
    fun toHtmlFragment(text: String, ranges: List<FormattedRange>): String =
        toHtmlFragment(FormattedText(text, ranges))

    /**
     * Writes everything [formattedText] holds - character formats, colours, links, paragraphs and inline images -
     * as an HTML fragment for the body of the message. Plain text that is a URL and not already part of a link
     * becomes a link, so the recipient sees the links the user sees while writing.
     */
    fun toHtmlFragment(formattedText: FormattedText): String

    /**
     * Reads back an HTML fragment written by [toHtmlFragment], e.g. from a draft that is opened again.
     */
    fun fromHtmlFragment(html: String): FormattedText

    /**
     * Reads any HTML, e.g. the quoted part of a reply, keeping the text, the line structure and everything the
     * model can hold, and reports what kind of content it had to drop instead.
     */
    fun fromHtmlFragmentLenient(html: String): LenientResult

    /**
     * Whether [fromHtmlFragment] reads [html] without losing anything, i.e. whether it only contains text, line
     * breaks and the formatting [toHtmlFragment] writes: character formats, text and highlight colours, links,
     * bullet and numbered lists, quotes, alignment, indentation and inline images. Tables, images that are not
     * inline images of the message and other styles are not.
     */
    fun canRepresent(html: String): Boolean

    /**
     * The text [fromHtmlFragmentLenient] read, and the kinds of content it could not keep.
     */
    data class LenientResult(
        val formattedText: FormattedText,
        val lost: Set<LostContent>,
    )

    /**
     * The kinds of content [fromHtmlFragmentLenient] drops instead of writing into the model.
     */
    enum class LostContent {
        Tables,
        Images,
        Styles,
        Other,
    }
}
