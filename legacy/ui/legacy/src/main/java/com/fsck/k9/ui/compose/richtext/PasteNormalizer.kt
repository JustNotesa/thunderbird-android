package com.fsck.k9.ui.compose.richtext

import android.graphics.Typeface
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.AbsoluteSizeSpan
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.text.style.LeadingMarginSpan
import android.text.style.MetricAffectingSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import net.thunderbird.feature.mail.message.composer.richtext.LinkUrlPolicy
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

// Sizes closer to normal than this stay normal when they are pasted.
private const val SMALL_LIMIT = 0.9f
private const val LARGE_LIMIT = 1.12f
private const val HUGE_LIMIT = 1.4f

/**
 * Keeps of pasted text the formatting the composer can send and drops the rest: fonts and sizes it doesn't offer,
 * images, buttons, links it would refuse, and the paragraph styles of other apps (the lines get the paragraph
 * formatting of the place they were pasted to).
 */
internal class PasteNormalizer : TextWatcher {
    private var insertStart = 0
    private var insertEnd = 0

    override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
        insertStart = start
        insertEnd = start + count
    }

    override fun afterTextChanged(text: Editable?) {
        if (text == null || insertEnd - insertStart < 2 || insertEnd > text.length) return

        normalize(text, insertStart, insertEnd)
    }

    private fun normalize(text: Editable, start: Int, end: Int) {
        for (span in text.getSpans(start, end, Any::class.java)) {
            if (text.getSpanFlags(span) and Spanned.SPAN_COMPOSING != 0) continue

            when {
                span is RelativeSizeSpan -> replaceSize(text, span)
                span is URLSpan -> if (LinkUrlPolicy.normalize(span.url) == null) text.removeSpan(span)
                span.isForeign() -> text.removeSpan(span)
            }
        }
    }

    private fun replaceSize(text: Editable, span: RelativeSizeSpan) {
        if (span.textFormats().isNotEmpty()) return

        val size = when {
            span.sizeChange < SMALL_LIMIT -> TextFormat.SizeSmall
            span.sizeChange > HUGE_LIMIT -> TextFormat.SizeHuge
            span.sizeChange > LARGE_LIMIT -> TextFormat.SizeLarge
            else -> null
        }
        val start = text.getSpanStart(span)
        val end = text.getSpanEnd(span)
        text.removeSpan(span)
        if (size != null) text.setSpan(size.createSpan(), start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
    }

    private fun Any.isForeign(): Boolean {
        return when (this) {
            is InlineImageSpan -> false
            is ImageSpan, is AbsoluteSizeSpan, is QuoteSpan -> true
            is ClickableSpan -> this !is URLSpan
            is StyleSpan -> style == Typeface.NORMAL
            is TypefaceSpan -> textFormats().isEmpty()
            is LeadingMarginSpan -> !isComposerParagraphSpan()
            is MetricAffectingSpan -> this !is StyleSpan && textFormats().isEmpty()
            else -> false
        }
    }
}
