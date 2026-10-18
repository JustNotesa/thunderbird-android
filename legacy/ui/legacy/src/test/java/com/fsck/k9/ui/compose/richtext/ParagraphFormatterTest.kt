package com.fsck.k9.ui.compose.richtext

import android.text.Editable
import android.text.SpannableStringBuilder
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isTrue
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.thunderbird.core.android.testing.RobolectricTest
import net.thunderbird.feature.mail.message.composer.richtext.Alignment
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange
import org.junit.Test

class ParagraphFormatterTest : RobolectricTest() {

    @Test
    fun `setBlock should make every line of the selection a list`() {
        // Arrange
        val text = SpannableStringBuilder("one\ntwo\nthree")

        // Act
        ParagraphFormatter.setBlock(text, BlockType.BulletList, 0, 13)

        // Assert
        assertThat(ParagraphFormatter.paragraphs(text)).containsExactly(
            ParagraphRange(0, 13, BlockType.BulletList, 0, Alignment.Start),
        )
    }

    @Test
    fun `setBlock should take the block away when the selection has it completely`() {
        // Arrange
        val text = SpannableStringBuilder("one\ntwo\nthree")
        ParagraphFormatter.setBlock(text, BlockType.BulletList, 0, 13)

        // Act
        ParagraphFormatter.setBlock(text, BlockType.BulletList, 0, 13)

        // Assert
        assertThat(ParagraphFormatter.paragraphs(text)).isEmpty()
    }

    @Test
    fun `setBlock should set the block when only a part of the selection has it`() {
        // Arrange
        val text = SpannableStringBuilder("one\ntwo\nthree")
        ParagraphFormatter.setBlock(text, BlockType.BulletList, 0, 3)

        // Act
        ParagraphFormatter.setBlock(text, BlockType.BulletList, 0, 13)

        // Assert
        assertThat(ParagraphFormatter.paragraphs(text)).containsExactly(
            ParagraphRange(0, 13, BlockType.BulletList, 0, Alignment.Start),
        )
    }

    @Test
    fun `setBlock should keep the level of the line`() {
        // Arrange
        val text = SpannableStringBuilder("one\ntwo")
        ParagraphFormatter.setBlock(text, BlockType.BulletList, 0, 7)
        ParagraphFormatter.changeLevel(text, delta = 2, selectionStart = 0, selectionEnd = 3)

        // Act
        ParagraphFormatter.setBlock(text, BlockType.Quote, 0, 3)

        // Assert
        assertThat(ParagraphFormatter.paragraphs(text)).containsExactly(
            ParagraphRange(0, 3, BlockType.Quote, 2, Alignment.Start),
            ParagraphRange(4, 7, BlockType.BulletList, 0, Alignment.Start),
        )
    }

    @Test
    fun `changeLevel should not go beyond the deepest level`() {
        // Arrange
        val text = SpannableStringBuilder("one")
        ParagraphFormatter.setBlock(text, BlockType.NumberedList, 0, 3)

        // Act
        ParagraphFormatter.changeLevel(text, delta = 9, selectionStart = 0, selectionEnd = 3)

        // Assert
        assertThat(ParagraphFormatter.paragraphs(text)).containsExactly(
            ParagraphRange(0, 3, BlockType.NumberedList, ParagraphRange.MAX_LEVEL, Alignment.Start),
        )
    }

    @Test
    fun `changeLevel should end a list when the outermost item is outdented`() {
        // Arrange
        val text = SpannableStringBuilder("one")
        ParagraphFormatter.setBlock(text, BlockType.BulletList, 0, 3)

        // Act
        ParagraphFormatter.changeLevel(text, delta = -1, selectionStart = 0, selectionEnd = 3)

        // Assert
        assertThat(ParagraphFormatter.paragraphs(text)).isEmpty()
    }

