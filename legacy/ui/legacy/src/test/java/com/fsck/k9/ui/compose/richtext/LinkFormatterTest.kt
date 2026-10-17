package com.fsck.k9.ui.compose.richtext

import android.text.SpannableStringBuilder
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import net.thunderbird.core.android.testing.RobolectricTest
import net.thunderbird.feature.mail.message.composer.richtext.LinkRange
import org.junit.Test

class LinkFormatterTest : RobolectricTest() {

    @Test
    fun `setLink should make the selection a link`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")

        // Act
        LinkFormatter.setLink(text, "https://example.com", start = 6, end = 10)

        // Assert
        assertThat(RichTextFormatter.formattedText(text).links)
            .containsExactly(LinkRange(6, 10, "https://example.com"))
        assertThat(LinkFormatter.linkAt(text, selectionStart = 7, selectionEnd = 9))
            .isEqualTo(LinkRange(6, 10, "https://example.com"))
    }

    @Test
    fun `setLink should replace the link that reaches into the selection`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")
        LinkFormatter.setLink(text, "https://example.com", start = 6, end = 8)

        // Act
        LinkFormatter.setLink(text, "https://example.org", start = 7, end = 10)

        // Assert
        assertThat(RichTextFormatter.formattedText(text).links)
            .containsExactly(LinkRange(7, 10, "https://example.org"))
    }

    @Test
    fun `linkAt should be null outside of a link`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")
        LinkFormatter.setLink(text, "https://example.com", start = 6, end = 10)

        // Act
        val result = LinkFormatter.linkAt(text, selectionStart = 0, selectionEnd = 3)

        // Assert
        assertThat(result).isEqualTo(null)
    }

    @Test
    fun `removeLink should remove the whole link the position is in`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")
        LinkFormatter.setLink(text, "https://example.com", start = 0, end = 5)
        LinkFormatter.setLink(text, "https://example.org", start = 6, end = 10)

        // Act
        LinkFormatter.removeLink(text, position = 3)

        // Assert
        assertThat(RichTextFormatter.formattedText(text).links)
            .containsExactly(LinkRange(6, 10, "https://example.org"))
    }

    @Test
    fun `typing at the end of a link should not extend the link`() {
        // Arrange
        val text = SpannableStringBuilder("Hello Jane")
        LinkFormatter.setLink(text, "https://example.com", start = 6, end = 10)

        // Act
        text.insert(10, "!")

        // Assert
        assertThat(RichTextFormatter.formattedText(text).links)
            .containsExactly(LinkRange(6, 10, "https://example.com"))
    }

    @Test
    fun `setLink should keep a link that only touches the new one`() {
        // Arrange
        val text = SpannableStringBuilder("HelloJane")
        LinkFormatter.setLink(text, "https://example.com", start = 0, end = 5)

        // Act
        LinkFormatter.setLink(text, "https://example.org", start = 5, end = 9)

        // Assert
        assertThat(RichTextFormatter.formattedText(text).links).containsExactly(
            LinkRange(0, 5, "https://example.com"),
            LinkRange(5, 9, "https://example.org"),
        )
    }
}
