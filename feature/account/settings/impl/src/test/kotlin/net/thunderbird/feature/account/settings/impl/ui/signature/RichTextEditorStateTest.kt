package net.thunderbird.feature.account.settings.impl.ui.signature

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

class RichTextEditorStateTest {

    @Test
    fun `toggle should format the selection`() {
        // Arrange
        val testSubject = RichTextEditorState(text = "Hello Jane", selectionStart = 6, selectionEnd = 10)

        // Act
        val result = testSubject.toggle(TextFormat.Bold)

        // Assert
        assertThat(result.ranges).containsExactly(FormattedRange(6, 10, TextFormat.Bold))
        assertThat(result.activeFormats).isEqualTo(setOf(TextFormat.Bold))
    }

    @Test
    fun `toggle should remove the format from a selection that has it completely`() {
        // Arrange
        val testSubject = RichTextEditorState(
            text = "Hello Jane",
            ranges = listOf(FormattedRange(0, 10, TextFormat.Bold)),
            selectionStart = 6,
            selectionEnd = 3,
        )

        // Act
        val result = testSubject.toggle(TextFormat.Bold)

        // Assert
        assertThat(result.ranges).containsExactly(
            FormattedRange(0, 3, TextFormat.Bold),
            FormattedRange(6, 10, TextFormat.Bold),
        )
    }

    @Test
    fun `toggle should format the whole selection when only a part of it has the format`() {
        // Arrange
        val testSubject = RichTextEditorState(
            text = "Hello Jane",
            ranges = listOf(FormattedRange(0, 5, TextFormat.Italic)),
            selectionStart = 3,
            selectionEnd = 10,
        )

        // Act
        val result = testSubject.toggle(TextFormat.Italic)

        // Assert
        assertThat(result.ranges).containsExactly(FormattedRange(0, 10, TextFormat.Italic))
    }

    @Test
    fun `typed text should continue the format of the text before the cursor`() {
        // Arrange
        val testSubject = RichTextEditorState(
            text = "Hello Jane",
            ranges = listOf(FormattedRange(6, 10, TextFormat.Bold)),
            selectionStart = 10,
            selectionEnd = 10,
        )

        // Act
        val result = testSubject.onTextFieldChange("Hello Jane!", 11, 11)

        // Assert
        assertThat(result.ranges).containsExactly(FormattedRange(6, 11, TextFormat.Bold))
    }

    @Test
    fun `text typed in front of formatted text should not get its format`() {
        // Arrange
        val testSubject = RichTextEditorState(
            text = "Hello Jane",
            ranges = listOf(FormattedRange(6, 10, TextFormat.Bold)),
            selectionStart = 6,
            selectionEnd = 6,
        )

        // Act
        val result = testSubject.onTextFieldChange("Hello dear Jane", 11, 11)

        // Assert
        assertThat(result.ranges).containsExactly(FormattedRange(11, 15, TextFormat.Bold))
    }

    @Test
    fun `toggle without selection should format what is typed next`() {
        // Arrange
        val testSubject = RichTextEditorState(text = "Hello ", selectionStart = 6, selectionEnd = 6)

        // Act
        val result = testSubject
            .toggle(TextFormat.Underline)
            .onTextFieldChange("Hello J", 7, 7)
            .onTextFieldChange("Hello Ja", 8, 8)

        // Assert
        assertThat(result.ranges).containsExactly(FormattedRange(6, 8, TextFormat.Underline))
        assertThat(result.activeFormats).isEqualTo(setOf(TextFormat.Underline))
    }

    @Test
    fun `toggle without selection should stop formatting what is typed next`() {
        // Arrange
        val testSubject = RichTextEditorState(
            text = "Hello Jane",
            ranges = listOf(FormattedRange(6, 10, TextFormat.Bold)),
            selectionStart = 10,
            selectionEnd = 10,
        )

        // Act
        val result = testSubject
            .toggle(TextFormat.Bold)
            .onTextFieldChange("Hello Jane!", 11, 11)

        // Assert
        assertThat(result.ranges).containsExactly(FormattedRange(6, 10, TextFormat.Bold))
        assertThat(result.activeFormats).isEmpty()
    }

