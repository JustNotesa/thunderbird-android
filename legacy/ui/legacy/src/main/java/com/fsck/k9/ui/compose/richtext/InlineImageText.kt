package com.fsck.k9.ui.compose.richtext

import android.text.Editable
import android.text.Spannable
import android.text.Spanned
import android.widget.EditText
import net.thunderbird.feature.mail.message.composer.richtext.InlineImage

private const val LINE_BREAK = '\n'

/**
 * Puts the spans of [images] over their [IMAGE_CHARACTER]. An image whose character is not there is left out.
 */
internal fun Spannable.setImageSpans(images: List<InlineImage>, inlineImages: InlineImages) {
    for (image in images) {
        if (image.position in 0 until length && this[image.position] == IMAGE_CHARACTER) {
            setSpan(
                inlineImages.spanFor(image.contentId, image.widthPercent),
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
        .map { span -> InlineImage(getSpanStart(span), span.contentId, span.widthPercent) }
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
 * The inline image in this text that has this Content-ID, `null` where there is none.
 */
internal fun Spanned.imageSpanOf(contentId: String): InlineImageSpan? {
    return getSpans(0, length, InlineImageSpan::class.java).firstOrNull { span -> span.contentId == contentId }
}

/**
 * Shows the pictures that became available since the text was built in place of their placeholders.
 */
internal fun Editable.refreshInlineImages(images: InlineImages) {
    for (span in getSpans(0, length, InlineImageSpan::class.java)) {
        val replacement = if (span.isPlaceholder) images.spanFor(span.contentId, span.widthPercent) else null
        if (replacement != null && !replacement.isPlaceholder) {
            val start = getSpanStart(span)
            val end = getSpanEnd(span)
            val flags = getSpanFlags(span)
            removeSpan(span)
            setSpan(replacement, start, end, flags)
        }
    }
}

/**
 * Gives the image [span] the width [widthPercent] and puts it over the same range again, which makes the text field
 * lay the image out again.
 */
internal fun Spannable.setWidthPercent(span: InlineImageSpan, widthPercent: Int) {
    if (span.widthPercent == widthPercent) return

    span.widthPercent = widthPercent
    redrawImage(span)
}

/**
 * Puts the image [span] over the same range again, so that the text field draws it anew: it keeps what it drew of its
 * text as long as the text doesn't change.
 */
internal fun Spannable.redrawImage(span: InlineImageSpan) {
    val start = getSpanStart(span)
    if (start < 0) return

    val end = getSpanEnd(span)
    val flags = getSpanFlags(span)
    removeSpan(span)
    setSpan(span, start, end, flags)
}

/**
 * The inline image the touch at [x], [y] stands on, `null` where there is none.
 */
internal fun EditText.imageSpanAt(x: Float, y: Float): InlineImageSpan? {
    val textLayout = layout ?: return null
    val textX = x - totalPaddingLeft + scrollX
    val line = textLayout.getLineForVertical((y - totalPaddingTop + scrollY).toInt())
    val offset = textLayout.getOffsetForHorizontal(line, textX)

    val span = text.getSpans(0, text.length, InlineImageSpan::class.java)
        .firstOrNull { image -> text.getSpanStart(image) in offset - 1..offset }

    return span?.takeIf { image ->
        // The nearest character can be the image without the finger being on it, e.g. in the empty space beside it.
        val bounds = image.drawable.bounds
        val left = textLayout.getPrimaryHorizontal(line) + bounds.left

        textX >= left && textX <= left + bounds.width()
    }
}

/**
 * The width the image is drawn with as a percentage of [textWidth]: what a swipe over an image starts from when the
 * user has not chosen a width for it yet.
 */
internal fun InlineImageSpan.naturalPercent(textWidth: Int): Int {
    if (textWidth <= 0) return InlineImage.MAX_WIDTH_PERCENT

    val percent = drawable.bounds.width() * InlineImage.MAX_WIDTH_PERCENT / textWidth

    return percent.coerceIn(InlineImage.MIN_WIDTH_PERCENT, InlineImage.MAX_WIDTH_PERCENT)
}
