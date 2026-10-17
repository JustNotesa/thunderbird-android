package net.thunderbird.feature.mail.message.composer.richtext

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test

class LinkUrlPolicyTest {
    private val testSubject = LinkUrlPolicy

    @Test
    fun `should keep an https url`() {
        // Arrange
        val input = "https://example.org/inbox?id=7"

        // Act
        val result = testSubject.normalize(input)

        // Assert
        assertThat(result).isEqualTo("https://example.org/inbox?id=7")
    }

    @Test
    fun `should keep an http url`() {
        // Arrange
        val input = "http://example.org"

        // Act
        val result = testSubject.normalize(input)

        // Assert
        assertThat(result).isEqualTo("http://example.org")
    }

    @Test
    fun `should lower case the scheme of an url typed in upper case`() {
        // Arrange
        val input = "HTTP://Example.org/Jane"

        // Act
        val result = testSubject.normalize(input)

        // Assert
        assertThat(result).isEqualTo("http://Example.org/Jane")
    }

    @Test
    fun `should add https to a host typed without a scheme`() {
        // Arrange
        val input = "www.example.org/page"

        // Act
        val result = testSubject.normalize(input)

        // Assert
        assertThat(result).isEqualTo("https://www.example.org/page")
    }

    @Test
    fun `should add mailto to an address typed without a scheme`() {
        // Arrange
        val input = "jane@example.org"

        // Act
        val result = testSubject.normalize(input)

        // Assert
        assertThat(result).isEqualTo("mailto:jane@example.org")
    }

    @Test
    fun `should keep a mailto address`() {
        // Arrange
        val input = "mailto:jane@example.org"

        // Act
        val result = testSubject.normalize(input)

        // Assert
        assertThat(result).isEqualTo("mailto:jane@example.org")
    }

    @Test
    fun `should trim the address around it`() {
        // Arrange
        val input = "  https://example.org  "

        // Act
        val result = testSubject.normalize(input)

        // Assert
        assertThat(result).isEqualTo("https://example.org")
    }

    @Test
    fun `should refuse a javascript url`() {
        // Arrange
        val input = "javascript:alert(1)"

        // Act
        val result = testSubject.normalize(input)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `should refuse a javascript url typed in upper case`() {
        // Arrange
        val input = "JavaScript:alert(1)"

        // Act
        val result = testSubject.normalize(input)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `should refuse a data url`() {
        // Arrange
        val input = "data:text/html;base64,PGgxPmhpPC9oMT4="

        // Act
        val result = testSubject.normalize(input)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `should refuse a file url`() {
        // Arrange
        val input = "file:///etc/passwd"

        // Act
        val result = testSubject.normalize(input)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `should refuse an intent url`() {
        // Arrange
        val input = "intent://scan/#Intent;scheme=zxing;end"

        // Act
        val result = testSubject.normalize(input)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `should refuse a content url`() {
        // Arrange
        val input = "content://com.example.provider/document/1"

        // Act
        val result = testSubject.normalize(input)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `should refuse an address with whitespace in it`() {
        // Arrange
        val input = "https://example.org/Jane and Jim"

        // Act
        val result = testSubject.normalize(input)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `should refuse an empty address`() {
        // Arrange
        val input = ""

        // Act
        val result = testSubject.normalize(input)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `should refuse an address that is only whitespace`() {
        // Arrange
        val input = "   "

        // Act
        val result = testSubject.normalize(input)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `should refuse an https url without a host`() {
        // Arrange
        val input = "https://"

        // Act
        val result = testSubject.normalize(input)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `should refuse an https url whose host follows the scheme without a separator`() {
        // Arrange
        val input = "https:example.org"

        // Act
        val result = testSubject.normalize(input)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `should refuse a mailto address without an address`() {
        // Arrange
        val input = "mailto:"

        // Act
        val result = testSubject.normalize(input)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `should refuse text that is no address`() {
        // Arrange
        val input = "hello"

        // Act
        val result = testSubject.normalize(input)

        // Assert
        assertThat(result).isNull()
    }
}
