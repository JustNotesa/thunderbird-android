package com.fsck.k9.ui.compose

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.thunderbird.feature.mail.message.composer.richtext.Alignment
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.ColorKind
import net.thunderbird.feature.mail.message.composer.richtext.ColorRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.InlineImage
import net.thunderbird.feature.mail.message.composer.richtext.LinkRange
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat
import org.junit.Test

class QuoteMergerTest {
    private val testSubject = QuoteMerger

    @Test
    fun `should put an empty line, the header and the quote as quote block below the text`() {
        // Arrange
        val body = FormattedText("Hi", listOf(FormattedRange(0, 2, TextFormat.Bold)))
        val quote = FormattedText(
            text = "a\nb",
            ranges = listOf(FormattedRange(0, 1, TextFormat.Italic)),
            links = listOf(LinkRange(2, 3, "https://example.com")),
        )

        // Act
        val result = testSubject.merge(body, "On Mon, Max wrote:", quote, replyAfterQuote = false)

        // Assert
        assertThat(result.text).isEqualTo(
            FormattedText(
                text = "Hi\n\nOn Mon, Max wrote:\na\nb",
                ranges = listOf(
                    FormattedRange(0, 2, TextFormat.Bold),
                    FormattedRange(23, 24, TextFormat.Italic),
                ),
                links = listOf(LinkRange(25, 26, "https://example.com")),
                paragraphs = listOf(ParagraphRange(23, 26, BlockType.Quote, 0)),
            ),
        )
        assertThat(result.cursor).isEqualTo(2)
    }

    @Test
    fun `should start with an empty line to write in when nothing was written yet`() {
        // Act
        val result = testSubject.merge(
            FormattedText("", emptyList()),
            "Header",
            FormattedText("q", emptyList()),
            replyAfterQuote = false,
        )

        // Assert
        assertThat(result.text.text).isEqualTo("\nHeader\nq")
        assertThat(result.text.paragraphs).isEqualTo(listOf(ParagraphRange(8, 9, BlockType.Quote, 0)))
        assertThat(result.cursor).isEqualTo(0)
    }

    @Test
    fun `should make a quote of the message one level deeper and everything else the first level`() {
        // Arrange
        val quote = FormattedText(
            text = "x\ny\nz",
            ranges = emptyList(),
            paragraphs = listOf(
                ParagraphRange(2, 3, BlockType.Quote, 0),
                ParagraphRange(4, 5, BlockType.Quote, 3),
            ),
        )

        // Act
        val result = testSubject.merge(FormattedText("", emptyList()), "", quote, replyAfterQuote = false)

        // Assert
        assertThat(result.text.paragraphs).isEqualTo(
            listOf(
                ParagraphRange(1, 2, BlockType.Quote, 0),
                ParagraphRange(3, 4, BlockType.Quote, 1),
                ParagraphRange(5, 6, BlockType.Quote, 4),
            ),
        )
    }

    @Test
    fun `should keep the alignment of the quoted lines`() {
        // Arrange
        val quote = FormattedText(
            text = "a\nb",
            ranges = emptyList(),
            paragraphs = listOf(ParagraphRange(2, 3, BlockType.Normal, 0, Alignment.Center)),
        )

        // Act
        val result = testSubject.merge(FormattedText("", emptyList()), "", quote, replyAfterQuote = false)

        // Assert
        assertThat(result.text.paragraphs).isEqualTo(
            listOf(
                ParagraphRange(1, 2, BlockType.Quote, 0, Alignment.Start),
                ParagraphRange(3, 4, BlockType.Quote, 0, Alignment.Center),
            ),
        )
    }

    @Test
    fun `should put the quote before the text and the cursor after it when the reply goes after the quote`() {
        // Arrange
        val body = FormattedText("Mine", listOf(FormattedRange(0, 4, TextFormat.Bold)))

        // Act
        val result = testSubject.merge(body, "H", FormattedText("q", emptyList()), replyAfterQuote = true)

        // Assert
        assertThat(result.text).isEqualTo(
            FormattedText(
                text = "H\nq\n\nMine",
                ranges = listOf(FormattedRange(5, 9, TextFormat.Bold)),
                paragraphs = listOf(ParagraphRange(2, 3, BlockType.Quote, 0)),
            ),
        )
        assertThat(result.cursor).isEqualTo(9)
    }

