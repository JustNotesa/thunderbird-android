package com.fsck.k9.view

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.annotation.ColorInt

/**
 * The subject of a message as it is shown: the subject itself, followed by the account it was received on when the
 * message was opened from the unified inbox.
 *
 * Both parts can be set in either order and the text is built again whenever one of them changes. The account name is
 * part of the text and not of the subject, so a screen reader reads it along with the subject.
 */
internal class SubjectTextBuilder {
    private var subject: CharSequence = ""
    private var label: AccountLabel? = null

    fun setSubject(subject: CharSequence) {
        this.subject = subject
    }

    fun setAccountLabel(accountLabel: AccountLabel?) {
        label = accountLabel
    }

    /** The subject on its own, which is what copying the subject to the clipboard puts there. */
    fun clipboardText(): String = subject.toString()

    /** The account shown after the subject, or null when the subject stands on its own. */
    fun accountLabel(): AccountLabel? = label

    fun build(context: Context): CharSequence {
        val accountLabel = label
        if (accountLabel == null) {
            return subject
        }

        // The label is set as a span of its own, so that it can move to the next line as a whole: the space in front
        // of it is the place where the line may break, and the spaces inside it don't.
        val label = accountLabel.name.replace(" ", NON_BREAKING_SPACE)
        val text = SpannableStringBuilder(subject).append(SEPARATOR).append(label)
        val labelStart = text.length - label.length
        text.setSpan(
            AccountLabelSpan(context, accountLabel.chipColor),
            labelStart,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )

        return text
    }

    private companion object {
        const val SEPARATOR = " "
        const val NON_BREAKING_SPACE = "\u00A0"
    }
}

/** The account a message was received on, shown as a label at the end of the subject. */
internal data class AccountLabel(val name: String, @ColorInt val chipColor: Int)
