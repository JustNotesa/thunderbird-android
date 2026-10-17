package com.fsck.k9.ui.compose.richtext

import android.text.SpannableStringBuilder
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import net.thunderbird.core.android.testing.RobolectricTest
import net.thunderbird.feature.mail.message.composer.richtext.ColorKind
import net.thunderbird.feature.mail.message.composer.richtext.ColorRange
import org.junit.Test

class ColorFormatterTest : RobolectricTest() {

    @Test
    fun `setColor should colour the text of the selection`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")

        // Act
        ColorFormatter.setColor(text, ColorKind.Text, 0xd93025, selectionStart = 6, selectionEnd = 10)

        // Assert
        assertThat(RichTextFormatter.formattedText(text).colors)
            .containsExactly(ColorRange(6, 10, ColorKind.Text, 0xd93025))
    }

    @Test
    fun `setColor should colour the background of the selection`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")

        // Act
        ColorFormatter.setColor(text, ColorKind.Highlight, 0xfde293, selectionStart = 6, selectionEnd = 10)

        // Assert
        assertThat(RichTextFormatter.formattedText(text).colors)
            .containsExactly(ColorRange(6, 10, ColorKind.Highlight, 0xfde293))
    }

    @Test
    fun `setColor should keep the colour of the text next to the selection`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")
        ColorFormatter.setColor(text, ColorKind.Text, 0xd93025, selectionStart = 0, selectionEnd = 10)

        // Act
        ColorFormatter.setColor(text, ColorKind.Text, null, selectionStart = 3, selectionEnd = 6)

        // Assert
        assertThat(RichTextFormatter.formattedText(text).colors).containsExactly(
            ColorRange(0, 3, ColorKind.Text, 0xd93025),
            ColorRange(6, 10, ColorKind.Text, 0xd93025),
        )
    }

    @Test
    fun `setColor should replace the colour of the same kind and keep the other one`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")
        ColorFormatter.setColor(text, ColorKind.Text, 0xd93025, selectionStart = 0, selectionEnd = 10)
        ColorFormatter.setColor(text, ColorKind.Highlight, 0xfde293, selectionStart = 0, selectionEnd = 10)

        // Act
        ColorFormatter.setColor(text, ColorKind.Text, 0x1a73e8, selectionStart = 0, selectionEnd = 10)

        // Assert
        assertThat(RichTextFormatter.formattedText(text).colors).containsExactly(
            ColorRange(0, 10, ColorKind.Text, 0x1a73e8),
            ColorRange(0, 10, ColorKind.Highlight, 0xfde293),
        )
    }

    @Test
    fun `setColor without selection should colour what is typed next`() {
        // Arrange
        val text = SpannableStringBuilder("Hello ")

        // Act
        ColorFormatter.setColor(text, ColorKind.Text, 0xd93025, selectionStart = 6, selectionEnd = 6)
        text.insert(6, "Jane")

        // Assert
        assertThat(RichTextFormatter.formattedText(text).colors)
            .containsExactly(ColorRange(6, 10, ColorKind.Text, 0xd93025))
    }

    @Test
    fun `activeColor should be the colour the whole selection has`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")
        ColorFormatter.setColor(text, ColorKind.Text, 0xd93025, selectionStart = 0, selectionEnd = 5)

        // Act
        val result = ColorFormatter.activeColor(text, ColorKind.Text, selectionStart = 0, selectionEnd = 5)

        // Assert
        assertThat(result).isEqualTo(0xd93025)
    }

    @Test
    fun `activeColor should be null when the selection has two colours`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")
        ColorFormatter.setColor(text, ColorKind.Text, 0xd93025, selectionStart = 0, selectionEnd = 5)
        ColorFormatter.setColor(text, ColorKind.Text, 0x1a73e8, selectionStart = 5, selectionEnd = 10)

        // Act
        val result = ColorFormatter.activeColor(text, ColorKind.Text, selectionStart = 0, selectionEnd = 10)

        // Assert
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `activeColor should be null when the selection has no colour`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")
        ColorFormatter.setColor(text, ColorKind.Highlight, 0xfde293, selectionStart = 0, selectionEnd = 10)

        // Act
        val result = ColorFormatter.activeColor(text, ColorKind.Text, selectionStart = 0, selectionEnd = 10)

        // Assert
        assertThat(result).isEqualTo(null)
    }
}
