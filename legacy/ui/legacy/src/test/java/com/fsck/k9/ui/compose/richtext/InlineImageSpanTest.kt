package com.fsck.k9.ui.compose.richtext

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.SpannableStringBuilder
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import net.thunderbird.core.android.testing.RobolectricTest
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.InlineImage
import org.junit.Test

class InlineImageSpanTest : RobolectricTest() {
    private val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
    private val pictures = mutableMapOf<String, Drawable>()
    private val store = InlineImageStore { contentId -> pictures[contentId] }
    private val images = InlineImages(store, InlineImageLimits(maxWidth = { 400 }, maxHeight = 300))

    @Test
    fun `formattedText should report the images of the text in the order they stand`() {
        // Arrange
        pictures["b"] = picture(100, 100)
        pictures["a"] = picture(100, 100)
        val formattedText = FormattedText(
            text = "x￼y￼z",
            ranges = emptyList(),
            images = listOf(InlineImage(1, "b"), InlineImage(3, "a")),
        )

        // Act
        val result = RichTextFormatter.formattedText(RichTextFormatter.toSpanned(formattedText, images))

        // Assert
        assertThat(result).isEqualTo(formattedText)
    }

    @Test
    fun `toSpanned should show the picture the store has for an image`() {
        // Arrange
        val picture = picture(100, 100)
        pictures["a"] = picture
        val formattedText = FormattedText("￼", emptyList(), images = listOf(InlineImage(0, "a")))

        // Act
        val text = RichTextFormatter.toSpanned(formattedText, images)

        // Assert
        val span = text.getSpans(0, text.length, InlineImageSpan::class.java).single()
        assertThat(span.drawable).isSameInstanceAs(picture)
        assertThat(span.isPlaceholder).isFalse()
    }

    @Test
    fun `toSpanned should show a placeholder of a fixed size for an image the store does not have`() {
        // Arrange
        val formattedText = FormattedText("a￼", emptyList(), images = listOf(InlineImage(1, "missing")))

        // Act
        val text = RichTextFormatter.toSpanned(formattedText, images)

        // Assert
        val span = text.getSpans(0, text.length, InlineImageSpan::class.java).single()
        assertThat(span.isPlaceholder).isTrue()
        assertThat(span.contentId).isEqualTo("missing")
        assertThat(span.drawable.intrinsicWidth).isEqualTo(240)
        assertThat(span.drawable.intrinsicHeight).isEqualTo(160)
        // The image is still part of the message, so saving the draft keeps it.
        assertThat(RichTextFormatter.formattedText(text).images).containsExactly(InlineImage(1, "missing"))
    }

    @Test
    fun `toSpanned should ignore an image whose character is not in the text`() {
        // Arrange
        val formattedText = FormattedText(
            text = "abc",
            ranges = emptyList(),
            images = listOf(InlineImage(1, "a"), InlineImage(9, "b")),
        )

        // Act
        val text = RichTextFormatter.toSpanned(formattedText, images)

        // Assert
        assertThat(text.getSpans(0, text.length, InlineImageSpan::class.java).toList()).isEmpty()
    }

    @Test
    fun `formattedText should no longer report an image whose character was deleted`() {
        // Arrange
        pictures["a"] = picture(100, 100)
        val formattedText = FormattedText("a￼b", emptyList(), images = listOf(InlineImage(1, "a")))
        val text = RichTextFormatter.toSpanned(formattedText, images) as Editable

        // Act
        text.delete(1, 2)

        // Assert
        assertThat(RichTextFormatter.formattedText(text).images).isEmpty()
    }

    @Test
    fun `refreshInlineImages should replace a placeholder when the picture has arrived`() {
        // Arrange
        val formattedText = FormattedText("￼", emptyList(), images = listOf(InlineImage(0, "a")))
        val text = SpannableStringBuilder(RichTextFormatter.toSpanned(formattedText, images))
        val picture = picture(100, 100)
        pictures["a"] = picture

        // Act
        text.refreshInlineImages(images)

        // Assert
        val span = text.getSpans(0, text.length, InlineImageSpan::class.java).single()
        assertThat(span.drawable).isSameInstanceAs(picture)
        assertThat(text.getSpanStart(span)).isEqualTo(0)
        assertThat(text.getSpanEnd(span)).isEqualTo(1)
    }

    @Test
    fun `insertInlineImage should replace the selection by the image`() {
        // Arrange
        pictures["a"] = picture(100, 100)
        val text = SpannableStringBuilder("Hello Jane")

        // Act
        text.insertInlineImage("a", start = 5, end = 10, images = images)

        // Assert
        assertThat(text.toString()).isEqualTo("Hello\n￼\n")
        assertThat(RichTextFormatter.formattedText(text).images).containsExactly(InlineImage(6, "a"))
    }

    @Test
    fun `insertInlineImage should not add line breaks where the line already ends and starts`() {
        // Arrange
        pictures["a"] = picture(100, 100)
        val text = SpannableStringBuilder("One\n\nTwo")

        // Act
        val cursor = text.insertInlineImage("a", start = 4, end = 4, images = images)

        // Assert
        assertThat(text.toString()).isEqualTo("One\n￼\nTwo")
        assertThat(cursor).isEqualTo(6)
    }

    @Test
    fun `insertInlineImage should return the start of the line after the image`() {
        // Arrange
        pictures["a"] = picture(100, 100)
        val text = SpannableStringBuilder("Hello Jane")

        // Act
        val cursor = text.insertInlineImage("a", start = 5, end = 5, images = images)

        // Assert
        assertThat(text.toString()).isEqualTo("Hello\n￼\n Jane")
        assertThat(cursor).isEqualTo(8)
    }

