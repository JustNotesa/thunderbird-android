package com.fsck.k9.ui.compose

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.InlineImage
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat
import org.junit.Test

class QuoteImagesTest {
    @Test
    fun `should remove the images that are not available together with their character`() {
        // Arrange
        val text = FormattedText(
            text = "a￼b￼c",
            ranges = listOf(FormattedRange(0, 5, TextFormat.Bold)),
            paragraphs = listOf(ParagraphRange(0, 5, BlockType.Quote, 0)),
            images = listOf(InlineImage(1, "gone"), InlineImage(3, "here")),
        )

        // Act
        val result = text.withoutImagesOutside(setOf("here"))

        // Assert
        assertThat(result).isEqualTo(
            FormattedText(
                text = "ab￼c",
                ranges = listOf(FormattedRange(0, 4, TextFormat.Bold)),
                paragraphs = listOf(ParagraphRange(0, 4, BlockType.Quote, 0)),
                images = listOf(InlineImage(2, "here")),
            ),
        )
    }

    @Test
    fun `should leave a text whose images are all available as it is`() {
        // Arrange
        val text = FormattedText("a￼", emptyList(), images = listOf(InlineImage(1, "here")))

        // Act
        val result = text.withoutImagesOutside(setOf("here"))

        // Assert
        assertThat(result).isEqualTo(text)
    }
}
