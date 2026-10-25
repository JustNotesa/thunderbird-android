package com.fsck.k9.view

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.ColorInt
import kotlin.math.roundToInt

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

    /**
     * The text of the subject while it is collapsed: the subject and the label, or - when they don't fit into
     * [maxLines] lines - the subject shortened to fit and a dot in the colour of the account in place of the label.
     */
    fun buildCollapsed(context: Context, paint: TextPaint, width: Int, maxLines: Int): CharSequence {
        val locationLabel = label
        if (locationLabel == null || locationLabel.displayText().isEmpty()) {
            return build(context)
        }

        val fullText = build(context)

        return if (fitsIntoLines(fullText, paint, width, maxLines)) {
            fullText
        } else {
            buildShortForm(context, paint, width, maxLines, locationLabel)
        }
    }

    /** Whether the text takes up no more than the given number of lines at the given width. */
    private fun fitsIntoLines(text: CharSequence, paint: TextPaint, width: Int, maxLines: Int): Boolean {
        if (width <= 0) {
            return true
        }

        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setIncludePad(true)
            .build()

        return layout.lineCount <= maxLines
    }

    /**
     * The subject shortened to the lines it is collapsed to, followed by an ellipsis and a dot in the colour of the
     * account. The dot stands in for the label, which doesn't fit into those lines.
     */
    private fun buildShortForm(
        context: Context,
        paint: TextPaint,
        width: Int,
        maxLines: Int,
        locationLabel: LocationLabel,
    ): CharSequence {
        val dotSpan = AccountDotSpan(context, locationLabel.accountColor)
        val dotWidth = dotSpan.getSize(paint, EMPTY_TEXT, 0, 0, null)
        val spaceWidth = paint.measureText(SPACE).roundToInt()
        val subjectWidth = width - spaceWidth - dotWidth
        if (subjectWidth <= 0) {
            return subject
        }

        var shortSubject = visibleSubject(paint, subjectWidth, maxLines)
        var text = shortText(shortSubject, dotSpan)

        // The view lays the text out again, which may break the lines differently. Cut the subject shorter until the
        // ellipsis and the dot fit on the lines the subject is collapsed to.
        while (!fitsIntoLines(text, paint, width, maxLines) && shortSubject.isNotEmpty()) {
            val withoutLastWord = shortSubject.toString().trimEnd()
            val lastSpace = withoutLastWord.lastIndexOf(' ')
            shortSubject = if (lastSpace < 0) "" else withoutLastWord.substring(0, lastSpace)
            text = shortText(shortSubject, dotSpan)
        }

        return text
    }

    /** The part of the subject that is visible when it is shortened to the given number of lines. */
    private fun visibleSubject(paint: TextPaint, width: Int, maxLines: Int): CharSequence {
        val layout = StaticLayout.Builder.obtain(subject, 0, subject.length, paint, width)
            .setIncludePad(true)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setEllipsizedWidth(width)
            .build()

        val lastLine = layout.lineCount - 1
        // The start of the ellipsis is counted from the start of its line, not from the start of the text.
        val visibleEnd = if (layout.getEllipsisCount(lastLine) > 0) {
            layout.getLineStart(lastLine) + layout.getEllipsisStart(lastLine)
        } else {
            layout.getLineEnd(lastLine)
        }

        return subject.subSequence(0, visibleEnd)
    }

    /** The shortened subject, followed by an ellipsis and the dot that stands in for the label. */
    private fun shortText(subjectPart: CharSequence, dotSpan: AccountDotSpan): CharSequence {
        // The ellipsis follows the last visible word directly, without the space the line was broken at.
        val text = SpannableStringBuilder(subjectPart.trimEnd()).append(ELLIPSIS).append(SPACE).append(SPACE)
        val dotStart = text.length - 1
        text.setSpan(dotSpan, dotStart, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        return text
    }

    private companion object {
        const val SEPARATOR = " "
        const val ELLIPSIS = "…"
        const val SPACE = "\u00A0"
        const val EMPTY_TEXT = ""
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
