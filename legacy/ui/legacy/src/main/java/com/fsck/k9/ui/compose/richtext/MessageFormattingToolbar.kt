package com.fsck.k9.ui.compose.richtext

import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.Selection
import android.text.SpanWatcher
import android.text.Spannable
import android.text.Spanned
import android.view.KeyEvent
import android.widget.EditText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.fsck.k9.ui.compose.richtext.bar.FormatBarEvent
import com.fsck.k9.ui.compose.richtext.bar.FormatBarState
import com.fsck.k9.ui.compose.richtext.bar.MessageFormatBar
import com.fsck.k9.ui.compose.richtext.bar.applyFormatBarEvent
import com.fsck.k9.ui.compose.richtext.bar.formatBarStateOf
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

/**
 * Connects the format bar above the keyboard to the message text field of the composer.
 *
 * A button formats the selected text or, without a selection, what is typed next; the bar shows the formatting at
 * the selection. The bar is only shown while the message text has the focus and the message is written as formatted
 * HTML. Typing keeps lists and quotes going ([ParagraphEditWatcher]); Ctrl+B, Ctrl+I and Ctrl+U format on a hardware
 * keyboard.
 *
 * The text field can also show the message as HTML code. The bar is hidden then, and the code is shown in a
 * fixed-width font without spelling suggestions.
 *
 * @param onFormatChange called when the user changed the formatting, which a text change listener doesn't notice
 */
