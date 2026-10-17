package net.thunderbird.feature.mail.message.composer.internal.richtext

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlin.test.Test
import net.thunderbird.feature.mail.message.composer.richtext.ColorKind
import net.thunderbird.feature.mail.message.composer.richtext.ColorRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.InlineImage
import net.thunderbird.feature.mail.message.composer.richtext.LinkRange
import net.thunderbird.feature.mail.message.composer.richtext.RichTextHtmlConverter.LostContent
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
    fun `fromHtmlFragment should read a format that stands between two runs of itself as one range`() {
        // Arrange
        val formattedText = FormattedText(
            text = "abcdef",
            ranges = listOf(
                FormattedRange(0, 3, TextFormat.Bold),
                FormattedRange(1, 4, TextFormat.Italic),
                FormattedRange(3, 6, TextFormat.Bold),
            ),
        )

        // Act
        val html = testSubject.toHtmlFragment(formattedText)
        val result = testSubject.fromHtmlFragment(html)

        // Assert
        assertThat(result.ranges).containsExactly(
            FormattedRange(0, 6, TextFormat.Bold),
            FormattedRange(1, 4, TextFormat.Italic),
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
            """<img src="https://example.com/logo.png">""",
            "<table><tr><td>cell</td></tr></table>",
            """<b style="color: red">red</b>""",
            """<div class="signature">text</div>""",
            "<ul><li>item</li></ul>",
            "<span>text</span>",
            """<a href="javascript:x" target="_blank">link</a>""",
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

    @Test
    fun `should write a strikethrough range as an s tag`() {
        // Act
        val result = testSubject.toHtmlFragment(
            FormattedText(text = "gone", ranges = listOf(FormattedRange(0, 4, TextFormat.Strikethrough))),
        )

        // Assert
        assertThat(result).isEqualTo("""<div dir="auto"><s>gone</s></div>""")
    }

    @Test
    fun `should write a text colour and a highlight colour as styles`() {
        // Arrange
        val formattedText = FormattedText(
            text = "ab",
            ranges = emptyList(),
            colors = listOf(
                ColorRange(start = 0, end = 1, kind = ColorKind.Text, color = 0xd93025),
                ColorRange(start = 1, end = 2, kind = ColorKind.Highlight, color = 0xf9ab00),
            ),
        )

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo(
            """<div dir="auto"><span style="color: #d93025">a</span>""" +
                """<span style="background-color: #f9ab00">b</span></div>""",
        )
    }

    @Test
    fun `should write a colour that has alpha bits as its rgb value`() {
        // Arrange
        val formattedText = FormattedText(
            text = "red",
            ranges = emptyList(),
            colors = listOf(ColorRange(0, 3, ColorKind.Text, 0xffd93025.toInt())),
        )

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo("""<div dir="auto"><span style="color: #d93025">red</span></div>""")
    }

    @Test
    fun `should wrap a colour inside the tags of the formats`() {
        // Arrange
        val formattedText = FormattedText(
            text = "gone",
            ranges = listOf(FormattedRange(0, 4, TextFormat.Bold)),
            colors = listOf(ColorRange(0, 4, ColorKind.Text, 0xd93025)),
        )

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo("""<div dir="auto"><b><span style="color: #d93025">gone</span></b></div>""")
    }

    @Test
    fun `should wrap the text of a link in an a tag`() {
        // Arrange
        val formattedText = FormattedText(
            text = "here",
            ranges = emptyList(),
            links = listOf(LinkRange(start = 0, end = 4, url = "https://example.com")),
        )

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo("""<div dir="auto"><a href="https://example.com">here</a></div>""")
    }

    @Test
    fun `should wrap the formats inside the tag of the link they belong to`() {
        // Arrange
        val formattedText = FormattedText(
            text = "go now",
            ranges = listOf(FormattedRange(start = 0, end = 2, format = TextFormat.Bold)),
            links = listOf(LinkRange(start = 0, end = 6, url = "https://example.com")),
        )

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo(
            """<div dir="auto"><a href="https://example.com"><b>go</b> now</a></div>""",
        )
    }

    @Test
    fun `should keep the text of a link the composer refuses`() {
        // Arrange
        val formattedText = FormattedText(
            text = "gone",
            ranges = emptyList(),
            links = listOf(LinkRange(start = 0, end = 4, url = "javascript:alert(1)")),
        )

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo("""<div dir="auto">gone</div>""")
    }

    @Test
    fun `should encode the characters of a url that have a meaning in an attribute`() {
        // Arrange
        val formattedText = FormattedText(
            text = "here",
            ranges = emptyList(),
            links = listOf(LinkRange(start = 0, end = 4, url = "https://example.com/?a=1&b=\"x\"")),
        )

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo(
            """<div dir="auto"><a href="https://example.com/?a=1&amp;b=&quot;x&quot;">here</a></div>""",
        )
    }

    @Test
    fun `should write an inline image where its character stands`() {
        // Arrange
        val formattedText = FormattedText(
            text = "a\uFFFCb",
            ranges = emptyList(),
            images = listOf(InlineImage(position = 1, contentId = "image1")),
        )

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo(
            """<div dir="auto">a<img src="cid:image1" alt="" style="max-width: 100%">b</div>""",
        )
    }

    @Test
    fun `should drop a character that stands for an image that is not attached`() {
        // Act
        val result = testSubject.toHtmlFragment(FormattedText(text = "a\uFFFCb", ranges = emptyList()))

        // Assert
        assertThat(result).isEqualTo("""<div dir="auto">ab</div>""")
    }

    @Test
    fun `should turn plain text that is a web address into a link`() {
        // Act
        val text = "see https://example.com now"

        // Act
        val result = testSubject.toHtmlFragment(FormattedText(text = text, ranges = emptyList()))

        // Assert
        assertThat(result).isEqualTo(
            """<div dir="auto">see <a href="https://example.com">https://example.com</a> now</div>""",
        )
    }

    @Test
    fun `should not turn plain text inside a link into a second link`() {
        // Arrange
        val formattedText = FormattedText(
            text = "https://example.com",
            ranges = emptyList(),
            links = listOf(LinkRange(start = 0, end = 19, url = "https://example.com")),
        )

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo(
            """<div dir="auto"><a href="https://example.com">https://example.com</a></div>""",
        )
    }

    @Test
    fun `should not turn plain text into a link that reaches into a link of the text`() {
        // Arrange
        val formattedText = FormattedText(
            text = "https://example.com/a",
            ranges = emptyList(),
            links = listOf(LinkRange(start = 0, end = 11, url = "https://example.com/b")),
        )

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo(
            """<div dir="auto"><a href="https://example.com/b">https://exa</a>mple.com/a</div>""",
        )
    }

    @Test
    fun `fromHtmlFragment should read strikethrough from s, strike and del`() {
        // Act
        val result = testSubject.fromHtmlFragment("""<div dir="auto"><s>a</s> <strike>b</strike> <del>c</del></div>""")

        // Assert
        assertThat(result.ranges).isEqualTo(
            listOf(0, 2, 4).map {
                FormattedRange(start = it, end = it + 1, format = TextFormat.Strikethrough)
            },
        )
    }

    @Test
    fun `fromHtmlFragment should read the colours of a span`() {
        // Act
        val result = testSubject.fromHtmlFragment(
            """<div dir="auto"><span style="color: rgb(217, 48, 37)">a</span>""" +
                """<span style="background-color: #ff0">b</span></div>""",
        )

        // Assert
        assertThat(result.colors).isEqualTo(
            listOf(
                ColorRange(start = 0, end = 1, kind = ColorKind.Text, color = 0xd93025),
                ColorRange(start = 1, end = 2, kind = ColorKind.Highlight, color = 0xffff00),
            ),
        )
    }

    @Test
    fun `fromHtmlFragment should read every style a span sets`() {
        // Act
        val result = testSubject.fromHtmlFragment(
            """<div dir="auto"><span style="font-size: large; color: #112233">a</span></div>""",
        )

        // Assert
        assertThat(result.ranges).isEqualTo(listOf(FormattedRange(0, 1, TextFormat.SizeLarge)))
        assertThat(result.colors).isEqualTo(listOf(ColorRange(0, 1, ColorKind.Text, 0x112233)))
    }

    @Test
    fun `fromHtmlFragment should keep the text of a link it refuses`() {
        // Act
        val result = testSubject.fromHtmlFragment("""<div dir="auto"><a href="javascript:alert(1)">gone</a></div>""")

        // Assert
        assertThat(result).isEqualTo(FormattedText(text = "gone", ranges = emptyList()))
    }

    @Test
    fun `canRepresent should accept what every feature of its own writes`() {
        // Arrange
        val htmlsToRepresent = listOf(
            """<div dir="auto"><s>a</s></div>""",
            """<div dir="auto"><span style="color: #d93025">a</span></div>""",
            """<div dir="auto"><span style="background-color: #f9ab00">a</span></div>""",
            """<div dir="auto"><a href="mailto:jane@example.com">a</a></div>""",
            """<div dir="auto"><img src="cid:image1" alt="" style="max-width: 100%"></div>""",
        )

        // Act
        val results = htmlsToRepresent.map { testSubject.canRepresent(it) }

        // Assert
        assertThat(results).isEqualTo(htmlsToRepresent.map { true })
    }

    @Test
    fun `canRepresent should reject content the composer would have to drop`() {
        // Arrange
        val htmlThatWouldLoseSomething = listOf(
            """<a href="javascript:x">link</a>""",
            """<img src="https://example.com/logo.png">""",
            """<span style="color: red; float: left">red</span>""",
            "<table><tr><td>cell</td></tr></table>",
        )

        // Act
        val results = htmlThatWouldLoseSomething.map { testSubject.canRepresent(it) }

        // Assert
        assertThat(results).isEqualTo(htmlThatWouldLoseSomething.map { false })
    }

    @Test
    fun `should read back every combination of characters and formats`() {
        assertReadBack(
            FormattedText(text = "Hello", ranges = emptyList()),
            FormattedText(
                text = "abcdef",
                ranges = listOf(
                    FormattedRange(0, 3, TextFormat.Bold),
                    FormattedRange(2, 5, TextFormat.Italic),
                ),
            ),
            FormattedText(
                text = "abcdef",
                ranges = listOf(
                    FormattedRange(0, 2, TextFormat.Underline),
                    FormattedRange(2, 6, TextFormat.Strikethrough),
                ),
            ),
            FormattedText(
                text = "abcdef",
                ranges = listOf(
                    FormattedRange(0, 3, TextFormat.SizeLarge),
                    FormattedRange(2, 6, TextFormat.FontSerif),
                ),
                colors = listOf(ColorRange(0, 2, ColorKind.Text, 0x9334e6)),
            ),
        )
    }

    @Test
    fun `should read back every combination of colours`() {
        assertReadBack(
            FormattedText(
                text = "abcdef",
                ranges = emptyList(),
                colors = listOf(ColorRange(1, 4, ColorKind.Text, 0xd93025)),
            ),
            FormattedText(
                text = "abcdef",
                ranges = emptyList(),
                colors = listOf(ColorRange(0, 6, ColorKind.Highlight, 0xfde293)),
            ),
            FormattedText(
                text = "abcdef",
                ranges = emptyList(),
                colors = listOf(
                    ColorRange(0, 3, ColorKind.Text, 0x1a73e8),
                    ColorRange(3, 6, ColorKind.Highlight, 0xfde293),
                ),
            ),
        )
    }

    @Test
    fun `should read back every combination of links and images`() {
        assertReadBack(
            FormattedText(
                text = "abcdef",
                ranges = emptyList(),
                links = listOf(LinkRange(0, 6, "https://example.com")),
            ),
            FormattedText(
                text = "abcdef",
                ranges = listOf(FormattedRange(1, 3, TextFormat.Bold)),
                links = listOf(LinkRange(0, 6, "https://example.com")),
            ),
            FormattedText(
                text = "a\uFFFCb",
                ranges = emptyList(),
                images = listOf(InlineImage(position = 1, contentId = "image1")),
            ),
            FormattedText(
                text = "go\uFFFCnow",
                ranges = listOf(FormattedRange(0, 6, TextFormat.Bold)),
                links = listOf(LinkRange(0, 6, "https://example.com")),
                images = listOf(InlineImage(position = 2, contentId = "image2")),
            ),
            FormattedText(
                text = "one\ntwo\uFFFCthree",
                ranges = listOf(
                    FormattedRange(0, 3, TextFormat.Bold),
                    FormattedRange(4, 7, TextFormat.Italic),
                ),
                colors = listOf(ColorRange(4, 7, ColorKind.Highlight, 0xe6f4ea)),
                links = listOf(LinkRange(4, 7, "https://example.com/a")),
                images = listOf(InlineImage(position = 7, contentId = "image3")),
            ),
        )
    }

    @Test
    fun `fromHtmlFragmentLenient should keep a quoted message with its formatting`() {
        // Arrange
        val html = """
            <div dir="auto">Hi <b>Jane</b>, see <i>this</i> and <a href="https://example.com">the site</a>.
            <div class="gmail_quote"><blockquote type="cite">On Monday, <b>Max</b> wrote:
            <br>hello</blockquote></div></div>
        """.trimIndent()

        // Act
        val result = testSubject.fromHtmlFragmentLenient(html)

        // Assert
        val text = result.formattedText.text
        val jane = text.indexOf("Jane")
        val thisWord = text.indexOf("this")
        val theSite = text.indexOf("the site")
        val max = text.indexOf("Max")
        assertThat(text).isEqualTo("Hi Jane, see this and the site.\nOn Monday, Max wrote:\nhello")
        assertThat(result.formattedText.ranges).isEqualTo(
            listOf(
                FormattedRange(jane, jane + 4, TextFormat.Bold),
                FormattedRange(thisWord, thisWord + 4, TextFormat.Italic),
                FormattedRange(max, max + 3, TextFormat.Bold),
            ),
        )
        assertThat(result.formattedText.links).isEqualTo(
            listOf(LinkRange(theSite, theSite + 8, "https://example.com")),
        )
        assertThat(result.lost).isEqualTo(setOf(LostContent.Styles))
    }

    @Test
    fun `fromHtmlFragmentLenient should put the cells of a table on lines of their own`() {
        // Act
        val result = testSubject.fromHtmlFragmentLenient("<table><tr><td>one</td><td>two</td></tr></table>")

        // Assert
        assertThat(result.formattedText.text).isEqualTo("one\ntwo")
        assertThat(result.lost).isEqualTo(setOf(LostContent.Tables))
    }

    @Test
    fun `fromHtmlFragmentLenient should not read what a script or a style file says`() {
        // Act
        val result = testSubject.fromHtmlFragmentLenient(
            """<div dir="auto">text<script>alert('hi')</script><style>p { color: red }</style>more</div>""",
        )

        // Assert
        assertThat(result.formattedText.text).isEqualTo("textmore")
        assertThat(result.lost).isEqualTo(emptySet())
    }

    @Test
    fun `fromHtmlFragmentLenient should report a style it does not model and keep the rest of it`() {
        // Act
        val result = testSubject.fromHtmlFragmentLenient(
            """<div dir="auto"><span style="float: left">a</span>""" +
                """<span style="color: #ff0000; margin: 0">b</span></div>""",
        )

        // Assert
        assertThat(result.formattedText.text).isEqualTo("ab")
        assertThat(result.formattedText.colors).isEqualTo(listOf(ColorRange(1, 2, ColorKind.Text, 0xff0000)))
        assertThat(result.lost).isEqualTo(setOf(LostContent.Styles))
    }

    @Test
    fun `fromHtmlFragmentLenient should report an image that is not an inline image of the message`() {
        // Act
        val result = testSubject.fromHtmlFragmentLenient(
            """<div dir="auto">a<img src="https://example.com/logo.png">b<img src="cid:image1"></div>""",
        )

        // Assert
        assertThat(result.formattedText.text).isEqualTo("ab\uFFFC")
        assertThat(result.formattedText.images).isEqualTo(listOf(InlineImage(position = 2, contentId = "image1")))
        assertThat(result.lost).isEqualTo(setOf(LostContent.Images))
    }

    @Test
    fun `fromHtmlFragmentLenient should unwrap a font that says nothing`() {
        // Act
        val result = testSubject.fromHtmlFragmentLenient("""<div dir="auto"><font>plain</font></div>""")

        // Assert
        assertThat(result.formattedText.text).isEqualTo("plain")
        assertThat(result.formattedText.colors).isEqualTo(emptyList())
        assertThat(result.lost).isEqualTo(emptySet())
    }

    @Test
    fun `fromHtmlFragmentLenient should read the colour of a font and report its other attributes`() {
        // Act
        val result = testSubject.fromHtmlFragmentLenient(
            """<div dir="auto"><font color="#ff0000">a</font><font face="Arial" size="2">b</font></div>""",
        )

        // Assert
        assertThat(result.formattedText.text).isEqualTo("ab")
        assertThat(result.formattedText.colors).isEqualTo(listOf(ColorRange(0, 1, ColorKind.Text, 0xff0000)))
        assertThat(result.lost).isEqualTo(setOf(LostContent.Styles))
    }

    @Test
    fun `fromHtmlFragmentLenient should report a tag it knows nothing about`() {
        // Act
        val result = testSubject.fromHtmlFragmentLenient("""<div dir="auto">a<marquee>goes</marquee></div>""")

        // Assert
        assertThat(result.formattedText.text).isEqualTo("agoes")
        assertThat(result.lost).isEqualTo(setOf(LostContent.Other))
    }

    @Test
    fun `fromHtmlFragmentLenient should collapse whitespace like HTML does`() {
        // Act
        val result = testSubject.fromHtmlFragmentLenient("<div dir=\"auto\">  a   b  <br>\n  c   d  </div>")

        // Assert
        assertThat(result.formattedText.text).isEqualTo("a b\nc d")
    }

    /**
     * Writes every [FormattedText] as HTML and reads it back, which has to give what was written.
     */
    private fun assertReadBack(vararg formattedTexts: FormattedText) {
        // Act
        val results = formattedTexts.map {
            val html = testSubject.toHtmlFragment(it)
            testSubject.canRepresent(html) to testSubject.fromHtmlFragment(html)
        }

        // Assert
        assertThat(results).isEqualTo(formattedTexts.map { true to it })
    }
}
