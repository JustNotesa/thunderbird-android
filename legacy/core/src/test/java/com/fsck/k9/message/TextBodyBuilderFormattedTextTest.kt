package com.fsck.k9.message

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.legacy.logging.Log
import org.junit.Before
import org.junit.Test

/**
 * Covers [TextBodyBuilder.setMessageContentHtml], which carries text the user formatted in the composer.
 */
class TextBodyBuilderFormattedTextTest {

    @Before
    fun setUp() {
        Log.logger = TestLogger()
    }

    @Test
    fun `buildTextHtml should use the formatted text instead of converting the plain text`() {
        // Arrange
        val testSubject = createTestSubject()
        testSubject.setMessageContentHtml(MESSAGE_CONTENT_HTML)

        // Act
        val result = testSubject.buildTextHtml()

        // Assert
        assertThat(result.rawText).isEqualTo("<!DOCTYPE html><html><body>$MESSAGE_CONTENT_HTML</body></html>")
    }

    @Test
    fun `buildTextHtml should report where the formatted text is, so a draft can be opened again`() {
        // Arrange
        val testSubject = createTestSubject()
        testSubject.setMessageContentHtml(MESSAGE_CONTENT_HTML)

        // Act
        val result = testSubject.buildTextHtml()

        // Assert
        val offset = result.composedMessageOffset!!
        val length = result.composedMessageLength!!
        assertThat(result.rawText.substring(offset, offset + length)).isEqualTo(MESSAGE_CONTENT_HTML)
    }

    @Test
    fun `buildTextPlain should keep using the plain text`() {
        // Arrange
        val testSubject = createTestSubject()
        testSubject.setMessageContentHtml(MESSAGE_CONTENT_HTML)

        // Act
        val result = testSubject.buildTextPlain().rawText

        // Assert
        assertThat(result).isEqualTo(MESSAGE_CONTENT)
    }

    @Test
    fun `buildTextHtml should convert the plain text when there is no formatted text`() {
        // Arrange
        val testSubject = createTestSubject()

        // Act
        val result = testSubject.buildTextHtml().rawText

        // Assert
        assertThat(result).isEqualTo("""<!DOCTYPE html><html><body><div dir="auto">hello Jane</div></body></html>""")
    }

    private fun createTestSubject(): TextBodyBuilder {
        return TextBodyBuilder(
            MESSAGE_CONTENT,
            FakeGeneralSettingsManager(),
            FakeHtmlSignatureSanitizer(sanitizedSignature = ""),
        ).apply {
            setAppendSignature(false)
            setIncludeQuotedText(false)
        }
    }

    private companion object {
        const val MESSAGE_CONTENT = "hello Jane"
        const val MESSAGE_CONTENT_HTML = """<div dir="auto">hello <b>Jane</b></div>"""
    }
}