    @Test
    fun `an image should not be drawn larger than the width of the text`() {
        // Arrange
        pictures["a"] = picture(1000, 500)
        val span = images.spanFor("a")

        // Act
        val width = span.getSize(Paint(), "￼", 0, 1, null)

        // Assert
        assertThat(width).isEqualTo(400)
        assertThat(span.drawable.bounds.height()).isEqualTo(200)
    }

    @Test
    fun `an image should not be drawn higher than the limit`() {
        // Arrange
        pictures["a"] = picture(500, 1000)
        val span = images.spanFor("a")

        // Act
        val width = span.getSize(Paint(), "￼", 0, 1, null)

        // Assert
        assertThat(span.drawable.bounds.height()).isEqualTo(300)
        assertThat(width).isEqualTo(150)
    }

    @Test
    fun `an image smaller than the limits should keep its size`() {
        // Arrange
        pictures["a"] = picture(100, 50)
        val span = images.spanFor("a")

        // Act
        val width = span.getSize(Paint(), "￼", 0, 1, null)

        // Assert
        assertThat(width).isEqualTo(100)
    }

    @Test
    fun `an image should be drawn as wide as the user chose`() {
        // Arrange
        pictures["a"] = picture(1000, 500)
        val span = images.spanFor("a", widthPercent = 50)

        // Act
        val width = span.getSize(Paint(), "￼", 0, 1, null)

        // Assert
        assertThat(width).isEqualTo(200)
        assertThat(span.drawable.bounds.height()).isEqualTo(100)
    }

    @Test
    fun `an image should never be drawn wider than the width of the text`() {
        // Arrange
        pictures["a"] = picture(1000, 500)
        val span = images.spanFor("a", widthPercent = 100)

        // Act
        val width = span.getSize(Paint(), "￼", 0, 1, null)

        // Assert
        assertThat(width).isEqualTo(400)
        assertThat(span.drawable.bounds.height()).isEqualTo(200)
    }

    @Test
    fun `an image with a width should keep its proportions when the height is the limit`() {
        // Arrange
        pictures["a"] = picture(1000, 1000)
        val span = images.spanFor("a", widthPercent = 100)

        // Act
        val width = span.getSize(Paint(), "￼", 0, 1, null)

        // Assert
        assertThat(width).isEqualTo(300)
        assertThat(span.drawable.bounds.height()).isEqualTo(300)
    }

    @Test
    fun `toSpanned should draw an image with the width it was sent with`() {
        // Arrange
        pictures["a"] = picture(1000, 500)
        val formattedText = FormattedText(
            text = "￼",
            ranges = emptyList(),
            images = listOf(InlineImage(0, "a", widthPercent = 75)),
        )

        // Act
        val text = RichTextFormatter.toSpanned(formattedText, images)

        // Assert
        val span = text.getSpans(0, text.length, InlineImageSpan::class.java).single()
        assertThat(span.widthPercent).isEqualTo(75)
        assertThat(span.getSize(Paint(), "￼", 0, 1, null)).isEqualTo(300)
    }

    @Test
    fun `formattedText should report the width the image is shown with`() {
        // Arrange
        pictures["a"] = picture(1000, 500)
        val formattedText = FormattedText(
            text = "a￼b",
            ranges = emptyList(),
            images = listOf(InlineImage(1, "a", widthPercent = 25)),
        )

        // Act
        val result = RichTextFormatter.formattedText(RichTextFormatter.toSpanned(formattedText, images))

        // Assert
        assertThat(result).isEqualTo(formattedText)
    }

    @Test
    fun `refreshInlineImages should keep the width the image is shown with`() {
        // Arrange
        val text = SpannableStringBuilder(
            RichTextFormatter.toSpanned(
                FormattedText("￼", emptyList(), images = listOf(InlineImage(0, "missing", widthPercent = 40))),
                images,
            ),
        )
        pictures["missing"] = picture(1000, 500)

        // Act
        text.refreshInlineImages(images)

        // Assert
        val span = text.getSpans(0, text.length, InlineImageSpan::class.java).single()
        assertThat(span.isPlaceholder).isFalse()
        assertThat(span.widthPercent).isEqualTo(40)
    }

    @Test
    fun `the limits of an edit text should use the width of its text`() {
        // Arrange
        val editText = EditText(ApplicationProvider.getApplicationContext())
        editText.setPadding(10, 0, 20, 0)
        editText.layout(0, 0, 500, 100)

        // Act
        val limits = InlineImageLimits.of(editText)

        // Assert
        assertThat(limits.maxWidth()).isEqualTo(470)
    }

    @Test
    fun `the paste normalizer should keep an inline image that is pasted`() {
        // Arrange
        pictures["a"] = picture(100, 100)
        val editText = EditText(ApplicationProvider.getApplicationContext())
        editText.addTextChangedListener(PasteNormalizer())
        val copied = RichTextFormatter.toSpanned(
            FormattedText("a\uFFFCb", emptyList(), images = listOf(InlineImage(1, "a"))),
            images,
        )

        // Act
        editText.text.insert(0, copied)

        // Assert
        assertThat(editText.text.formattedImages()).containsExactly(InlineImage(1, "a"))
    }

    private fun Editable.formattedImages() = RichTextFormatter.formattedText(this).images

    private fun picture(width: Int, height: Int): Drawable {
        return BitmapDrawable(resources, Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
    }
}
