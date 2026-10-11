package net.thunderbird.feature.mail.message.composer.internal.richtext

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlin.test.Test
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

class DefaultRichTextHtmlConverterTest {
    private val testSubject = DefaultRichTextHtmlConverter()

    @Test
    fun `should wrap a formatted range in its tag`() {
        // Arrange
        val ranges = listOf(FormattedRange(start = 6, end = 10, format = TextFormat.Bold))

        // Act
        val result = testSubject.toHtmlFragment("Hello Jane, hi", ranges)

        // Assert
        assertThat(result).isEqualTo("""<div dir="auto">Hello <b>Jane</b>, hi</div>""")
    }

    @Test
    fun `should use one tag per format`() {
        // Arrange
        val ranges = listOf(
            FormattedRange(start = 0, end = 1, format = TextFormat.Bold),
            FormattedRange(start = 2, end = 3, format = TextFormat.Italic),
            FormattedRange(start = 4, end = 5, format = TextFormat.Underline),
        )

        // Act
        val result = testSubject.toHtmlFragment("a b c", ranges)

        // Assert
        assertThat(result).isEqualTo("""<div dir="auto"><b>a</b> <i>b</i> <u>c</u></div>""")
    }

    @Test
    fun `should keep tags well nested when ranges overlap`() {
        // Arrange
        val ranges = listOf(
            FormattedRange(start = 0, end = 4, format = TextFormat.Bold),
            FormattedRange(start = 2, end = 6, format = TextFormat.Italic),
        )

        // Act
        val result = testSubject.toHtmlFragment("abcdef", ranges)

        // Assert
        assertThat(result).isEqualTo("""<div dir="auto"><b>ab</b><b><i>cd</i></b><i>ef</i></div>""")
    }

    @Test
    fun `should encode characters with a meaning in HTML`() {
        // Arrange
        val ranges = listOf(FormattedRange(start = 0, end = 3, format = TextFormat.Bold))

        // Act
        val result = testSubject.toHtmlFragment("""a<b & "c" > d""", ranges)

        // Assert
        assertThat(result).isEqualTo("""<div dir="auto"><b>a&lt;b</b> &amp; &quot;c&quot; &gt; d</div>""")
    }

    @Test
    fun `should turn line breaks into br tags and drop carriage returns`() {
        // Arrange
        val ranges = listOf(FormattedRange(start = 0, end = 3, format = TextFormat.Bold))

        // Act
        val result = testSubject.toHtmlFragment("one\r\ntwo\n\nthree", ranges)

        // Assert
        assertThat(result).isEqualTo("""<div dir="auto"><b>one</b><br>two<br><br>three</div>""")
    }

    @Test
    fun `should keep spaces HTML would collapse`() {
        // Act
        val result = testSubject.toHtmlFragment(" a  b\n c", ranges = emptyList())

        // Assert
        assertThat(result).isEqualTo("""<div dir="auto">&nbsp;a &nbsp;b<br>&nbsp;c</div>""")
    }

    @Test
    fun `should ignore empty ranges and clip ranges that reach past the text`() {
        // Arrange
        val ranges = listOf(
            FormattedRange(start = 1, end = 1, format = TextFormat.Italic),
            FormattedRange(start = 2, end = 99, format = TextFormat.Bold),
            FormattedRange(start = 50, end = 60, format = TextFormat.Underline),
        )

        // Act
        val result = testSubject.toHtmlFragment("abcd", ranges)

        // Assert
        assertThat(result).isEqualTo("""<div dir="auto">ab<b>cd</b></div>""")
    }

    @Test
    fun `fromHtmlFragment should read back what toHtmlFragment wrote`() {
        // Arrange
        val text = " Hello Jane,\n\nthis is <important> & \"urgent\".  Bye"
        val ranges = listOf(
            FormattedRange(start = 7, end = 11, format = TextFormat.Bold),
            FormattedRange(start = 14, end = 30, format = TextFormat.Italic),
            FormattedRange(start = 22, end = 33, format = TextFormat.Underline),
        )
        val html = testSubject.toHtmlFragment(text, ranges)

        // Act
        val result = testSubject.fromHtmlFragment(html)

        // Assert
        assertThat(result.text).isEqualTo(text)
        assertThat(testSubject.toHtmlFragment(result.text, result.ranges)).isEqualTo(html)
    }

    @Test
    fun `fromHtmlFragment should report formatted ranges by their position in the text`() {
        // Act
        val result = testSubject.fromHtmlFragment("""<div dir="auto">Hello <b>Jane</b>,<br><i><u>hi</u></i></div>""")

        // Assert
        assertThat(result).isEqualTo(
            FormattedText(
                text = "Hello Jane,\nhi",
                ranges = listOf(
                    FormattedRange(start = 6, end = 10, format = TextFormat.Bold),
                    FormattedRange(start = 12, end = 14, format = TextFormat.Italic),
                    FormattedRange(start = 12, end = 14, format = TextFormat.Underline),
                ),
            ),
        )
    }