    @Test
    fun `changeLevel should keep the list when a nested item is outdented`() {
        // Arrange
        val text = SpannableStringBuilder("one")
        ParagraphFormatter.setBlock(text, BlockType.BulletList, 0, 3)
        ParagraphFormatter.changeLevel(text, delta = 1, selectionStart = 0, selectionEnd = 3)

        // Act
        ParagraphFormatter.changeLevel(text, delta = -1, selectionStart = 0, selectionEnd = 3)

        // Assert
        assertThat(ParagraphFormatter.paragraphs(text)).containsExactly(
            ParagraphRange(0, 3, BlockType.BulletList, 0, Alignment.Start),
        )
    }

    @Test
    fun `changeLevel should indent the line it is called for`() {
        // Arrange
        val text = SpannableStringBuilder("one\ntwo")

        // Act
        ParagraphFormatter.changeLevel(text, delta = 1, selectionStart = 4, selectionEnd = 7)

        // Assert
        assertThat(ParagraphFormatter.paragraphs(text)).containsExactly(
            ParagraphRange(4, 7, BlockType.Normal, 1, Alignment.Start),
        )
    }

    @Test
    fun `setAlignment should align every line of the selection`() {
        // Arrange
        val text = SpannableStringBuilder("one\ntwo")

        // Act
        ParagraphFormatter.setAlignment(text, Alignment.Center, 0, 7)

        // Assert
        assertThat(ParagraphFormatter.paragraphs(text)).containsExactly(
            ParagraphRange(0, 7, BlockType.Normal, 0, Alignment.Center),
        )
    }

    @Test
    fun `paragraphAt should give the formatting of the first line of the selection`() {
        // Arrange
        val text = SpannableStringBuilder("one\ntwo\nthree")
        ParagraphFormatter.setBlock(text, BlockType.BulletList, 0, 3)
        ParagraphFormatter.setAlignment(text, Alignment.End, 0, 3)

        // Act
        val paragraphOfListLine = ParagraphFormatter.paragraphAt(text, selectionStart = 0, selectionEnd = 3)
        val paragraphOfSecondLine = ParagraphFormatter.paragraphAt(text, selectionStart = 4, selectionEnd = 7)

        // Assert
        assertThat(paragraphOfListLine).isEqualTo(ParagraphRange(0, 3, BlockType.BulletList, 0, Alignment.End))
        assertThat(paragraphOfSecondLine).isEqualTo(ParagraphRange(4, 7, BlockType.Normal, 0, Alignment.Start))
    }

    @Test
    fun `paragraphs should merge the lines that share their formatting`() {
        // Arrange
        val text = SpannableStringBuilder("one\ntwo\nthree\nfour")
        ParagraphFormatter.setBlock(text, BlockType.BulletList, 0, 7)
        ParagraphFormatter.setAlignment(text, Alignment.End, 8, 12)

        // Act
        val paragraphs = ParagraphFormatter.paragraphs(text)

        // Assert
        assertThat(paragraphs).containsExactly(
            ParagraphRange(0, 7, BlockType.BulletList, 0, Alignment.Start),
            ParagraphRange(8, 13, BlockType.Normal, 0, Alignment.End),
        )
    }

    @Test
    fun `formattedText and toSpanned should keep the paragraphs`() {
        // Arrange
        val formattedText = FormattedText(
            text = "one\ntwo\nthree",
            ranges = emptyList(),
            paragraphs = listOf(
                ParagraphRange(0, 7, BlockType.BulletList, 1, Alignment.Start),
                ParagraphRange(8, 13, BlockType.Normal, 0, Alignment.Center),
            ),
        )

        // Act
        val text = RichTextFormatter.toSpanned(formattedText)

        // Assert
        assertThat(RichTextFormatter.formattedText(text).paragraphs).containsExactly(
            ParagraphRange(0, 7, BlockType.BulletList, 1, Alignment.Start),
            ParagraphRange(8, 13, BlockType.Normal, 0, Alignment.Center),
        )
    }