class MessageFormattingToolbar(
    private val formatBar: ComposeView,
    private val messageContent: EditText,
    featureThemeProvider: FeatureThemeProvider,
    private val onFormatChange: Runnable,
) {
    private var barState by mutableStateOf(FormatBarState(isUndoVisible = true))
    private val linkEditor = LinkEditor(messageContent)
    private val history = EditHistory(messageContent, onChange = { updateState() })

    private val selectionWatcher = object : SpanWatcher {
        override fun onSpanAdded(text: Spannable, what: Any, start: Int, end: Int) = onSpanMoved(what)

        override fun onSpanRemoved(text: Spannable, what: Any, start: Int, end: Int) = Unit

        override fun onSpanChanged(text: Spannable, what: Any, ostart: Int, oend: Int, nstart: Int, nend: Int) {
            onSpanMoved(what)
        }

        private fun onSpanMoved(what: Any) {
            if (what === Selection.SELECTION_START || what === Selection.SELECTION_END) {
                updateState()
            }
        }
    }

    /**
     * Whether the message is written as HTML at all. A plain text message has no formatting.
     */
    var isFormattingAvailable: Boolean = false
        set(value) {
            field = value
            updateVisibility()
        }

    /**
     * Whether the text field shows the message as HTML code instead of as formatted text.
     */
    var isHtmlSourceMode: Boolean = false
        private set

    private var formattedTextTypeface: Typeface? = null
    private var formattedTextInputType: Int = messageContent.inputType

    /**
     * The message text with all its formatting.
     */
    val formattedText: FormattedText
        get() = RichTextFormatter.formattedText(messageContent.text)

    init {
        formatBar.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        formatBar.setContent {
            featureThemeProvider.WithTheme {
                MessageFormatBar(state = barState, onEvent = ::onEvent)
            }
        }

        messageContent.addTextChangedListener(ParagraphEditWatcher { isFormattingAvailable && !isHtmlSourceMode })
        messageContent.addTextChangedListener(PasteNormalizer())
        // The text field gets a new Editable when its text is set as a whole, which drops the watcher.
        messageContent.doAfterTextChanged { text ->
            if (text != null) watchSelection(text)
            updateState()
        }
        messageContent.setOnKeyListener { _, keyCode, event -> onKey(keyCode, event) }
        watchSelection(messageContent.text)
        updateState()
    }

    /**
     * To be called when the message text gains or loses the focus: the bar is only shown while it has it.
     */
    fun onFocusChange(hasFocus: Boolean) {
        updateVisibility(hasFocus)
    }

    /**
     * Shows formatted text in the text field, e.g. the one of a draft, and leaves the HTML code mode.
     */
    fun showFormattedText(formattedText: FormattedText) {
        setHtmlSourceMode(false)
        messageContent.setText(RichTextFormatter.toSpanned(formattedText))
    }

    /**
     * Shows HTML code in the text field and enters the HTML code mode.
     */
    fun showHtmlSource(html: String) {
        setHtmlSourceMode(true)
        messageContent.setText(html)
    }

    /**
     * Sets the mode without touching the text, for when the text field restored its text itself.
     */
    fun setHtmlSourceMode(htmlSourceMode: Boolean) {
        if (htmlSourceMode == isHtmlSourceMode) return

        if (htmlSourceMode) {
            formattedTextTypeface = messageContent.typeface
            formattedTextInputType = messageContent.inputType
            messageContent.inputType = formattedTextInputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            messageContent.typeface = Typeface.MONOSPACE
        } else {
            messageContent.inputType = formattedTextInputType
            messageContent.typeface = formattedTextTypeface
        }

        isHtmlSourceMode = htmlSourceMode
        updateVisibility()
    }

    private fun onEvent(event: FormatBarEvent) {
        val start = minOf(messageContent.selectionStart, messageContent.selectionEnd)
        val end = maxOf(messageContent.selectionStart, messageContent.selectionEnd)
        if (start < 0) return

        when (event) {
            FormatBarEvent.Undo -> history.undo()
            FormatBarEvent.Redo -> history.redo()
            FormatBarEvent.EditLink -> {
                history.beforeFormatChange()
                linkEditor.edit { onFormatChanged() }
            }
            else -> {
                history.beforeFormatChange()
                if (applyFormatBarEvent(messageContent.text, event, start, end)) onFormatChanged()
            }
        }
    }

    private fun onFormatChanged() {
        updateState()
        onFormatChange.run()
    }

    private fun onKey(keyCode: Int, event: KeyEvent): Boolean {
        val start = messageContent.selectionStart
        val shortcut = if (event.isCtrlPressed) shortcutEvent(keyCode, event.isShiftPressed) else null
        val handled = when {
            event.action != KeyEvent.ACTION_DOWN || !isFormattingAvailable || isHtmlSourceMode -> false
            shortcut != null -> {
                onEvent(shortcut)
                true
            }
            // Backspace at the start of a list item where there is no line break to delete, e.g. the first line.
            keyCode == KeyEvent.KEYCODE_DEL && start == messageContent.selectionEnd ->
                ParagraphFormatter.removeBlockAtLineStart(messageContent.text, start).also { removed ->
                    if (removed) onFormatChanged()
                }
            else -> false
        }

        return handled
    }

    private fun updateState() {
        val start = messageContent.selectionStart
        val end = messageContent.selectionEnd
        if (start < 0 || end < 0) return

        barState = formatBarStateOf(messageContent.text, start, end, barState)
            .copy(canUndo = history.canUndo, canRedo = history.canRedo)
    }

    private fun updateVisibility(hasFocus: Boolean = messageContent.hasFocus()) {
        formatBar.isVisible = isFormattingAvailable && !isHtmlSourceMode && hasFocus
    }

    private fun watchSelection(text: Editable) {
        if (text.getSpanStart(selectionWatcher) == -1) {
            text.setSpan(selectionWatcher, 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
    }

    private companion object {
        val SHORTCUT_FORMATS = mapOf(
            KeyEvent.KEYCODE_B to TextFormat.Bold,
            KeyEvent.KEYCODE_I to TextFormat.Italic,
            KeyEvent.KEYCODE_U to TextFormat.Underline,
        )

        /**
         * The event of a Ctrl shortcut: B, I and U format, Z undoes, Shift+Z and Y redo.
         */
        fun shortcutEvent(keyCode: Int, isShiftPressed: Boolean): FormatBarEvent? {
            return when {
                keyCode == KeyEvent.KEYCODE_Z && !isShiftPressed -> FormatBarEvent.Undo
                keyCode == KeyEvent.KEYCODE_Z || keyCode == KeyEvent.KEYCODE_Y -> FormatBarEvent.Redo
                else -> SHORTCUT_FORMATS[keyCode]?.let { FormatBarEvent.ToggleFormat(it) }
            }
        }
    }
}
