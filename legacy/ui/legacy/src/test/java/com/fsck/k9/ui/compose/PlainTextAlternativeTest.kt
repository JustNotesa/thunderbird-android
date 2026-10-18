package com.fsck.k9.ui.compose

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange
import org.junit.Test

class PlainTextAlternativeTest {
    private fun formatted(text: String, vararg paragraphs: ParagraphRange) =
        FormattedText(text = text, ranges = emptyList(), paragraphs = paragraphs.toList())

    @Test
    fun `text without paragraph formatting should stay as it is`() {
        val testSubject = formatted("one\ntwo")

        assertThat(testSubject.toPlainTextAlternative()).isEqualTo("one\ntwo")
    }

    @Test
    fun `quote lines should start with one marker per level`() {
        val testSubject = formatted(
            "answer\nquoted\ndeeper",
            ParagraphRange(start = 7, end = 13, block = BlockType.Quote, level = 0),
            ParagraphRange(start = 14, end = 20, block = BlockType.Quote, level = 1),
        )

        assertThat(testSubject.toPlainTextAlternative()).isEqualTo("answer\n> quoted\n> > deeper")
    }

    @Test
    fun `an empty quote line should keep its marker without trailing space`() {
        val testSubject = formatted("a\n\nb", ParagraphRange(start = 0, end = 4, block = BlockType.Quote, level = 0))

        assertThat(testSubject.toPlainTextAlternative()).isEqualTo("> a\n>\n> b")
    }

    @Test
    fun `list items should get bullets and running numbers`() {
        val testSubject = formatted(
            "x\none\ntwo\ny\nfirst\nsub\nsecond",
            ParagraphRange(start = 2, end = 9, block = BlockType.BulletList),
            ParagraphRange(start = 12, end = 17, block = BlockType.NumberedList),
            ParagraphRange(start = 18, end = 21, block = BlockType.NumberedList, level = 1),
            ParagraphRange(start = 22, end = 28, block = BlockType.NumberedList),
        )

        assertThat(testSubject.toPlainTextAlternative())
            .isEqualTo("x\n• one\n• two\ny\n1. first\n  1. sub\n2. second")
    }

    @Test
    fun `the place of an inline image should be dropped`() {
        val testSubject = formatted("a￼b")

        assertThat(testSubject.toPlainTextAlternative()).isEqualTo("ab")
    }
}
