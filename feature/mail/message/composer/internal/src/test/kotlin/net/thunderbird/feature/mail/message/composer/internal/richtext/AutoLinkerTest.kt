package net.thunderbird.feature.mail.message.composer.internal.richtext

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.feature.mail.message.composer.richtext.LinkRange

class AutoLinkerTest {
    private val testSubject = AutoLinker

    @Test
    fun `should find a web address that ends a sentence`() {
        // Act
        val result = testSubject.findLinks("see https://example.com.")

        // Assert
        assertThat(result).isEqualTo(listOf(LinkRange(4, 23, "https://example.com")))
    }

    @Test
    fun `should leave the bracket that closes the sentence with the sentence`() {
        // Act
        val result = testSubject.findLinks("(https://example.com)")

        // Assert
        assertThat(result).isEqualTo(listOf(LinkRange(1, 20, "https://example.com")))
    }

    @Test
    fun `should keep the bracket the address opened itself`() {
        // Act
        val result = testSubject.findLinks("https://example.com/a_(b)")

        // Assert
        assertThat(result).isEqualTo(listOf(LinkRange(0, 25, "https://example.com/a_(b)")))
    }

    @Test
    fun `should find two web addresses`() {
        // Act
        val result = testSubject.findLinks("https://a.example and https://b.example")

        // Assert
        assertThat(result).isEqualTo(
            listOf(
                LinkRange(0, 17, "https://a.example"),
                LinkRange(22, 39, "https://b.example"),
            ),
        )
    }

    @Test
    fun `should give an address that starts with www the scheme it needs`() {
        // Act
        val result = testSubject.findLinks("www.example.com")

        // Assert
        assertThat(result).isEqualTo(listOf(LinkRange(0, 15, "https://www.example.com")))
    }

    @Test
    fun `should find nothing in text without a web address`() {
        // Act
        val result = testSubject.findLinks("nothing to see here")

        // Assert
        assertThat(result).isEqualTo(emptyList())
    }

    @Test
    fun `should not find an email address`() {
        // Act
        val result = testSubject.findLinks("write to jane@example.com")

        // Assert
        assertThat(result).isEqualTo(emptyList())
    }

    @Test
    fun `should not find an address the composer refuses`() {
        // Act
        val result = testSubject.findLinks("javascript:alert(1)")

        // Assert
        assertThat(result).isEqualTo(emptyList())
    }

    @Test
    fun `should find a web address at the start of the text`() {
        // Act
        val result = testSubject.findLinks("https://example.com hi")

        // Assert
        assertThat(result).isEqualTo(listOf(LinkRange(0, 19, "https://example.com")))
    }

    @Test
    fun `should find a web address at the end of the text`() {
        // Act
        val result = testSubject.findLinks("see https://example.com")

        // Assert
        assertThat(result).isEqualTo(listOf(LinkRange(4, 23, "https://example.com")))
    }

    @Test
    fun `should find a web address after a line break`() {
        // Act
        val result = testSubject.findLinks("one\nhttps://example.com")

        // Assert
        assertThat(result).isEqualTo(listOf(LinkRange(4, 23, "https://example.com")))
    }

    @Test
    fun `should end a web address where a tag starts`() {
        // Act
        val result = testSubject.findLinks("see https://example.com</a>")

        // Assert
        assertThat(result).isEqualTo(listOf(LinkRange(4, 23, "https://example.com")))
    }
}
