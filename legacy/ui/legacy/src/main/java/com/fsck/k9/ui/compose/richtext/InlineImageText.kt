package com.fsck.k9.ui.compose.richtext

import android.text.Editable
import android.text.Spannable
import android.text.Spanned
import net.thunderbird.feature.mail.message.composer.richtext.InlineImage

private const val LINE_BREAK = '\n'

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
 * Replaces the selection `start..end` by the image with this Content-ID. The image gets a line of its own, like in
 * other mail apps: a line break is added before it unless it is at the start of a line, and after it unless a line
 * break follows already. Returns where the cursor belongs afterwards, at the start of the line after the image.
 */
internal fun Editable.insertInlineImage(contentId: String, start: Int, end: Int, images: InlineImages): Int {
    val from = minOf(start, end).coerceIn(0, length)
    val to = maxOf(start, end).coerceIn(0, length)
    val breakBefore = from > 0 && this[from - 1] != LINE_BREAK
    val breakAfter = to >= length || this[to] != LINE_BREAK

    val prefix = if (breakBefore) LINE_BREAK.toString() else ""
    val suffix = if (breakAfter) LINE_BREAK.toString() else ""
    replace(from, to, prefix + IMAGE_CHARACTER + suffix)
    val imageStart = from + prefix.length
    setSpan(images.spanFor(contentId), imageStart, imageStart + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

    // Behind an existing line break the cursor goes to the next line too.
    return (imageStart + 1 + (if (breakAfter) suffix.length else 1)).coerceAtMost(length)
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
