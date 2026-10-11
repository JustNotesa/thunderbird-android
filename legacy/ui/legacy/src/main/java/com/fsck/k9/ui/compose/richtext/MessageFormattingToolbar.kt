package com.fsck.k9.ui.compose.richtext

import android.text.Editable
import android.text.Selection
import android.text.SpanWatcher
import android.text.Spannable
import android.text.Spanned
import android.view.View
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

/**
 * Connects the formatting buttons of the message composer to the message text field.
 *
 * A button formats the selected text or, without a selection, what is typed next. The buttons are shown as checked
 * while the selection or the cursor is in text with that format.
 *
 * @param onFormatChange called when the user changed the formatting, which a text change listener doesn't notice
 */
class MessageFormattingToolbar(
    private val toolbar: View,
    private val messageContent: EditText,
    boldButton: MaterialButton,
    italicButton: MaterialButton,
    underlineButton: MaterialButton,
    private val onFormatChange: Runnable,
) {
    private val buttons = mapOf(
        TextFormat.Bold to boldButton,
        TextFormat.Italic to italicButton,
        TextFormat.Underline to underlineButton,
    )

    private val selectionWatcher = object : SpanWatcher {
        override fun onSpanAdded(text: Spannable, what: Any, start: Int, end: Int) = onSpanMoved(what)

        override fun onSpanRemoved(text: Spannable, what: Any, start: Int, end: Int) = Unit

        override fun onSpanChanged(text: Spannable, what: Any, ostart: Int, oend: Int, nstart: Int, nend: Int) {
            onSpanMoved(what)
        }

        private fun onSpanMoved(what: Any) {
            if (what === Selection.SELECTION_START || what === Selection.SELECTION_END) {
                updateButtons()
            }
        }
    }

    var isVisible: Boolean
        get() = toolbar.isVisible
        set(value) {
            toolbar.isVisible = value
        }

    /**
     * The formatted parts of the message text, empty when the user didn't format anything.
     */
    val formattedRanges: List<FormattedRange>
        get() = RichTextFormatter.formattedRanges(messageContent.text)

    init {
        for ((format, button) in buttons) {
            button.setOnClickListener { toggle(format) }
        }

        // The text field gets a new Editable when its text is set as a whole, which drops the watcher.
        messageContent.doAfterTextChanged { text ->
            if (text != null) watchSelection(text)
            updateButtons()
        }
        watchSelection(messageContent.text)
        updateButtons()
    }

    private fun toggle(format: TextFormat) {
        RichTextFormatter.toggle(
            text = messageContent.text,
            format = format,
            selectionStart = messageContent.selectionStart,
            selectionEnd = messageContent.selectionEnd,
        )
        updateButtons()
        onFormatChange.run()
    }

    /**
     * Replaces the message text with formatted text, e.g. the one of a draft.
     */
    fun setFormattedText(formattedText: FormattedText) {
        messageContent.setText(RichTextFormatter.toSpanned(formattedText))
    }

    private fun updateButtons() {
        val text = messageContent.text
        val selectionStart = messageContent.selectionStart
        val selectionEnd = messageContent.selectionEnd
        if (selectionStart < 0 || selectionEnd < 0) return

        for ((format, button) in buttons) {
            button.isChecked = RichTextFormatter.isActive(text, format, selectionStart, selectionEnd)
        }
    }

    private fun watchSelection(text: Editable) {
        if (text.getSpanStart(selectionWatcher) == -1) {
            text.setSpan(selectionWatcher, 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
    }
}
