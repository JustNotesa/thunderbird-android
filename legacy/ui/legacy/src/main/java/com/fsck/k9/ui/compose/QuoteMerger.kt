package com.fsck.k9.ui.compose

import net.thunderbird.feature.mail.message.composer.richtext.FormattedText

/**
 * The text of a message with its quote written into it.
 *
 * @property cursor where the user continues writing: at the end of what they have written so far
 */
data class MergedQuote(val text: FormattedText, val cursor: Int)

/**
 * Puts the quote of a reply into the text the user is writing, so that the quote can be edited like the rest of it.
 */
internal object QuoteMerger {
    /**
     * @param body what the user has written so far
     * @param header the lines that introduce the quote, as plain text (e.g. "On Monday, Max wrote:")
     * @param quote the quoted message
     * @param replyAfterQuote whether the quote comes before [body], which is how the account wants replies written
     */
    fun merge(body: FormattedText, header: String, quote: FormattedText, replyAfterQuote: Boolean): MergedQuote {
        val quoteBlock = quote.asQuoteBlock()
        val introduction = FormattedText(header, emptyList())
        val quoted = if (header.isEmpty()) quoteBlock else joinText(introduction, "\n", quoteBlock)
        // One empty line between the two; without text of the user the empty line is the place to start writing.
        val separator = if (body.text.isEmpty()) "\n" else "\n\n"

        return if (replyAfterQuote) {
            val merged = joinText(quoted, separator, body)
            MergedQuote(merged, merged.text.length)
        } else {
            MergedQuote(joinText(body, separator, quoted), body.text.length)
        }
    }
}
