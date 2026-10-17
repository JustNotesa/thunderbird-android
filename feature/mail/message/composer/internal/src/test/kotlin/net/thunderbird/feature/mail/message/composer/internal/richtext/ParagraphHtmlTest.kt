package net.thunderbird.feature.mail.message.composer.internal.richtext

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.feature.mail.message.composer.richtext.Alignment
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.ColorKind
import net.thunderbird.feature.mail.message.composer.richtext.ColorRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.LinkRange
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange
import net.thunderbird.feature.mail.message.composer.richtext.RichTextHtmlConverter.LostContent
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

/**
 * The paragraphs of a message: the lists, the quotes, the indentation and the alignment the user has chosen, and
 * the HTML they are written as.
 */
class ParagraphHtmlTest {
    private val testSubject = DefaultRichTextHtmlConverter()

    @Test
    fun `should write a bullet list`() {
        // Arrange
        val formattedText = FormattedText(
            text = "a\nb",
            ranges = emptyList(),
            paragraphs = listOf(ParagraphRange(0, 3, BlockType.BulletList)),
        )

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo("""<div dir="auto"><ul><li>a</li><li>b</li></ul></div>""")
    }

    @Test
    fun `should write a numbered list between the lines around it`() {
        // Arrange
        val formattedText = FormattedText(
            text = "x\na\ny",
            ranges = emptyList(),
            paragraphs = listOf(ParagraphRange(2, 3, BlockType.NumberedList)),
        )

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo("""<div dir="auto">x<ol><li>a</li></ol>y</div>""")
    }

    @Test
    fun `should write a list of a deeper level inside the item above it`() {
        // Arrange
        val formattedText = FormattedText(
            text = "a\nb\nc",
            ranges = emptyList(),
            paragraphs = listOf(
                ParagraphRange(0, 1, BlockType.BulletList, 0),
                ParagraphRange(2, 3, BlockType.BulletList, 1),
                ParagraphRange(4, 5, BlockType.BulletList, 0),
            ),
        )

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo(
            """<div dir="auto"><ul><li>a<ul><li>b</li></ul></li><li>c</li></ul></div>""",
        )
    }

    @Test
    fun `should write a quote block`() {
        // Arrange
        val formattedText = FormattedText(
            text = "q1\nq2",
            ranges = emptyList(),
            paragraphs = listOf(ParagraphRange(0, 5, BlockType.Quote)),
        )

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo(
            """<div dir="auto"><blockquote style="margin: 0 0 0 0.8ex; border-left: 2px solid #cccccc; """ +
                """padding-left: 1ex">q1<br>q2</blockquote></div>""",
        )
    }

    @Test
    fun `should write a line placed in the middle`() {
        // Arrange
        val formattedText = FormattedText(
            text = "t",
            ranges = emptyList(),
            paragraphs = listOf(ParagraphRange(0, 1, BlockType.Normal, 0, Alignment.Center)),
        )

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo("""<div dir="auto"><div style="text-align: center">t</div></div>""")
    }

    @Test
    fun `should write an indented line`() {
        // Arrange
        val formattedText = FormattedText(
            text = "t",
            ranges = emptyList(),
            paragraphs = listOf(ParagraphRange(0, 1, BlockType.Normal, 2)),
        )

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo("""<div dir="auto"><div style="margin-left: 80px">t</div></div>""")
    }

    @Test
    fun `should write an empty line of a list as a line of its own`() {
        // Arrange
        val formattedText = FormattedText(
            text = "a\n\nb",
            ranges = emptyList(),
            paragraphs = listOf(ParagraphRange(0, 4, BlockType.BulletList)),
        )

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo(
            """<div dir="auto"><ul><li>a</li><li><br></li><li>b</li></ul></div>""",
        )
    }

    @Test
    fun `should write a level that has no line of its own as an item without a marker`() {
        // Arrange
        val formattedText = FormattedText(
            text = "a\nb",
            ranges = emptyList(),
            paragraphs = listOf(
                ParagraphRange(0, 1, BlockType.BulletList, 0),
                ParagraphRange(2, 3, BlockType.BulletList, 2),
            ),
        )

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo(
            """<div dir="auto"><ul><li>a<ul><li style="list-style-type: none"><ul><li>b</li></ul>""" +
                """</li></ul></li></ul></div>""",
        )
    }

