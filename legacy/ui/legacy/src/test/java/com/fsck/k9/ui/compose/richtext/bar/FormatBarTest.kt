package com.fsck.k9.ui.compose.richtext.bar

import android.text.SpannableStringBuilder
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.fsck.k9.ui.compose.richtext.LinkFormatter
import com.fsck.k9.ui.compose.richtext.ImageSource
import com.fsck.k9.ui.compose.richtext.RichTextFormatter
import net.thunderbird.core.android.testing.RobolectricTest
import net.thunderbird.feature.mail.message.composer.richtext.Alignment
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.ColorKind
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat
import org.junit.Test

class FormatBarTest : RobolectricTest() {

    @Test
    fun `the state should show the formatting of the selection`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane\nnext")
        applyFormatBarEvent(text, FormatBarEvent.ToggleFormat(TextFormat.Bold), 0, 5)
        applyFormatBarEvent(text, FormatBarEvent.SetColor(ColorKind.Text, 0xd93025), 0, 5)
        applyFormatBarEvent(text, FormatBarEvent.ToggleBlock(BlockType.BulletList), 0, 5)
        applyFormatBarEvent(text, FormatBarEvent.SelectSize(TextFormat.SizeLarge), 1, 4)
        LinkFormatter.setLink(text, "https://example.com", 0, 5)

        // Act
        val state = formatBarStateOf(text, 1, 4, FormatBarState(isUndoVisible = true))

        // Assert
        assertThat(state).isEqualTo(
            FormatBarState(
                activeFormats = setOf(TextFormat.Bold),
                size = TextFormat.SizeLarge,
                textColor = 0xd93025,
                block = BlockType.BulletList,
                isInLink = true,
                isUndoVisible = true,
            ),
        )
    }

    @Test
    fun `the state should show nothing for text without formatting`() {
        // Arrange
        val text = SpannableStringBuilder("Hello")

        // Act
        val state = formatBarStateOf(text, 2, 2, FormatBarState())

        // Assert
        assertThat(state).isEqualTo(FormatBarState())
    }

    @Test
    fun `the paragraph events should format the lines of the selection`() {
        // Arrange
        val text = SpannableStringBuilder("one\ntwo")

        // Act
        applyFormatBarEvent(text, FormatBarEvent.ToggleBlock(BlockType.NumberedList), 0, 7)
        applyFormatBarEvent(text, FormatBarEvent.ChangeLevel(1), 4, 4)
        applyFormatBarEvent(text, FormatBarEvent.SetAlignment(Alignment.Center), 0, 0)

        // Assert
        assertThat(RichTextFormatter.formattedText(text).paragraphs).containsExactly(
            ParagraphRange(0, 3, BlockType.NumberedList, 0, Alignment.Center),
            ParagraphRange(4, 7, BlockType.NumberedList, 1, Alignment.Start),
        )
    }

    @Test
    fun `clear formatting should take the character formats of the selection away`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")
        applyFormatBarEvent(text, FormatBarEvent.ToggleFormat(TextFormat.Italic), 0, 10)

        // Act
        applyFormatBarEvent(text, FormatBarEvent.ClearFormatting, 0, 5)

        // Assert
        assertThat(RichTextFormatter.formattedRanges(text)).containsExactly(FormattedRange(5, 10, TextFormat.Italic))
    }

    @Test
    fun `the events the bar does not format with should be left to the caller`() {
        // Arrange
        val text = SpannableStringBuilder("Hello")

        // Act
        val handled = listOf(
            FormatBarEvent.EditLink,
            FormatBarEvent.InsertImage(ImageSource.Gallery),
            FormatBarEvent.Undo,
        ).map { event -> applyFormatBarEvent(text, event, 0, 5) }

        // Assert
        assertThat(handled).containsExactly(false, false, false)
    }

    @Test
    fun `the colours should be distinct colours without alpha`() {
        // Arrange
        val colors = FormatBarColors.TEXT + FormatBarColors.HIGHLIGHT

        // Act
        val distinct = colors.map { it.color }.distinct()

        // Assert
        assertThat(distinct.size == colors.size).isTrue()
        assertThat(colors.any { it.color !in 0..0xFFFFFF }).isFalse()
    }
}
