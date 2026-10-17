package com.fsck.k9.ui.compose.richtext

import android.text.Editable
import android.text.Spanned
import android.text.style.URLSpan
import net.thunderbird.feature.mail.message.composer.richtext.LinkRange
import net.thunderbird.feature.mail.message.composer.richtext.LinkUrlPolicy

/**
 * Turns parts of the text of the message composer into links and reads them back.
 *
 * A link is always a whole range of the text; half a link is no link the user could recognise.
 */
internal object LinkFormatter {
    /**
     * Turns `start..end` into the link to [url]. A link that reaches into that range is removed first, and the new link
     * does not grow while the user types at its end. The url has to be one the composer accepts; the caller gets it
     * from [LinkUrlPolicy].
     */
    fun setLink(text: Editable, url: String, start: Int, end: Int) {
        text.removeLinksIn(start, end)
        text.setSpan(URLSpan(url), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /**
     * Removes the link the position is in, as a whole: half a link is no link the user could recognise.
     */
    fun removeLink(text: Editable, position: Int) {
        for (span in text.linkSpans()) {
            if (text.getSpanStart(span) <= position && position <= text.getSpanEnd(span)) {
                text.removeSpan(span)
            }
        }
    }

    /**
     * The link the selection is in, or `null` when it is not part of a link.
     */
    fun linkAt(text: Spanned, selectionStart: Int, selectionEnd: Int): LinkRange? {
        val start = minOf(selectionStart, selectionEnd)
        val end = maxOf(selectionStart, selectionEnd)

        return text.linkSpans()
            .map { span -> LinkRange(text.getSpanStart(span), text.getSpanEnd(span), span.url) }
            .firstOrNull { link -> link.start <= start && end <= link.end }
    }
}

/**
 * Removes the links that reach into `start..end`. A link is taken as a whole; half of it is no link the user could
 * recognise.
 */
internal fun Editable.removeLinksIn(start: Int, end: Int) {
    for (span in linkSpans()) {
        // A link that only touches the range is a link of its own and stays.
        if (getSpanStart(span) < end && start < getSpanEnd(span)) {
            removeSpan(span)
        }
    }
}

/**
 * The links of the text. A link whose url the composer refuses is left out.
 */
internal fun Spanned.linkRanges(): List<LinkRange> {
    return linkSpans()
        .mapNotNull { span ->
            val url = LinkUrlPolicy.normalize(span.url) ?: return@mapNotNull null
            LinkRange(getSpanStart(span), getSpanEnd(span), url)
        }
        .filter { link -> link.start < link.end }
        .sortedBy { link -> link.start }
}

internal fun Spanned.linkSpans(): List<URLSpan> {
    return getSpans(0, length, URLSpan::class.java)
        .filter { span -> !isComposingSpan(span) }
}