    @Test
    fun `clearFormatting should leave the paragraphs alone`() {
        // Arrange
        val text = SpannableStringBuilder("one\ntwo")
        ParagraphFormatter.setBlock(text, BlockType.Quote, 0, 7)

        // Act
        RichTextFormatter.clearFormatting(text, 0, 7)

        // Assert
        assertThat(ParagraphFormatter.paragraphs(text)).containsExactly(
            ParagraphRange(0, 7, BlockType.Quote, 0, Alignment.Start),
        )
    }

    @Test
    fun `Enter should continue a bullet list`() {
        // Arrange
        val text = bulletListEditText()

        // Act
        text.text.insert(3, "\n")

        // Assert
        // Both lines are items of the list; the model keeps lines that share their formatting as one range. The empty
        // line at the end carries its formatting on the end marker.
        assertThat(text.text.toString()).isEqualTo("one\n$END_MARKER")
        assertThat(ParagraphFormatter.paragraphs(text.text)).containsExactly(
            ParagraphRange(0, 5, BlockType.BulletList, 0, Alignment.Start),
        )
    }

    @Test
    fun `Enter should continue a numbered list with its level`() {
        // Arrange
        val text = bulletListEditText(BlockType.NumberedList, level = 2)

        // Act
        text.text.insert(3, "\n")

        // Assert
        assertThat(ParagraphFormatter.paragraphs(text.text)).containsExactly(
            ParagraphRange(0, 5, BlockType.NumberedList, 2, Alignment.Start),
        )
    }

    @Test
    fun `Enter on an empty item should end the list`() {
        // Arrange
        val text = bulletListEditText()
        text.text.insert(3, "\n")

        // Act
        text.text.insert(4, "\n")

        // Assert
        assertThat(text.text.toString()).isEqualTo("one\n")
        assertThat(ParagraphFormatter.paragraphs(text.text)).containsExactly(
            ParagraphRange(0, 3, BlockType.BulletList, 0, Alignment.Start),
        )
        assertThat(text.text.paragraphSpansAt(4)).isEmpty()
    }

    @Test
    fun `Enter on an empty quote line should end the quote`() {
        // Arrange
        val text = bulletListEditText(BlockType.Quote)
        text.text.insert(3, "\n")

        // Act
        text.text.insert(4, "\n")

        // Assert
        assertThat(text.text.toString()).isEqualTo("one\n")
        assertThat(text.text.paragraphSpansAt(4)).isEmpty()
    }

    @Test
    fun `Backspace at the start of an item should take the block away first`() {
        // Arrange
        val text = bulletListEditText()
        text.text.insert(3, "\n")

        // Act
        text.text.delete(3, 4)

        // Assert
        assertThat(text.text.toString()).isEqualTo("one\n")
        assertThat(text.text.paragraphSpansAt(4)).isEmpty()
        assertThat(ParagraphFormatter.paragraphs(text.text)).containsExactly(
            ParagraphRange(0, 3, BlockType.BulletList, 0, Alignment.Start),
        )
    }

    @Test
    fun `Backspace at the start of the text should take the block away`() {
        // Arrange
        val text = bulletListEditText()

        // Act
        val removed = ParagraphFormatter.removeBlockAtLineStart(text.text, cursor = 0)

        // Assert
        assertThat(removed).isTrue()
        assertThat(text.text.toString()).isEqualTo("one")
        assertThat(ParagraphFormatter.paragraphs(text.text)).isEmpty()
    }

    @Test
    fun `Backspace that joins a normal line should delete as usual`() {
        // Arrange
        val text = editText()
        text.text.insert(0, "one\ntwo")
        ParagraphFormatter.applyParagraphs(text.text, listOf(ParagraphRange(0, 3, BlockType.BulletList)))

        // Act
        text.text.delete(3, 4)

        // Assert
        assertThat(text.text.toString()).isEqualTo("onetwo")
        assertThat(ParagraphFormatter.paragraphs(text.text)).containsExactly(
            ParagraphRange(0, 6, BlockType.BulletList, 0, Alignment.Start),
        )
    }

