package com.fsck.k9.ui.compose.richtext

import android.text.Editable
import android.text.Spannable
import android.text.Spanned
import net.thunderbird.feature.mail.message.composer.richtext.InlineImage

/**
 * Puts the spans of [images] over their [IMAGE_CHARACTER]. An image whose character is not there is left out.
 */
internal fun Spannable.setImageSpans(images: List<InlineImage>, inlineImages: InlineImages) {
    for (image in images) {
        if (image.position in 0 until length && this[image.position] == IMAGE_CHARACTER) {
            setSpan(
                inlineImages.spanFor(image.contentId),
                image.position,
                image.position + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
}

/**
 * The inline images in this text, in the order of the text.
 */
internal fun Spanned.inlineImages(): List<InlineImage> {
    return getSpans(0, length, InlineImageSpan::class.java)
        .map { span -> InlineImage(getSpanStart(span), span.contentId) }
        .filter { image -> image.position in 0 until length && get(image.position) == IMAGE_CHARACTER }
        .sortedBy { image -> image.position }
}

/**
 * Replaces the selection `start..end` by the image with this Content-ID.
 */
internal fun Editable.insertInlineImage(contentId: String, start: Int, end: Int, images: InlineImages) {
    val from = minOf(start, end).coerceIn(0, length)
    val to = maxOf(start, end).coerceIn(0, length)

    replace(from, to, IMAGE_CHARACTER.toString())
    setSpan(images.spanFor(contentId), from, from + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

/**
 * Shows the pictures that became available since the text was built in place of their placeholders.
 */
internal fun Editable.refreshInlineImages(images: InlineImages) {
    for (span in getSpans(0, length, InlineImageSpan::class.java)) {
        val replacement = if (span.isPlaceholder) images.spanFor(span.contentId) else null
        if (replacement != null && !replacement.isPlaceholder) {
            val start = getSpanStart(span)
            val end = getSpanEnd(span)
            val flags = getSpanFlags(span)
            removeSpan(span)
            setSpan(replacement, start, end, flags)
        }
    }
}
