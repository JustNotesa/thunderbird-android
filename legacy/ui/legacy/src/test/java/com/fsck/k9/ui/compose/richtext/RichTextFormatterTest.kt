package com.fsck.k9.ui.compose.richtext

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.thunderbird.core.android.testing.RobolectricTest
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat
import org.junit.Test

class RichTextFormatterTest : RobolectricTest() {

    @Test
    fun `toggle should format the selection`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")

        // Act
        RichTextFormatter.toggle(text, TextFormat.Bold, selectionStart = 6, selectionEnd = 10)

        // Assert
        assertThat(RichTextFormatter.formattedRanges(text)).containsExactly(FormattedRange(6, 10, TextFormat.Bold))
        assertThat(RichTextFormatter.isActive(text, TextFormat.Bold, 6, 10)).isTrue()
        assertThat(RichTextFormatter.isActive(text, TextFormat.Italic, 6, 10)).isFalse()
    }

    @Test
    fun `toggle should handle a selection made from right to left`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")

        // Act
        RichTextFormatter.toggle(text, TextFormat.Italic, selectionStart = 10, selectionEnd = 6)

        // Assert
        assertThat(RichTextFormatter.formattedRanges(text)).containsExactly(FormattedRange(6, 10, TextFormat.Italic))
    }

    @Test
    fun `toggle should remove the format from a selection that has it completely`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")
        RichTextFormatter.toggle(text, TextFormat.Bold, selectionStart = 0, selectionEnd = 10)

        // Act
        RichTextFormatter.toggle(text, TextFormat.Bold, selectionStart = 3, selectionEnd = 6)

        // Assert
        assertThat(RichTextFormatter.formattedRanges(text)).containsExactly(
            FormattedRange(0, 3, TextFormat.Bold),
            FormattedRange(6, 10, TextFormat.Bold),
        )
    }

    @Test
    fun `toggle should format the whole selection when only a part of it has the format`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")
        RichTextFormatter.toggle(text, TextFormat.Bold, selectionStart = 0, selectionEnd = 5)

        // Act
        RichTextFormatter.toggle(text, TextFormat.Bold, selectionStart = 3, selectionEnd = 10)

        // Assert
        assertThat(RichTextFormatter.formattedRanges(text)).containsExactly(FormattedRange(0, 10, TextFormat.Bold))
    }

    @Test
    fun `formats should not affect each other`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")
        RichTextFormatter.toggle(text, TextFormat.Bold, selectionStart = 0, selectionEnd = 10)
        RichTextFormatter.toggle(text, TextFormat.Underline, selectionStart = 6, selectionEnd = 10)

        // Act
        RichTextFormatter.toggle(text, TextFormat.Bold, selectionStart = 6, selectionEnd = 10)

        // Assert
        assertThat(RichTextFormatter.formattedRanges(text)).containsExactly(
            FormattedRange(0, 6, TextFormat.Bold),
            FormattedRange(6, 10, TextFormat.Underline),
        )
    }

    @Test
    fun `toggle without selection should format what is typed next`() {
        // Arrange
        val text = SpannableStringBuilder("Hello ")

        // Act
        RichTextFormatter.toggle(text, TextFormat.Bold, selectionStart = 6, selectionEnd = 6)
        val activeBeforeTyping = RichTextFormatter.isActive(text, TextFormat.Bold, 6, 6)
        text.insert(6, "Jane")

        // Assert
        assertThat(activeBeforeTyping).isTrue()
        assertThat(RichTextFormatter.formattedRanges(text)).containsExactly(FormattedRange(6, 10, TextFormat.Bold))
    }

    @Test
    fun `toggle without selection at the end of formatted text should stop formatting what is typed next`() {
        // Arrange
        val text = SpannableStringBuilder("Hello ")
        RichTextFormatter.toggle(text, TextFormat.Bold, selectionStart = 6, selectionEnd = 6)
        text.insert(6, "Jane")

        // Act
        RichTextFormatter.toggle(text, TextFormat.Bold, selectionStart = 10, selectionEnd = 10)
        val activeBeforeTyping = RichTextFormatter.isActive(text, TextFormat.Bold, 10, 10)
        text.insert(10, ", hi")

        // Assert
        assertThat(activeBeforeTyping).isFalse()
        assertThat(RichTextFormatter.formattedRanges(text)).containsExactly(FormattedRange(6, 10, TextFormat.Bold))
    }

    @Test
    fun `typing at the end of a formatted selection should continue the format`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")
        RichTextFormatter.toggle(text, TextFormat.Italic, selectionStart = 6, selectionEnd = 10)

        // Act
        text.insert(10, "!")

        // Assert
        assertThat(RichTextFormatter.isActive(text, TextFormat.Italic, 11, 11)).isTrue()
        assertThat(RichTextFormatter.formattedRanges(text)).containsExactly(FormattedRange(6, 11, TextFormat.Italic))
    }

    @Test
    fun `formattedRanges should ignore the underline of the word the keyboard is composing`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")
        text.setSpan(UnderlineSpan(), 6, 10, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_COMPOSING)

        // Act
        val result = RichTextFormatter.formattedRanges(text)

        // Assert
        assertThat(result).isEmpty()
    }

    @Test
    fun `formattedRanges should read a bold italic span as both formats`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")
        text.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Act
        val result = RichTextFormatter.formattedRanges(text)

        // Assert
        assertThat(result).containsExactly(
            FormattedRange(0, 5, TextFormat.Bold),
            FormattedRange(0, 5, TextFormat.Italic),
        )
    }

    @Test
    fun `toSpanned should show formatted text that reads back as the same ranges`() {
        // Arrange
        val formattedText = FormattedText(
            text = "Hello Jane",
            ranges = listOf(
                FormattedRange(0, 5, TextFormat.Bold),
                FormattedRange(3, 10, TextFormat.Underline),
                FormattedRange(8, 99, TextFormat.Italic),
            ),
        )

        // Act
        val result = RichTextFormatter.toSpanned(formattedText)

        // Assert
        assertThat(result.toString()).isEqualTo("Hello Jane")
        assertThat(RichTextFormatter.formattedRanges(result)).containsExactly(
            FormattedRange(0, 5, TextFormat.Bold),
            FormattedRange(8, 10, TextFormat.Italic),
            FormattedRange(3, 10, TextFormat.Underline),
        )
    }
}
