package com.fsck.k9.ui.compose.richtext

import android.text.style.AlignmentSpan
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.ParagraphStyle
import assertk.assertThat
import assertk.assertions.isTrue
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.thunderbird.core.android.testing.RobolectricTest
import net.thunderbird.feature.mail.message.composer.richtext.Alignment
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange
import org.junit.Test

private const val TEST_DENSITY = 2f

class ParagraphSpansTest : RobolectricTest() {

    @Test
    fun `the paragraph spans should indent their line`() {
        // Arrange
        val spans = listOf<LeadingMarginSpan>(
            BulletListSpan(1, TEST_DENSITY),
            NumberedListSpan(1, TEST_DENSITY),
            QuoteBlockSpan(1, TEST_DENSITY),
            IndentSpan(1, TEST_DENSITY),
        )

        // Act
        val classes = spans.map { span -> span::class.java }

        // Assert
        assertThat(classes).containsExactly(
            BulletListSpan::class.java,
            NumberedListSpan::class.java,
            QuoteBlockSpan::class.java,
            IndentSpan::class.java,
        )
        assertThat(spans.all { span -> span is ParagraphStyle }).isTrue()
        assertThat(spans.map { span -> span.getLeadingMargin(true) }).containsExactly(96, 96, 96, 48)
    }

    @Test
    fun `the indent of a list item should grow with its level`() {
        // Act
        val margins = (0..2).map { level -> BulletListSpan(level, TEST_DENSITY).getLeadingMargin(true) }

        // Assert
        assertThat(margins).containsExactly(48, 96, 144)
    }

    @Test
    fun `the indent of a quote block should grow with its level`() {
        // Act
        val margins = (0..1).map { level -> QuoteBlockSpan(level, TEST_DENSITY).getLeadingMargin(true) }

        // Assert
        assertThat(margins).containsExactly(48, 96)
    }

    @Test
    fun `the indent of a normal paragraph should be its level`() {
        // Act
        val margins = (0..2).map { level -> IndentSpan(level, TEST_DENSITY).getLeadingMargin(true) }

        // Assert
        assertThat(margins).containsExactly(0, 48, 96)
    }

    @Test
    fun `a line should carry exactly one set of paragraph spans`() {
        // Arrange
        val text = SpannableStringBuilder("one\ntwo")
        ParagraphFormatter.setBlock(text, BlockType.BulletList, 0, text.length)
        ParagraphFormatter.setAlignment(text, Alignment.Center, 0, text.length)

        // Act
        ParagraphFormatter.setBlock(text, BlockType.Normal, 0, text.length)

        // Assert: the list is gone, and each line keeps one alignment span.
        val spans = text.paragraphSpans(0, text.length)
        assertThat(spans.filterIsInstance<LeadingMarginSpan>()).isEmpty()
        val alignments = spans.filterIsInstance<AlignmentSpan.Standard>()
        assertThat(alignments.map { text.getSpanStart(it) to text.getSpanEnd(it) }).containsExactly(0 to 4, 4 to 7)
    }

    @Test
    fun `changing one line should leave the paragraph spans of the other lines alone`() {
        // Arrange
        val text = SpannableStringBuilder("one\ntwo")
        ParagraphFormatter.setBlock(text, BlockType.BulletList, 0, text.length)

        // Act
        ParagraphFormatter.setBlock(text, BlockType.Quote, 4, 7)

        // Assert
        assertThat(ParagraphFormatter.paragraphs(text)).containsExactly(
            ParagraphRange(0, 3, BlockType.BulletList, 0, Alignment.Start),
            ParagraphRange(4, 7, BlockType.Quote, 0, Alignment.Start),
        )
    }

    @Test
    fun `the number of a list item should count the items above it`() {
        // Arrange
        val text = numberedList(listOf(0, 0, 0))

        // Act
        val numbers = (0..2).map { index -> text.numberedItemNumber(lineStart(index), 0) }

        // Assert
        assertThat(numbers).containsExactly(1, 2, 3)
    }

    @Test
    fun `the number of a list item should skip the lines nested deeper`() {
        // Arrange
        val text = numberedList(listOf(0, 1, 0))

        // Act
        val numbers = listOf(0, 1, 0).mapIndexed { index, level ->
            text.numberedItemNumber(lineStart(index), level)
        }

        // Assert
        assertThat(numbers).containsExactly(1, 1, 2)
    }

    @Test
    fun `the number of a list item should stop at a line that is not part of the list`() {
        // Arrange
        val text = numberedList(listOf(0, null, 0))

        // Act
        val number = text.numberedItemNumber(lineStart(2), 0)

        // Assert
        assertThat(number).isEqualTo(1)
    }

    @Test
    fun `the number of a list item should stop at a level above its own`() {
        // Arrange
        val text = numberedList(listOf(1, 0))

        // Act
        val number = text.numberedItemNumber(lineStart(1), 0)

        // Assert
        assertThat(number).isEqualTo(1)
    }

    /**
     * The lines of [LINES], each with a [NumberedListSpan] of the level at its place, `null` for a line without one.
     */
    private fun numberedList(levels: List<Int?>): SpannableStringBuilder {
        val text = SpannableStringBuilder(LINES.joinToString("\n"))
        for ((index, level) in levels.withIndex()) {
            if (level != null) {
                val start = lineStart(index)
                text.setSpan(NumberedListSpan(level, TEST_DENSITY), start, start, Spanned.SPAN_PARAGRAPH)
            }
        }

        return text
    }

    private fun lineStart(index: Int): Int {
        return index * (LINES[0].length + 1)
    }

    companion object {
        private val LINES = listOf("one", "two", "three")
    }
}
