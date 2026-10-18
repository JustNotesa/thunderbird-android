package com.fsck.k9.ui.compose.richtext

import android.view.KeyEvent
import android.widget.EditText
import com.fsck.k9.ui.compose.richtext.bar.FormatBarEvent
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

/**
 * Reacts to the keys pressed in the message text field while the message is written as formatted text: Ctrl+B, Ctrl+I
 * and Ctrl+U format, Ctrl+Z undoes, Ctrl+Shift+Z and Ctrl+Y redo.
 *
 * Backspace at the start of a list item where there is no line break to delete, e.g. the first line, takes the
 * formatting of the line away instead of a character.
 */
internal class FormatKeys(
    private val messageContent: EditText,
    private val onShortcut: (FormatBarEvent) -> Unit,
    private val onFormatChanged: () -> Unit,
) {
    /**
     * Whether the key is handled at all: only formatted text is formatted with a key.
     */
    fun onKey(keyCode: Int, event: KeyEvent, canFormat: Boolean): Boolean {
        val start = messageContent.selectionStart
        val shortcut = if (event.isCtrlPressed) shortcutEvent(keyCode, event.isShiftPressed) else null
        val handled = when {
            event.action != KeyEvent.ACTION_DOWN || !canFormat -> false
            shortcut != null -> {
                onShortcut(shortcut)
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
