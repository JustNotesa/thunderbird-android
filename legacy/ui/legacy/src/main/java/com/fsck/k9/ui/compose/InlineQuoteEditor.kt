package com.fsck.k9.ui.compose

import android.app.Activity
import com.fsck.k9.ui.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.RichTextHtmlConverter
import net.thunderbird.feature.mail.message.composer.richtext.RichTextHtmlConverter.LostContent

/**
 * Receives the text of the message once the quote has been written into it.
 */
fun interface OnQuoteMerged {
    fun onQuoteMerged(merged: MergedQuote)
}

/**
 * Lets the user edit the quote of a reply as part of the formatted text of the message.
 *
 * The quoted HTML is read into the composer's text model. What the model can't hold is dropped, so the user is asked
 * first when there is any, and told what it is.
 */
class InlineQuoteEditor(
    private val activity: Activity,
    private val converter: RichTextHtmlConverter,
) {
    /**
     * Merges the quote into [body] and hands the result to [onMerged], after the user agreed to what is lost, if
     * anything is. Nothing happens if they cancel.
     *
     * @param quoteHtml the quoted message as HTML, without the lines that introduce it
     * @param header the lines that introduce the quote, as plain text
     * @param availableContentIds the Content-IDs of the images of the quoted message that can be sent along
     */
    fun edit(
        quoteHtml: String,
        header: String,
        body: FormattedText,
        replyAfterQuote: Boolean,
        availableContentIds: Set<String>,
        onMerged: OnQuoteMerged,
    ) {
        val read = converter.fromHtmlFragmentLenient(quoteHtml)
        val quote = read.formattedText.withoutImagesOutside(availableContentIds)
        val imagesLost = quote.images.size != read.formattedText.images.size
        val lost = if (imagesLost) read.lost + LostContent.Images else read.lost
        val merge = { onMerged.onQuoteMerged(QuoteMerger.merge(body, header, quote, replyAfterQuote)) }

        if (lost.isEmpty()) {
            merge()
        } else {
            askAboutLoss(lost, merge)
        }
    }

    private fun askAboutLoss(lost: Set<LostContent>, merge: () -> Unit) {
        val names = lost.map { content -> activity.getString(content.nameRes()) }.distinct()

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.message_compose_edit_quote_dialog_title)
            .setMessage(activity.getString(R.string.message_compose_edit_quote_dialog_message, names.joinToString()))
            .setPositiveButton(R.string.message_compose_edit_quote_dialog_confirm) { _, _ -> merge() }
            .setNegativeButton(com.fsck.k9.ui.base.R.string.cancel_action, null)
            .show()
    }

    private fun LostContent.nameRes(): Int {
        return when (this) {
            LostContent.Tables -> R.string.message_compose_edit_quote_lost_tables
            LostContent.Images -> R.string.message_compose_edit_quote_lost_images
            LostContent.Styles, LostContent.Other -> R.string.message_compose_edit_quote_lost_styles
        }
    }
}