    @Test
    fun `typing a bullet marker at the start of a line should start a bullet list`() {
        // Arrange
        val text = editText()

        // Act
        text.text.insert(0, "-")
        text.text.insert(1, " ")
        text.text.insert(0, "two")

        // Assert
        assertThat(text.text.toString()).isEqualTo("two")
        assertThat(ParagraphFormatter.paragraphs(text.text)).containsExactly(
            ParagraphRange(0, 3, BlockType.BulletList, 0, Alignment.Start),
        )
    }

    @Test
    fun `typing an asterisk at the start of a line should start a bullet list`() {
        // Arrange
        val text = editText()

        // Act
        text.text.insert(0, "*")
        text.text.insert(1, " ")

        // Assert
        assertThat(ParagraphFormatter.paragraphs(text.text)).containsExactly(
            ParagraphRange(0, 1, BlockType.BulletList, 0, Alignment.Start),
        )
    }

    @Test
    fun `typing a number marker at the start of a line should start a numbered list`() {
        // Arrange
        val text = editText()

        // Act
        text.text.insert(0, "1")
        text.text.insert(1, ".")
        text.text.insert(2, " ")
        text.text.insert(0, "two")

        // Assert
        assertThat(text.text.toString()).isEqualTo("two")
        assertThat(ParagraphFormatter.paragraphs(text.text)).containsExactly(
            ParagraphRange(0, 3, BlockType.NumberedList, 0, Alignment.Start),
        )
    }

    @Test
    fun `typing a quote marker at the start of a line should start a quote`() {
        // Arrange
        val text = editText()

        // Act
        text.text.insert(0, ">")
        text.text.insert(1, " ")
        text.text.insert(0, "two")

        // Assert
        assertThat(text.text.toString()).isEqualTo("two")
        assertThat(ParagraphFormatter.paragraphs(text.text)).containsExactly(
            ParagraphRange(0, 3, BlockType.Quote, 0, Alignment.Start),
        )
    }

    @Test
    fun `typing a marker that is not at the start of the line should do nothing`() {
        // Arrange
        val text = editText()

        // Act
        text.text.insert(0, "one- ")

        // Assert
        assertThat(text.text.toString()).isEqualTo("one- ")
        assertThat(ParagraphFormatter.paragraphs(text.text)).isEmpty()
    }

    @Test
    fun `the line start shortcuts should do nothing when they are disabled`() {
        // Arrange
        val text = editText(isEnabled = false)

        // Act
        text.text.insert(0, "-")
        text.text.insert(1, " ")
        text.text.insert(2, "\n")

        // Assert
        assertThat(text.text.toString()).isEqualTo("- \n")
        assertThat(ParagraphFormatter.paragraphs(text.text)).isEmpty()
    }

    @Test
    fun `text pasted into a list should stay in the list`() {
        // Arrange
        val text = bulletListEditText()

        // Act
        text.text.insert(3, "two\nthree")

        // Assert
        assertThat(text.text.toString()).isEqualTo("onetwo\nthree")
        assertThat(ParagraphFormatter.paragraphs(text.text)).containsExactly(
            ParagraphRange(0, 12, BlockType.BulletList, 0, Alignment.Start),
        )
    }

    private fun editText(isEnabled: Boolean = true): EditText {
        val editText = EditText(ApplicationProvider.getApplicationContext())
        editText.addTextChangedListener(ParagraphEditWatcher { isEnabled })

        return editText
    }

    /**
     * A text field holding "one" as the single item of a list of [block] on nesting [level].
     */
    private fun bulletListEditText(block: BlockType = BlockType.BulletList, level: Int = 0): EditText {
        val editText = editText()
        val text: Editable = editText.text
        text.insert(0, "one")
        ParagraphFormatter.applyParagraphs(text, listOf(ParagraphRange(0, 3, block, level, Alignment.Start)))

        return editText
    }
}
