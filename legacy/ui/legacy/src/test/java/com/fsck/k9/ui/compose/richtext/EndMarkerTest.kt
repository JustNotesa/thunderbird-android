package com.fsck.k9.ui.compose.richtext

import android.text.Editable
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.thunderbird.core.android.testing.RobolectricTest
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

class EndMarkerTest : RobolectricTest() {

    @Test
    fun `Enter at the end of a bullet list should give the new item its own bullet`() {
        // Arrange
        val text = bulletListEditText()
        text.setSelection(3)

        // Act
        text.text.insert(3, "\n")

        // Assert
        assertThat(text.text.toString()).isEqualTo("one\n$END_MARKER")
        assertThat(text.text.bulletSpansOf(0)).hasSize(1)
        assertThat(text.text.bulletSpansOf(4)).hasSize(1)
    }

    @Test
    fun `the bullet of the new item should cover the end marker`() {
        // Arrange
        val text = bulletListEditText()
        text.setSelection(3)

        // Act
        text.text.insert(3, "\n")

        // Assert
        val bullets = text.text.bulletSpansOf(4)
        assertThat(text.text.getSpanStart(bullets.single())).isEqualTo(4)
        assertThat(text.text.getSpanEnd(bullets.single())).isEqualTo(5)
    }

    @Test
    fun `the cursor should stay in front of the end marker`() {
        // Arrange
        val text = bulletListEditText()
        text.setSelection(3)

        // Act
        text.text.insert(3, "\n")

        // Assert - the marker stands behind the cursor, so what is typed next belongs to the item.
        assertThat(text.selectionStart).isEqualTo(4)
        assertThat(text.selectionEnd).isEqualTo(4)
        assertThat(text.text.getOrNull(text.selectionEnd)).isEqualTo(END_MARKER)
    }

    @Test
    fun `typing on the empty item should take the end marker away`() {
        // Arrange
        val text = bulletListEditText()
        text.setSelection(3)
        text.text.insert(3, "\n")
        text.setSelection(4)
        val emptyItem = text.text.toString()

        // Act
        text.text.insert(4, "x")

        // Assert - the character became the item, and the marker it was typed in front of is gone.
        assertThat(emptyItem).isEqualTo("one\n$END_MARKER")
        assertThat(text.text.toString()).isEqualTo("one\nx")
        assertThat(text.text.bulletSpansOf(4)).hasSize(1)
        assertThat(ParagraphFormatter.paragraphs(text.text)).containsExactly(
            ParagraphRange(0, 5, BlockType.BulletList, 0, Alignment.Start),
        )
    }

    @Test
    fun `Enter on the empty item should end the list and take the end marker away`() {
        // Arrange
        val text = bulletListEditText()
        text.setSelection(3)
        text.text.insert(3, "\n")
        text.setSelection(4)

        // Act
        text.text.insert(4, "\n")

        // Assert
        assertThat(text.text.toString()).isEqualTo("one\n")
        assertThat(text.text.paragraphSpansAt(4)).isEmpty()
        assertThat(ParagraphFormatter.paragraphs(text.text)).containsExactly(
            ParagraphRange(0, 3, BlockType.BulletList, 0, Alignment.Start),
        )
    }

    @Test
    fun `formattedText should leave the end marker and the empty line behind it out`() {
        // Arrange
        val text = bulletListEditText()
        text.setSelection(3)
        text.text.insert(3, "\n")

        // Act
        val formattedText = RichTextFormatter.formattedText(text.text)

        // Assert - the editor holds the marker, the text of the message doesn't.
        assertThat(text.text.toString()).isEqualTo("one\n$END_MARKER")
        assertThat(formattedText.text).isEqualTo("one\n")
        assertThat(formattedText.paragraphs).containsExactly(
            ParagraphRange(0, 4, BlockType.BulletList, 0, Alignment.Start),
        )
    }