    @Test
    fun `should write a list that begins below the line above it`() {
        // Arrange
        val formattedText = FormattedText(
            text = "b",
            ranges = emptyList(),
            paragraphs = listOf(ParagraphRange(0, 1, BlockType.NumberedList, 2)),
        )

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo(
            """<div dir="auto"><ol><li style="list-style-type: none"><ol><li style="list-style-type: none">""" +
                """<ol><li>b</li></ol></li></ol></li></ol></div>""",
        )
    }

    @Test
    fun `should write the text of a list item placed in the middle`() {
        // Arrange
        val formattedText = FormattedText(
            text = "a\nb",
            ranges = emptyList(),
            paragraphs = listOf(
                ParagraphRange(0, 1, BlockType.BulletList, 0, Alignment.Center),
                ParagraphRange(2, 3, BlockType.BulletList, 0, Alignment.End),
            ),
        )

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo(
            """<div dir="auto"><ul><li style="text-align: center">a</li>""" +
                """<li style="text-align: right">b</li></ul></div>""",
        )
    }

    @Test
    fun `should read a bullet list back`() {
        // Act
        val result = testSubject.fromHtmlFragment("""<div dir="auto"><ul><li>a</li><li>b</li></ul></div>""")

        // Assert
        assertThat(result.text).isEqualTo("a\nb")
        assertThat(result.paragraphs).isEqualTo(listOf(ParagraphRange(0, 3, BlockType.BulletList, 0, Alignment.Start)))
    }

    @Test
    fun `should read a numbered list between the lines around it back`() {
        // Act
        val result = testSubject.fromHtmlFragment("""<div dir="auto">x<ol><li>a</li></ol>y</div>""")

        // Assert
        assertThat(result.text).isEqualTo("x\na\ny")
        assertThat(result.paragraphs).isEqualTo(
            listOf(ParagraphRange(2, 3, BlockType.NumberedList, 0, Alignment.Start)),
        )
    }

    @Test
    fun `should read a list of a deeper level back`() {
        // Act
        val result = testSubject.fromHtmlFragment(
            """<div dir="auto"><ul><li>a<ul><li>b</li></ul></li><li>c</li></ul></div>""",
        )

        // Assert
        assertThat(result.text).isEqualTo("a\nb\nc")
        assertThat(result.paragraphs).isEqualTo(
            listOf(
                ParagraphRange(0, 1, BlockType.BulletList, 0, Alignment.Start),
                ParagraphRange(2, 3, BlockType.BulletList, 1, Alignment.Start),
                ParagraphRange(4, 5, BlockType.BulletList, 0, Alignment.Start),
            ),
        )
    }

    @Test
    fun `should read a quote block back`() {
        // Act
        val result = testSubject.fromHtmlFragment(
            """<div dir="auto"><blockquote style="margin: 0 0 0 0.8ex; border-left: 2px solid #cccccc; """ +
                """padding-left: 1ex">q1<br>q2</blockquote></div>""",
        )

        // Assert
        assertThat(result.text).isEqualTo("q1\nq2")
        assertThat(result.paragraphs).isEqualTo(listOf(ParagraphRange(0, 5, BlockType.Quote, 0, Alignment.Start)))
    }

    @Test
    fun `should read a line placed in the middle back`() {
        // Act
        val result = testSubject.fromHtmlFragment("""<div dir="auto"><div style="text-align: center">t</div></div>""")

        // Assert
        assertThat(result.text).isEqualTo("t")
        assertThat(result.paragraphs).isEqualTo(
            listOf(ParagraphRange(0, 1, BlockType.Normal, 0, Alignment.Center)),
        )
    }

    @Test
    fun `should read an indented line back`() {
        // Act
        val result = testSubject.fromHtmlFragment("""<div dir="auto"><div style="margin-left: 80px">t</div></div>""")

        // Assert
        assertThat(result.text).isEqualTo("t")
        assertThat(result.paragraphs).isEqualTo(listOf(ParagraphRange(0, 1, BlockType.Normal, 2, Alignment.Start)))
    }

    @Test
    fun `should read an empty line of a list back`() {
        // Act
        val result = testSubject.fromHtmlFragment(
            """<div dir="auto"><ul><li>a</li><li><br></li><li>b</li></ul></div>""",
        )

        // Assert
        assertThat(result.text).isEqualTo("a\n\nb")
        assertThat(result.paragraphs).isEqualTo(listOf(ParagraphRange(0, 4, BlockType.BulletList, 0, Alignment.Start)))
    }

