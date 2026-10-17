package net.thunderbird.feature.mail.message.composer.internal.richtext

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.InlineImage
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange
import net.thunderbird.feature.mail.message.composer.richtext.RichTextHtmlConverter.LostContent
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

/**
 * Reading the HTML of a message that is quoted in a reply, whatever the sender's mail program wrote.
 */
class QuotedMessageReadingTest {
    private val testSubject = DefaultRichTextHtmlConverter()

    @Test
    fun `should read a whole HTML document and nothing of its head`() {
        // Arrange
        val html = """
            <!DOCTYPE html><html><head><title>Subject line</title><style>b { color: red }</style></head>
            <body><p>Hello <b>you</b></p><p>Second</p></body></html>
        """.trimIndent()

        // Act
        val result = testSubject.fromHtmlFragmentLenient(html)

        // Assert
        assertThat(result.formattedText.text).isEqualTo("Hello you\nSecond")
        assertThat(result.formattedText.ranges).isEqualTo(listOf(FormattedRange(6, 9, TextFormat.Bold)))
        assertThat(result.lost).isEqualTo(emptySet())
    }

    @Test
    fun `should read the quotes a Gmail message has inside its quote`() {
        // Arrange
        val html = """
            <div dir="ltr">Top</div>
            <div class="gmail_quote"><div class="gmail_attr">On Monday, Max wrote:<br></div>
            <blockquote class="gmail_quote" style="margin:0px 0px 0px 0.8ex;border-left:1px solid rgb(204,204,204)">
            <div dir="ltr">Middle<br><blockquote class="gmail_quote" style="margin:0px 0px 0px 0.8ex">
            <div dir="ltr">Deep</div></blockquote></div></blockquote></div>
        """.trimIndent()

        // Act
        val result = testSubject.fromHtmlFragmentLenient(html)

        // Assert
        assertThat(result.formattedText.text).isEqualTo("Top\nOn Monday, Max wrote:\nMiddle\nDeep")
        assertThat(result.formattedText.paragraphs).isEqualTo(
            listOf(
                ParagraphRange(26, 32, BlockType.Quote, 0),
                ParagraphRange(33, 37, BlockType.Quote, 1),
            ),
        )
    }

    @Test
    fun `should report a table inside a quote and keep the text of its cells`() {
        // Arrange
        val html = """
            <blockquote><p>Before</p><table><tr><td>Cell</td></tr></table><p>After</p></blockquote>
        """.trimIndent()

        // Act
        val result = testSubject.fromHtmlFragmentLenient(html)

        // Assert
        assertThat(result.formattedText.text).isEqualTo("Before\nCell\nAfter")
        assertThat(result.lost).isEqualTo(setOf(LostContent.Tables))
    }

    @Test
    fun `should read a list inside a quote`() {
        // Act
        val result = testSubject.fromHtmlFragmentLenient("<blockquote><ul><li>one</li><li>two</li></ul></blockquote>")

        // Assert
        assertThat(result.formattedText.text).isEqualTo("one\ntwo")
        assertThat(result.lost).isEqualTo(emptySet())
    }

    @Test
    fun `should read the image of the message and report one from the web`() {
        // Act
        val result = testSubject.fromHtmlFragmentLenient(
            """<div>a<img src="cid:logo@example.com">b<img src="https://example.com/x.png"></div>""",
        )

        // Assert
        assertThat(result.formattedText.images).isEqualTo(listOf(InlineImage(1, "logo@example.com")))
        assertThat(result.lost).isEqualTo(setOf(LostContent.Images))
    }
}
