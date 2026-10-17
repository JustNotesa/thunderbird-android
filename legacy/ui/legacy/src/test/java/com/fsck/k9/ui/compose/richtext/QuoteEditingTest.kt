package com.fsck.k9.ui.compose.richtext

import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.ui.compose.QuoteMerger
import net.thunderbird.core.android.testing.RobolectricTest
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.LinkRange
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat
import org.junit.Test

/**
 * Typing into a quote that was written into the text of the message, as editing the quote of a reply does.
 */
class QuoteEditingTest : RobolectricTest() {
    @Test
    fun `an answer typed between two quoted lines should leave the lines below it quoted once`() {
        // Arrange
        val editText = EditText(ApplicationProvider.getApplicationContext())
        editText.addTextChangedListener(ParagraphEditWatcher { true })
        editText.addTextChangedListener(PasteNormalizer())
        val quote = FormattedText(
            text = "Hello Jane\nSecond\nFirst\nSecond item\nLast",
            ranges = listOf(FormattedRange(6, 10, TextFormat.Bold)),
            links = listOf(LinkRange(0, 5, "https://example.com")),
            paragraphs = listOf(ParagraphRange(18, 36, BlockType.BulletList, 0)),
        )
        val merged = QuoteMerger.merge(FormattedText("Intro", emptyList()), "On x wrote:", quote, false)
        editText.setText(RichTextFormatter.toSpanned(merged.text))
        val end = editText.text.indexOf("Second\n") + "Second".length
        editText.setSelection(end)
        val input = editText.onCreateInputConnection(EditorInfo())

        // Act
        input.commitText("\n", 1)
        input.commitText("\n", 1)
        "Inline".forEach { input.commitText(it.toString(), 1) }

        // Assert
        val text = editText.text
        // Counted as the spans themselves: a line with two of them would be drawn and indented twice.
        val counts = text.toString().split("\n").runningFold(0) { s, l -> s + l.length + 1 }.dropLast(1)
            .map { start ->
                text.getSpans(0, text.length, QuoteBlockSpan::class.java).count { text.getSpanStart(it) == start }
            }
        
        assertThat(counts).isEqualTo(listOf(0, 0, 0, 1, 1, 0, 1, 1, 1))
    }
}