    @Test
    fun `fromHtmlFragment should read text without formatting`() {
        // Act
        val result = testSubject.fromHtmlFragment("""<div dir="auto">one<br>two</div>""")

        // Assert
        assertThat(result).isEqualTo(FormattedText(text = "one\ntwo", ranges = emptyList()))
    }

    @Test
    fun `fromHtmlFragment should start every block on a new line`() {
        // Act
        val result = testSubject.fromHtmlFragment("<div>one</div><div><strong>two</strong></div><p><em>three</em></p>")

        // Assert
        assertThat(result.text).isEqualTo("one\ntwo\nthree")
        assertThat(result.ranges).isEqualTo(
            listOf(
                FormattedRange(start = 4, end = 7, format = TextFormat.Bold),
                FormattedRange(start = 8, end = 13, format = TextFormat.Italic),
            ),
        )
    }

    @Test
    fun `canRepresent should accept what toHtmlFragment writes`() {
        // Arrange
        val ranges = listOf(
            FormattedRange(start = 0, end = 5, format = TextFormat.Bold),
            FormattedRange(start = 3, end = 9, format = TextFormat.Italic),
            FormattedRange(start = 6, end = 12, format = TextFormat.Underline),
        )
        val html = testSubject.toHtmlFragment("Hello Jane,\n <hi> & bye", ranges)

        // Act
        val result = testSubject.canRepresent(html)

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `canRepresent should accept hand written HTML that only uses supported formatting`() {
        // Act
        val result = testSubject.canRepresent("<B>Max</B><br><I>On the road</I><p><strong>x</strong> <em>y</em></p>")

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `canRepresent should accept plain text`() {
        // Act
        val result = testSubject.canRepresent("Just text")

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `canRepresent should reject HTML with content the composer can not show`() {
        // Arrange
        val htmlThatWouldLoseSomething = listOf(
            """<a href="https://example.com">link</a>""",
            """<img src="https://example.com/logo.png">""",
            "<table><tr><td>cell</td></tr></table>",
            """<b style="color: red">red</b>""",
            """<div class="signature">text</div>""",
            "<ul><li>item</li></ul>",
            "<span>text</span>",
        )

        // Act
        val results = htmlThatWouldLoseSomething.map { testSubject.canRepresent(it) }

        // Assert
        assertThat(results).isEqualTo(htmlThatWouldLoseSomething.map { false })
    }

    @Test
    fun `should write sizes and fonts as styles`() {
        // Arrange
        val ranges = listOf(
            FormattedRange(start = 0, end = 1, format = TextFormat.SizeSmall),
            FormattedRange(start = 2, end = 3, format = TextFormat.SizeLarge),
            FormattedRange(start = 4, end = 5, format = TextFormat.SizeHuge),
            FormattedRange(start = 6, end = 7, format = TextFormat.FontSerif),
            FormattedRange(start = 8, end = 9, format = TextFormat.FontMonospace),
        )

        // Act
        val result = testSubject.toHtmlFragment("a b c d e", ranges)

        // Assert
        assertThat(result).isEqualTo(
            """<div dir="auto"><span style="font-size: small">a</span> """ +
                """<span style="font-size: large">b</span> """ +
                """<span style="font-size: x-large">c</span> """ +
                """<span style="font-family: serif">d</span> """ +
                """<span style="font-family: monospace">e</span></div>""",
        )
    }

    @Test
    fun `should read back sizes and fonts combined with other formatting`() {
        // Arrange
        val text = "Big bold serif and small"
        val ranges = listOf(
            FormattedRange(start = 0, end = 14, format = TextFormat.SizeHuge),
            FormattedRange(start = 4, end = 14, format = TextFormat.Bold),
            FormattedRange(start = 9, end = 14, format = TextFormat.FontSerif),
            FormattedRange(start = 19, end = 24, format = TextFormat.SizeSmall),
        )
        val html = testSubject.toHtmlFragment(text, ranges)

        // Act
        val result = testSubject.fromHtmlFragment(html)

        // Assert
        assertThat(testSubject.canRepresent(html)).isTrue()
        assertThat(result.text).isEqualTo(text)
        assertThat(testSubject.toHtmlFragment(result.text, result.ranges)).isEqualTo(html)
    }

    @Test
    fun `canRepresent should accept a known style however it is spaced`() {
        // Act
        val result = testSubject.canRepresent("""<span style="FONT-SIZE:large;">big</span>""")

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `canRepresent should reject spans with other styles or attributes`() {
        // Arrange
        val htmlThatWouldLoseSomething = listOf(
            """<span style="color: red">red</span>""",
            """<span style="font-size: 30px">big</span>""",
            """<span style="font-size: large; color: red">big red</span>""",
            """<span style="font-size: large" class="x">big</span>""",
        )

        // Act
        val results = htmlThatWouldLoseSomething.map { testSubject.canRepresent(it) }

        // Assert
        assertThat(results).isEqualTo(htmlThatWouldLoseSomething.map { false })
    }
}