    @Test
    fun `text typed inside formatted text after switching the format off should split it`() {
        // Arrange
        val testSubject = RichTextEditorState(
            text = "abcd",
            ranges = listOf(FormattedRange(0, 4, TextFormat.Bold)),
            selectionStart = 2,
            selectionEnd = 2,
        )

        // Act
        val result = testSubject
            .toggle(TextFormat.Bold)
            .onTextFieldChange("abXcd", 3, 3)

        // Assert
        assertThat(result.ranges).containsExactly(
            FormattedRange(0, 2, TextFormat.Bold),
            FormattedRange(3, 5, TextFormat.Bold),
        )
    }

    @Test
    fun `moving the cursor should forget a format that was switched on but not used`() {
        // Arrange
        val testSubject = RichTextEditorState(text = "Hello Jane", selectionStart = 10, selectionEnd = 10)

        // Act
        val result = testSubject
            .toggle(TextFormat.Bold)
            .onTextFieldChange("Hello Jane", 5, 5)

        // Assert
        assertThat(result.activeFormats).isEmpty()
    }

    @Test
    fun `deleting text should shrink and move the formatted ranges`() {
        // Arrange
        val testSubject = RichTextEditorState(
            text = "Hello dear Jane",
            ranges = listOf(
                FormattedRange(0, 8, TextFormat.Italic),
                FormattedRange(11, 15, TextFormat.Bold),
            ),
            selectionStart = 11,
            selectionEnd = 11,
        )

        // Act
        val result = testSubject.onTextFieldChange("Hello Jane", 6, 6)

        // Assert
        assertThat(result.ranges).containsExactly(
            FormattedRange(0, 6, TextFormat.Italic),
            FormattedRange(6, 10, TextFormat.Bold),
        )
    }

    @Test
    fun `a formatted word replaced by autocorrect should keep its format`() {
        // Arrange
        val testSubject = RichTextEditorState(
            text = "Hello Jnae now",
            ranges = listOf(FormattedRange(6, 10, TextFormat.Bold)),
            selectionStart = 10,
            selectionEnd = 10,
        )

        // Act
        val result = testSubject.onTextFieldChange("Hello Janet now", 11, 11)

        // Assert
        assertThat(result.ranges).containsExactly(FormattedRange(6, 11, TextFormat.Bold))
    }

    @Test
    fun `select should give the selection one size and replace the size it had`() {
        // Arrange
        val testSubject = RichTextEditorState(
            text = "Hello Jane",
            ranges = listOf(FormattedRange(0, 10, TextFormat.SizeSmall)),
            selectionStart = 6,
            selectionEnd = 10,
        )

        // Act
        val result = testSubject.select(TextFormat.Group.Size, TextFormat.SizeHuge)

        // Assert
        assertThat(result.ranges).containsExactly(
            FormattedRange(0, 6, TextFormat.SizeSmall),
            FormattedRange(6, 10, TextFormat.SizeHuge),
        )
        assertThat(result.activeFormat(TextFormat.Group.Size)).isEqualTo(TextFormat.SizeHuge)
    }

    @Test
    fun `select without a format should go back to the default and keep other formatting`() {
        // Arrange
        val testSubject = RichTextEditorState(
            text = "Hello Jane",
            ranges = listOf(
                FormattedRange(0, 10, TextFormat.Bold),
                FormattedRange(0, 10, TextFormat.FontSerif),
            ),
            selectionStart = 0,
            selectionEnd = 5,
        )

        // Act
        val result = testSubject.select(TextFormat.Group.Font, null)

        // Assert
        assertThat(result.ranges).containsExactly(
            FormattedRange(0, 10, TextFormat.Bold),
            FormattedRange(5, 10, TextFormat.FontSerif),
        )
        assertThat(result.activeFormat(TextFormat.Group.Font)).isEqualTo(null)
    }

    @Test
    fun `select without selection should set the size of what is typed next`() {
        // Arrange
        val testSubject = RichTextEditorState(
            text = "Hello ",
            ranges = listOf(FormattedRange(0, 6, TextFormat.SizeSmall)),
            selectionStart = 6,
            selectionEnd = 6,
        )

        // Act
        val result = testSubject
            .select(TextFormat.Group.Size, TextFormat.SizeLarge)
            .onTextFieldChange("Hello J", 7, 7)

        // Assert
        assertThat(result.ranges).containsExactly(
            FormattedRange(0, 6, TextFormat.SizeSmall),
            FormattedRange(6, 7, TextFormat.SizeLarge),
        )
    }
}