    @Test
    fun `should read a level that has no line of its own back`() {
        // Act
        val result = testSubject.fromHtmlFragment(
            """<div dir="auto"><ul><li>a<ul><li style="list-style-type: none"><ul><li>b</li></ul>""" +
                """</li></ul></li></ul></div>""",
        )

        // Assert
        assertThat(result.text).isEqualTo("a\nb")
        assertThat(result.paragraphs).isEqualTo(
            listOf(
                ParagraphRange(0, 1, BlockType.BulletList, 0, Alignment.Start),
                ParagraphRange(2, 3, BlockType.BulletList, 2, Alignment.Start),
            ),
        )
    }

    @Test
    fun `should read a list that begins below the line above it back`() {
        // Act
        val result = testSubject.fromHtmlFragment(
            """<div dir="auto"><ol><li style="list-style-type: none"><ol><li style="list-style-type: none">""" +
                """<ol><li>b</li></ol></li></ol></li></ol></div>""",
        )

        // Assert
        assertThat(result.text).isEqualTo("b")
        assertThat(result.paragraphs).isEqualTo(
            listOf(ParagraphRange(0, 1, BlockType.NumberedList, 2, Alignment.Start)),
        )
    }

    @Test
    fun `should read every paragraph back`() {
        assertRoundTrip(*PARAGRAPH_ROUND_TRIP_CASES.toTypedArray())
    }

    @Test
    fun `should move a paragraph that begins inside a line to the borders of its lines`() {
        // Act
        val result = normalizeParagraphs("abc\ndef", listOf(ParagraphRange(1, 3, BlockType.BulletList, 0)))

        // Assert
        assertThat(result).isEqualTo(listOf(ParagraphRange(0, 3, BlockType.BulletList, 0, Alignment.Start)))
    }

    @Test
    fun `should merge paragraphs of the same formatting that follow each other`() {
        // Act
        val result = normalizeParagraphs(
            "a\nb\nc",
            listOf(
                ParagraphRange(0, 1, BlockType.BulletList, 0),
                ParagraphRange(2, 3, BlockType.BulletList, 0),
            ),
        )

        // Assert
        assertThat(result).isEqualTo(listOf(ParagraphRange(0, 3, BlockType.BulletList, 0, Alignment.Start)))
    }

    @Test
    fun `should drop a paragraph that formats its lines the way an unformatted line is formatted`() {
        // Act
        val result = normalizeParagraphs("a\nb", listOf(ParagraphRange(0, 3, BlockType.Normal, 0, Alignment.Start)))

        // Assert
        assertThat(result).isEqualTo(emptyList())
    }

    @Test
    fun `should keep the level a line stands at, however deep the line above it is`() {
        // Act
        val result = normalizeParagraphs(
            "a\nb",
            listOf(
                ParagraphRange(0, 1, BlockType.BulletList, 0),
                ParagraphRange(2, 3, BlockType.BulletList, 3),
            ),
        )

        // Assert
        assertThat(result).isEqualTo(
            listOf(
                ParagraphRange(0, 1, BlockType.BulletList, 0, Alignment.Start),
                ParagraphRange(2, 3, BlockType.BulletList, 3, Alignment.Start),
            ),
        )
    }

    @Test
    fun `canRepresent should accept what the writer writes`() {
        // Arrange
        val htmlsToRepresent = listOf(
            """<div dir="auto"><ul><li>a</li><li>b</li></ul></div>""",
            """<div dir="auto">x<ol><li>a</li></ol>y</div>""",
            """<div dir="auto"><ul><li>a<ul><li>b</li></ul></li><li>c</li></ul></div>""",
            """<div dir="auto"><blockquote style="margin: 0 0 0 0.8ex; border-left: 2px solid #cccccc; """ +
                """padding-left: 1ex">q1<br>q2</blockquote></div>""",
            """<div dir="auto"><div style="text-align: center">t</div></div>""",
            """<div dir="auto"><div style="margin-left: 80px">t</div></div>""",
            """<div dir="auto"><ul><li>a</li><li><br></li><li>b</li></ul></div>""",
            """<div dir="auto"><ul><li style="text-align: center">a</li></ul></div>""",
            """<div dir="auto"><ul><li style="list-style-type: none"><ul><li>a</li></ul></li></ul></div>""",
            """<div dir="auto"><ul><li style="text-align: center">a</li>""" +
                """<li style="text-align: right">b</li></ul></div>""",
            """<div dir="auto"><ul><li>a<ul><li style="list-style-type: none"><ul><li>b</li></ul>""" +
                """</li></ul></li></ul></div>""",
        )

        // Act
        val results = htmlsToRepresent.map { testSubject.canRepresent(it) }

        // Assert
        assertThat(results).isEqualTo(htmlsToRepresent.map { true })
    }

