package com.fsck.k9.view

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.annotation.ColorInt

/**
 * The subject of a message as it is shown: the subject itself, followed by a label that tells the user where the
 * message is, i.e. the account it was received on and/or the folder it is stored in.
 *
 * Both parts can be set in either order and the text is built again whenever one of them changes. The label is part
 * of the text and not of the subject, so a screen reader reads it along with the subject.
 */
internal class SubjectTextBuilder {
    private var subject: CharSequence = ""
    private var label: LocationLabel? = null

    fun setSubject(subject: CharSequence) {
        this.subject = subject
    }

    fun setLocationLabel(label: LocationLabel?) {
        this.label = label
    }

    /** The subject on its own, which is what copying the subject to the clipboard puts there. */
    fun clipboardText(): String = subject.toString()

    /** The label shown after the subject, or null when the subject stands on its own. */
    fun locationLabel(): LocationLabel? = label

    fun build(context: Context): CharSequence {
        val locationLabel = label
        val labelText = locationLabel?.displayText().orEmpty()
        if (locationLabel == null || labelText.isEmpty()) {
            return subject
        }

        // The label is set as a span of its own, so that it can move to the next line as a whole: the space in front
        // of it is the place where the line may break, and the spaces inside it don't.
        val text = SpannableStringBuilder(subject).append(SEPARATOR).append(labelText)
        val labelStart = text.length - labelText.length
        text.setSpan(
            AccountLabelSpan(context, locationLabel.accountColor),
            labelStart,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )

        return text
    }

    private companion object {
        const val SEPARATOR = " "
    }
}

/**
 * Where a message is: the account it was received on and/or the folder it is stored in. Both parts are optional, but
 * without either of them there is nothing to show.
 */
internal data class LocationLabel(
    val accountName: String?,
    val folderName: String?,
    @ColorInt val accountColor: Int,
) {
    /** The text of the label: the account and the folder, separated by a dot. */
    fun displayText(): String {
        val parts = listOfNotNull(accountName, folderName)

        return parts.joinToString(separator = LABEL_SEPARATOR).replace(" ", NON_BREAKING_SPACE)
    }

    private companion object {
        const val LABEL_SEPARATOR = " · "
        const val NON_BREAKING_SPACE = "\u00A0"
    }
}
