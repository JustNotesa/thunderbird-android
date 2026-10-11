package net.thunderbird.feature.account.settings.impl.ui.signature

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class HtmlFormatTest {

    @Test
    fun `bold should wrap the selection and put the cursor behind it`() {
        // Arrange
        val value = TextFieldValue(text = "Jane Doe", selection = TextRange(0, 4))

        // Act
        val result = value.applyHtmlFormat(HtmlFormat.Bold)

        // Assert
        assertThat(result.text).isEqualTo("<b>Jane</b> Doe")
        assertThat(result.selection).isEqualTo(TextRange(11))
    }

    @Test
    fun `italic without selection should insert empty tags and put the cursor between them`() {
        // Arrange
        val value = TextFieldValue(text = "Jane ", selection = TextRange(5))

        // Act
        val result = value.applyHtmlFormat(HtmlFormat.Italic)

        // Assert
        assertThat(result.text).isEqualTo("Jane <i></i>")
        assertThat(result.selection).isEqualTo(TextRange(8))
    }

    @Test
    fun `format should handle a selection made from right to left`() {
        // Arrange
        val value = TextFieldValue(text = "Jane Doe", selection = TextRange(8, 5))

        // Act
        val result = value.applyHtmlFormat(HtmlFormat.Bold)

        // Assert
        assertThat(result.text).isEqualTo("Jane <b>Doe</b>")
    }

    @Test
    fun `link should wrap the selection and put the cursor at the end of the address`() {
        // Arrange
        val value = TextFieldValue(text = "Visit our website", selection = TextRange(10, 17))

        // Act
        val result = value.applyHtmlFormat(HtmlFormat.Link)

        // Assert
        assertThat(result.text).isEqualTo("Visit our <a href=\"https://\">website</a>")
        assertThat(result.selection).isEqualTo(TextRange(27))
    }

    @Test
    fun `line break should replace the selection and put the cursor behind it`() {
        // Arrange
        val value = TextFieldValue(text = "Jane Doe", selection = TextRange(4, 5))

        // Act
        val result = value.applyHtmlFormat(HtmlFormat.LineBreak)

        // Assert
        assertThat(result.text).isEqualTo("Jane<br>Doe")
        assertThat(result.selection).isEqualTo(TextRange(8))
    }
}