    @Test
    fun `canRepresent should reject paragraphs it would have to change`() {
        // Arrange
        val htmlThatWouldLoseSomething = listOf(
            """<ul><li style="color: red">x</li></ul>""",
            """<ol><li style="list-style-type: disc">x</li></ol>""",
            """<blockquote style="margin: 0">x</blockquote>""",
            """<div style="margin-left: 30px">x</div>""",
            """<div style="float: left">x</div>""",
            """<ul class="x"><li>x</li></ul>""",
            """<ul type="a"><li>x</li></ul>""",
            """<blockquote>x</blockquote>""",
        )

        // Act
        val results = htmlThatWouldLoseSomething.map { testSubject.canRepresent(it) }

        // Assert
        assertThat(results).isEqualTo(htmlThatWouldLoseSomething.map { false })
    }

    @Test
    fun `should read a quote of a message with a quote in it`() {
        // Act
        val result = testSubject.fromHtmlFragmentLenient(
            """<div class="gmail_quote"><blockquote><blockquote>deep</blockquote></blockquote></div>""",
        )

        // Assert
        assertThat(result.formattedText.text).isEqualTo("deep")
        assertThat(result.formattedText.paragraphs).isEqualTo(
            listOf(ParagraphRange(0, 4, BlockType.Quote, 1, Alignment.Start)),
        )
        assertThat(result.lost).isEqualTo(emptySet())
    }

    @Test
    fun `should read a heading as bold text`() {
        // Act
        val result = testSubject.fromHtmlFragmentLenient("<h1>Title</h1>")

        // Assert
        assertThat(result.formattedText.text).isEqualTo("Title")
        assertThat(result.formattedText.ranges).isEqualTo(
            listOf(
                FormattedRange(0, 5, TextFormat.Bold),
                FormattedRange(0, 5, TextFormat.SizeLarge),
            ),
        )
    }

    @Test
    fun `should read a paragraph placed in the middle`() {
        // Act
        val result = testSubject.fromHtmlFragmentLenient("""<p style="text-align: center">c</p>""")

        // Assert
        assertThat(result.formattedText.paragraphs).isEqualTo(
            listOf(ParagraphRange(0, 1, BlockType.Normal, 0, Alignment.Center)),
        )
        assertThat(result.lost).isEqualTo(setOf(LostContent.Styles))
    }

    @Test
    fun `should read a list that is deeper than the model knows`() {
        // Act
        val result = testSubject.fromHtmlFragmentLenient(
            """<ul><li><ul><li><ul><li><ul><li><ul><li><ul><li>x</li></ul></li></ul></li></ul></li></ul></li></ul>""",
        )

        // Assert
        assertThat(result.formattedText.text).isEqualTo("x")
        assertThat(result.formattedText.paragraphs).isEqualTo(
            listOf(ParagraphRange(0, 1, BlockType.BulletList, ParagraphRange.MAX_LEVEL, Alignment.Start)),
        )
    }

    /**
     * Writes every [FormattedText] as HTML and reads it back, which has to give what was written.
     */
    private fun assertRoundTrip(vararg formattedTexts: FormattedText) {
        // Act
        val results = formattedTexts.map {
            val normalized = it.copy(paragraphs = normalizeParagraphs(it.text, it.paragraphs))
            val html = testSubject.toHtmlFragment(normalized)
            testSubject.canRepresent(html) to testSubject.fromHtmlFragment(html)
        }

        // Assert
        assertThat(results).isEqualTo(
            formattedTexts.map {
                true to it.copy(paragraphs = normalizeParagraphs(it.text, it.paragraphs))
            },
        )
    }
}

