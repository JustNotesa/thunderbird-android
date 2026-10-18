package net.thunderbird.feature.mail.message.composer.internal.richtext

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.InlineImage

/**
 * The width the user chooses for an inline image, and the width an image of another client comes with.
 */
class InlineImageWidthTest {
    private val testSubject = DefaultRichTextHtmlConverter()

    @Test
    fun `should write the width the user chose into the style of the image`() {
        // Arrange
        val formattedText = imageAt(1, "image1", widthPercent = 50)

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo(
            """<div dir="auto">a<img src="cid:image1" alt="" style="width: 50%; max-width: 100%">b</div>""",
        )
    }

    @Test
    fun `should write an image without a width as wide as it naturally is`() {
        // Arrange
        val formattedText = imageAt(1, "image1")

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo(
            """<div dir="auto">a<img src="cid:image1" alt="" style="max-width: 100%">b</div>""",
        )
    }

    @Test
    fun `should write a width outside of what an image may be shown with as the nearest width it may have`() {
        // Arrange
        val formattedText = imageAt(1, "image1", widthPercent = 250)

        // Act
        val result = testSubject.toHtmlFragment(formattedText)

        // Assert
        assertThat(result).isEqualTo(
            """<div dir="auto">a<img src="cid:image1" alt="" style="width: 100%; max-width: 100%">b</div>""",
        )
    }

    @Test
    fun `should read back an image with and without a width`() {
        // Arrange
        val formattedTexts = listOf(
            imageAt(1, "image1"),
            imageAt(1, "image1", widthPercent = 25),
            imageAt(1, "image1", widthPercent = InlineImage.MAX_WIDTH_PERCENT),
            FormattedText(
                text = "￼a￼",
                ranges = emptyList(),
                images = listOf(
                    InlineImage(position = 0, contentId = "first", widthPercent = 50),
                    InlineImage(position = 2, contentId = "second"),
                ),
            ),
        )

        // Act
        val results = formattedTexts.map {
            val html = testSubject.toHtmlFragment(it)
            testSubject.canRepresent(html) to testSubject.fromHtmlFragment(html)
        }

        // Assert
        assertThat(results).isEqualTo(formattedTexts.map { true to it })
    }

    @Test
    fun `canRepresent should accept an image with and without a width`() {
        // Arrange
        val htmlsToRepresent = listOf(
            """<div dir="auto"><img src="cid:image1" alt=""></div>""",
            """<div dir="auto"><img src="cid:image1" alt="" style="max-width: 100%"></div>""",
            """<div dir="auto"><img src="cid:image1" alt="" style="width:50%;max-width:100%"></div>""",
            """<div dir="auto"><img src="cid:image1" alt="" style="width: 100%; max-width: 100%;"></div>""",
        )

        // Act
        val results = htmlsToRepresent.map { testSubject.canRepresent(it) }

        // Assert
        assertThat(results).isEqualTo(htmlsToRepresent.map { true })
    }

    @Test
    fun `canRepresent should reject a width the composer does not write`() {
        // Arrange
        val htmlThatWouldLoseSomething = listOf(
            """<div dir="auto"><img src="cid:image1" alt="" style="width: 250%; max-width: 100%"></div>""",
            """<div dir="auto"><img src="cid:image1" alt="" style="width: 5%; max-width: 100%"></div>""",
            """<div dir="auto"><img src="cid:image1" alt="" style="width: 50%"></div>""",
            """<div dir="auto"><img src="cid:image1" alt="" style="height: 50%"></div>""",
            """<div dir="auto"><img src="cid:image1" alt="" style="width: 50px; max-width: 100%"></div>""",
            """<div dir="auto"><img src="cid:image1" alt="" width="50%"></div>""",
        )

        // Act
        val results = htmlThatWouldLoseSomething.map { testSubject.canRepresent(it) }

        // Assert
        assertThat(results).isEqualTo(htmlThatWouldLoseSomething.map { false })
    }

    @Test
    fun `fromHtmlFragment should read the width out of the style of the image`() {
        // Act
        val result = testSubject.fromHtmlFragment(
            """<div dir="auto"><img src="cid:image1" alt="" style="width: 75%; max-width: 100%"></div>""",
        )

        // Assert
        assertThat(result.images).containsExactly(InlineImage(0, "image1", 75))
    }

    @Test
    fun `fromHtmlFragment should leave an image whose width it does not know its natural size`() {
        // Act
        val result = testSubject.fromHtmlFragment(
            """<div dir="auto"><img src="cid:image1" alt="" style="max-width: 100%" width="50%"></div>""",
        )

        // Assert
        assertThat(result.images).containsExactly(InlineImage(0, "image1"))
    }

    @Test
    fun `fromHtmlFragmentLenient should read the width another client wrote as an attribute`() {
        // Act
        val result = testSubject.fromHtmlFragmentLenient(
            """<div dir="auto"><img src="cid:image1" width="50%"></div>""",
        )

        // Assert
        assertThat(result.formattedText.images).containsExactly(InlineImage(0, "image1", 50))
    }

    @Test
    fun `fromHtmlFragmentLenient should read the width out of the style and the attribute`() {
        // Act
        val result = testSubject.fromHtmlFragmentLenient(
            """<div dir="auto"><img src="cid:image1" style="width: 25%" width="50%"></div>""",
        )

        // Assert
        assertThat(result.formattedText.images).containsExactly(InlineImage(0, "image1", 25))
    }

    @Test
    fun `fromHtmlFragmentLenient should clamp a width outside of what an image may be shown with`() {
        // Arrange
        val html = """<div dir="auto"><img src="cid:wide" width="250%"><img src="cid:narrow" width="2%"></div>"""

        // Act
        val result = testSubject.fromHtmlFragmentLenient(html)

        // Assert
        assertThat(result.formattedText.images).containsExactly(
            InlineImage(0, "wide", InlineImage.MAX_WIDTH_PERCENT),
            InlineImage(1, "narrow", InlineImage.MIN_WIDTH_PERCENT),
        )
    }

    @Test
    fun `fromHtmlFragmentLenient should leave an image whose width is no percentage its natural size`() {
        // Arrange
        val html = """<div dir="auto"><img src="cid:px" width="500px"><img src="cid:none"></div>"""

        // Act
        val result = testSubject.fromHtmlFragmentLenient(html)

        // Assert
        assertThat(result.formattedText.images).containsExactly(
            InlineImage(0, "px"),
            InlineImage(1, "none"),
        )
    }

    private fun imageAt(position: Int, contentId: String, widthPercent: Int? = null): FormattedText {
        return FormattedText(
            text = "a￼b",
            ranges = emptyList(),
            images = listOf(InlineImage(position = position, contentId = contentId, widthPercent = widthPercent)),
        )
    }
}
