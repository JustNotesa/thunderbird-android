package com.fsck.k9.ui.compose.richtext

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.thunderbird.core.android.testing.RobolectricTest
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.LinkRange
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat
import org.junit.Test

class PasteNormalizerTest : RobolectricTest() {
    private val editText = EditText(ApplicationProvider.getApplicationContext()).apply {
        addTextChangedListener(PasteNormalizer())
    }

    private fun paste(text: String, vararg spans: Pair<Any, String>) {
        val pasted = SpannableString(text)
        for ((span, word) in spans) {
            val start = text.indexOf(word)
            pasted.setSpan(span, start, start + word.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        editText.text.insert(0, pasted)
    }

    private fun span(what: Any, word: String) = what to word

    @Test
    fun `pasting should keep bold and safe links and drop the rest`() {
        // Arrange
        val text = "bold link script absolute cursive tiny quote"

        // Act
        paste(
            text,
            span(StyleSpan(Typeface.BOLD), "bold"),
            span(URLSpan("https://example.com"), "link"),
            span(URLSpan("javascript:x"), "script"),
            span(AbsoluteSizeSpan(40), "absolute"),
            span(TypefaceSpan("cursive"), "cursive"),
            span(RelativeSizeSpan(1.05f), "tiny"),
            span(QuoteSpan(), "quote"),
        )

        // Assert
        val formatted = RichTextFormatter.formattedText(editText.text)
        assertThat(formatted.ranges).containsOnly(FormattedRange(0, 4, TextFormat.Bold))
        assertThat(formatted.links).containsOnly(LinkRange(5, 9, "https://example.com"))
        val spanned = editText.text
        assertThat(spanned.getSpans(0, spanned.length, URLSpan::class.java)).hasSize(1)
        assertThat(spanned.getSpans(0, spanned.length, AbsoluteSizeSpan::class.java)).isEmpty()
        assertThat(spanned.getSpans(0, spanned.length, TypefaceSpan::class.java)).isEmpty()
        assertThat(spanned.getSpans(0, spanned.length, QuoteSpan::class.java)).isEmpty()
        assertThat(spanned.getSpans(0, spanned.length, RelativeSizeSpan::class.java)).isEmpty()
    }

    @Test
    fun `pasting should map a big relative size to the huge size`() {
        // Arrange
        val text = "headline text"

        // Act
        paste(text, span(RelativeSizeSpan(1.5f), "headline"))

        // Assert
        val formatted = RichTextFormatter.formattedText(editText.text)
        assertThat(formatted.ranges).containsOnly(FormattedRange(0, 8, TextFormat.SizeHuge))
    }

    @Test
    fun `typing a single character should leave its spans alone`() {
        // Arrange
        val character = SpannableString("a")
        character.setSpan(AbsoluteSizeSpan(40), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Act
        editText.text.insert(0, character)

        // Assert
        assertThat(editText.text.getSpans(0, 1, AbsoluteSizeSpan::class.java)).hasSize(1)
    }
}