// Lists, quotes, alignment and indentation, alone and mixed with the formatting of the characters inside them.
private val PARAGRAPH_ROUND_TRIP_CASES = listOf(
    FormattedText(text = "Hello", ranges = emptyList()),
    FormattedText(
        text = "a\nb\nc",
        ranges = emptyList(),
        paragraphs = listOf(ParagraphRange(0, 5, BlockType.BulletList, 0)),
    ),
    FormattedText(
        text = "a\nb\nc",
        ranges = emptyList(),
        paragraphs = listOf(
            ParagraphRange(0, 1, BlockType.BulletList, 0),
            ParagraphRange(2, 3, BlockType.BulletList, 1),
            ParagraphRange(4, 5, BlockType.BulletList, 0),
        ),
    ),
    FormattedText(
        text = "a\nb",
        ranges = emptyList(),
        paragraphs = listOf(ParagraphRange(0, 3, BlockType.NumberedList, 0)),
    ),
    FormattedText(
        text = "a\nb",
        ranges = emptyList(),
        paragraphs = listOf(
            ParagraphRange(0, 1, BlockType.BulletList, 0),
            ParagraphRange(2, 3, BlockType.NumberedList, 0),
        ),
    ),
    FormattedText(
        text = "q1\nq2",
        ranges = emptyList(),
        paragraphs = listOf(ParagraphRange(0, 5, BlockType.Quote, 0)),
    ),
    FormattedText(
        text = "q1\nq2",
        ranges = emptyList(),
        paragraphs = listOf(ParagraphRange(0, 5, BlockType.Quote, 1)),
    ),
    FormattedText(
        text = "t\nu",
        ranges = emptyList(),
        paragraphs = listOf(
            ParagraphRange(0, 1, BlockType.Normal, 0, Alignment.Center),
            ParagraphRange(2, 3, BlockType.Normal, 0, Alignment.End),
        ),
    ),
    FormattedText(
        text = "t",
        ranges = emptyList(),
        paragraphs = listOf(ParagraphRange(0, 1, BlockType.Normal, 2)),
    ),
    FormattedText(
        text = "go now",
        ranges = listOf(FormattedRange(0, 2, TextFormat.Bold)),
        paragraphs = listOf(ParagraphRange(0, 6, BlockType.BulletList, 0)),
        links = listOf(LinkRange(0, 6, "https://example.com")),
    ),
    FormattedText(
        text = "a\n\nb",
        ranges = emptyList(),
        paragraphs = listOf(ParagraphRange(0, 4, BlockType.BulletList, 0)),
    ),
    FormattedText(
        text = "a\nb",
        ranges = emptyList(),
        paragraphs = listOf(ParagraphRange(0, 1, BlockType.BulletList, 0)),
    ),
    FormattedText(
        text = "q\nb",
        ranges = emptyList(),
        paragraphs = listOf(
            ParagraphRange(0, 1, BlockType.Quote, 0),
            ParagraphRange(2, 3, BlockType.BulletList, 0),
        ),
    ),
    FormattedText(
        text = "a\nb",
        ranges = emptyList(),
        paragraphs = listOf(
            ParagraphRange(0, 1, BlockType.BulletList, 0),
            ParagraphRange(2, 3, BlockType.BulletList, 2),
        ),
    ),
    FormattedText(
        text = "a",
        ranges = emptyList(),
        paragraphs = listOf(ParagraphRange(0, 1, BlockType.BulletList, 1)),
    ),
    FormattedText(
        text = "a\nb",
        ranges = emptyList(),
        paragraphs = listOf(
            ParagraphRange(0, 1, BlockType.BulletList, 0, Alignment.Center),
            ParagraphRange(2, 3, BlockType.BulletList, 0, Alignment.End),
        ),
    ),
    FormattedText(
        text = "a\n\nb",
        ranges = listOf(FormattedRange(0, 1, TextFormat.Bold)),
        paragraphs = listOf(ParagraphRange(0, 4, BlockType.NumberedList, 0)),
        colors = listOf(ColorRange(3, 4, ColorKind.Highlight, 0xfde293)),
    ),
    FormattedText(
        text = "q1\nq2",
        ranges = emptyList(),
        paragraphs = listOf(ParagraphRange(0, 5, BlockType.Quote, 0, Alignment.Center)),
    ),
    FormattedText(
        text = "a\nb",
        ranges = listOf(FormattedRange(0, 3, TextFormat.Italic)),
        paragraphs = emptyList(),
    ),
    FormattedText(
        text = "a\nb",
        ranges = emptyList(),
        paragraphs = emptyList(),
        links = listOf(LinkRange(0, 3, "https://example.com")),
    ),
)