    @Test
    fun `should leave an empty line to write in below the quote when nothing was written yet`() {
        // Act
        val result = testSubject.merge(
            FormattedText("", emptyList()),
            "H",
            FormattedText("q", emptyList()),
            replyAfterQuote = true,
        )

        // Assert
        assertThat(result.text.text).isEqualTo("H\nq\n")
        assertThat(result.cursor).isEqualTo(4)
    }

    @Test
    fun `should write the items of a list as lines with their bullet or number`() {
        // Arrange
        val quote = FormattedText(
            text = "one\ntwo\nx\nfirst\nsecond",
            ranges = listOf(FormattedRange(4, 7, TextFormat.Bold)),
            paragraphs = listOf(
                ParagraphRange(0, 7, BlockType.BulletList, 0),
                ParagraphRange(10, 22, BlockType.NumberedList, 0),
            ),
        )

        // Act
        val result = testSubject.merge(FormattedText("", emptyList()), "", quote, replyAfterQuote = false)

        // Assert
        assertThat(result.text.text).isEqualTo("\n• one\n• two\nx\n1. first\n2. second")
        assertThat(result.text.ranges).isEqualTo(listOf(FormattedRange(9, 12, TextFormat.Bold)))
        assertThat(result.text.paragraphs).isEqualTo(
            listOf(ParagraphRange(1, result.text.text.length, BlockType.Quote, 0)),
        )
    }

    @Test
    fun `should not format the bullet of the next item when the formatting reaches the end of a line`() {
        // Arrange
        val quote = FormattedText(
            text = "one\ntwo",
            ranges = listOf(FormattedRange(0, 4, TextFormat.Bold)),
            paragraphs = listOf(ParagraphRange(0, 7, BlockType.BulletList, 0)),
        )

        // Act
        val result = testSubject.merge(FormattedText("", emptyList()), "", quote, replyAfterQuote = false)

        // Assert
        assertThat(result.text.ranges).isEqualTo(listOf(FormattedRange(3, 7, TextFormat.Bold)))
    }

    @Test
    fun `should indent the items of a nested list`() {
        // Arrange
        val quote = FormattedText(
            text = "a\nb",
            ranges = emptyList(),
            paragraphs = listOf(
                ParagraphRange(0, 1, BlockType.BulletList, 0),
                ParagraphRange(2, 3, BlockType.BulletList, 1),
            ),
        )

        // Act
        val result = testSubject.merge(FormattedText("", emptyList()), "", quote, replyAfterQuote = false)

        // Assert
        assertThat(result.text.text).isEqualTo("\n• a\n  • b")
    }

    @Test
    fun `should drop the empty lines around the quote`() {
        // Act
        val result = testSubject.merge(
            FormattedText("", emptyList()),
            "",
            FormattedText("\n\nq\n", emptyList()),
            replyAfterQuote = false,
        )

        // Assert
        assertThat(result.text.text).isEqualTo("\nq")
    }

    @Test
    fun `should move colours and images of the quote to where their text ends up`() {
        // Arrange
        val body = FormattedText("Hi", emptyList())
        val quote = FormattedText(
            text = "ab￼",
            ranges = emptyList(),
            colors = listOf(ColorRange(0, 2, ColorKind.Text, 0xd93025)),
            images = listOf(InlineImage(2, "image1")),
        )

        // Act
        val result = testSubject.merge(body, "", quote, replyAfterQuote = false)

        // Assert
        assertThat(result.text.colors).isEqualTo(listOf(ColorRange(4, 6, ColorKind.Text, 0xd93025)))
        assertThat(result.text.images).isEqualTo(listOf(InlineImage(6, "image1")))
    }
}
