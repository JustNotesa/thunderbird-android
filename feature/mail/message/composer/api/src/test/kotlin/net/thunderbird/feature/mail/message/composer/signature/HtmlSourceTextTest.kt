package net.thunderbird.feature.mail.message.composer.signature

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class HtmlSourceTextTest {
    @Test
    fun `should wrap the selection in the tags of the format and keep it selected`() {
        val testSubject = HtmlSourceText("Hello world", selectionStart = 6, selectionEnd = 11)

        val result = testSubject.withFormat(HtmlSourceFormat.Bold)

        assertThat(result).isEqualTo(HtmlSourceText("Hello <b>world</b>", 9, 14))
    }

    @Test
    fun `should insert empty tags around the cursor when nothing is selected`() {
        val testSubject = HtmlSourceText("ab", selectionStart = 1, selectionEnd = 1)

        val result = testSubject.withFormat(HtmlSourceFormat.Italic)

        assertThat(result).isEqualTo(HtmlSourceText("a<i></i>b", 4, 4))
    }

    @Test
    fun `should accept a selection made backwards`() {
        val testSubject = HtmlSourceText("abc", selectionStart = 3, selectionEnd = 1)

        val result = testSubject.withFormat(HtmlSourceFormat.Underline)

        assertThat(result.text).isEqualTo("a<u>bc</u>")
    }

    @Test
    fun `should write the size as a relative font size`() {
        val testSubject = HtmlSourceText("x", selectionStart = 0, selectionEnd = 1)

        val result = testSubject.withFormat(HtmlSourceFormat.SizeLarge)

        assertThat(result.text).isEqualTo("<span style=\"font-size:1.25em\">x</span>")
    }

    @Test
    fun `should insert html at the cursor and put the cursor behind it`() {
        val testSubject = HtmlSourceText("ab", selectionStart = 1, selectionEnd = 1)

        val result = testSubject.withInserted("<br>")

        assertThat(result).isEqualTo(HtmlSourceText("a<br>b", 5, 5))
    }

    @Test
    fun `should replace the selection when inserting html`() {
        val testSubject = HtmlSourceText("abc", selectionStart = 0, selectionEnd = 2)

        val result = testSubject.withInserted("X")

        assertThat(result).isEqualTo(HtmlSourceText("Xc", 1, 1))
    }
}

class SignatureImageLimitsTest {
    @Test
    fun `should count the embedded images of a signature`() {
        val html = "<img src=\"data:image/png;base64,AAAA\"><p>x</p><img src='data:image/jpeg;base64,AAAAAAAA'>"

        val result = SignatureImageLimits.embeddedImageBytes(html)

        assertThat(result).isEqualTo(3 + 6)
    }

    @Test
    fun `should not count images with web addresses`() {
        val result = SignatureImageLimits.embeddedImageBytes("<img src=\"https://example.test/a.png\">")

        assertThat(result).isEqualTo(0)
    }
}