    @Test
    fun `toSpanned should give an empty item at the end of the text an end marker`() {
        // Arrange
        val formattedText = FormattedText(
            text = "one\n",
            ranges = emptyList(),
            paragraphs = listOf(
                ParagraphRange(0, 3, BlockType.BulletList, 0, Alignment.Start),
                ParagraphRange(4, 4, BlockType.BulletList, 0, Alignment.Start),
            ),
        )

        // Act
        val text = RichTextFormatter.toSpanned(formattedText)

        // Assert
        assertThat(text.toString()).isEqualTo("one\n$END_MARKER")
        assertThat((text as Editable).bulletSpansOf(4)).hasSize(1)
        assertThat(RichTextFormatter.formattedText(text).paragraphs).containsExactly(
            ParagraphRange(0, 4, BlockType.BulletList, 0, Alignment.Start),
        )
    }

    @Test
    fun `withoutEndMarkers should move what is behind a marker with the text`() {
        // Arrange
        val formattedText = FormattedText(
            text = "on${END_MARKER}e",
            ranges = listOf(FormattedRange(1, 5, TextFormat.Bold), FormattedRange(2, 3, TextFormat.Italic)),
            colors = listOf(ColorRange(0, 3, ColorKind.Text, 0xFF0000.toInt())),
            links = listOf(LinkRange(1, 4, "https://example.com")),
            paragraphs = listOf(ParagraphRange(0, 5)),
            images = listOf(InlineImage(3, "image@example.com")),
        )

        // Act
        val withoutMarkers = formattedText.withoutEndMarkers()

        // Assert
        assertThat(withoutMarkers.text).isEqualTo("one")
        assertThat(withoutMarkers.ranges).containsExactly(FormattedRange(1, 4, TextFormat.Bold))
        assertThat(withoutMarkers.colors).containsExactly(ColorRange(0, 2, ColorKind.Text, 0xFF0000.toInt()))
        assertThat(withoutMarkers.links).containsExactly(LinkRange(1, 3, "https://example.com"))
        assertThat(withoutMarkers.paragraphs).containsExactly(ParagraphRange(0, 4))
        assertThat(withoutMarkers.images).containsExactly(InlineImage(2, "image@example.com"))
    }

    @Test
    fun `an empty last line with an alignment should get an end marker`() {
        // Arrange
        val text = editText()
        text.text.insert(0, "one\n")

        // Act
        ParagraphFormatter.setAlignment(text.text, Alignment.Center, 4, 4)

        // Assert
        assertThat(text.text.toString()).isEqualTo("one\n$END_MARKER")
        assertThat(ParagraphFormatter.paragraphs(text.text)).containsExactly(
            ParagraphRange(4, 5, BlockType.Normal, 0, Alignment.Center),
        )
    }

    @Test
    fun `an empty last line without formatting should not get an end marker`() {
        // Arrange
        val text = editText()

        // Act
        text.text.insert(0, "one\n")

        // Assert
        assertThat(text.text.toString()).isEqualTo("one\n")
        assertThat(text.text.bulletSpansOf(0)).isEmpty()
    }

    private fun editText(): EditText {
        val editText = EditText(ApplicationProvider.getApplicationContext())
        editText.addTextChangedListener(ParagraphEditWatcher { true })

        return editText
    }

    /**
     * A text field holding "one" as the single item of a bullet list.
     */
    private fun bulletListEditText(): EditText {
        val editText = editText()
        val text: Editable = editText.text
        text.insert(0, "one")
        ParagraphFormatter.applyParagraphs(text, listOf(ParagraphRange(0, 3, BlockType.BulletList, 0, Alignment.Start)))

        return editText
    }

    /**
     * The bullet spans Android's layout draws the line that starts at [lineStart] with: it counts the line together
     * with the line break behind it, where the spans of the line after it start.
     */
    private fun Editable.bulletSpansOf(lineStart: Int): List<BulletListSpan> {
        val lineEnd = lineEndAt(lineStart)
        val end = if (lineEnd < length) lineEnd + 1 else lineEnd

        return getSpans(lineStart, end, BulletListSpan::class.java).toList()
    }
}
