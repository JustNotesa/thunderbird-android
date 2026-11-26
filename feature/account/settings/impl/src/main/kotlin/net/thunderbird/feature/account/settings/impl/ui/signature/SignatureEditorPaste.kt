package net.thunderbird.feature.account.settings.impl.ui.signature

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.Editor
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.EditorMode
import net.thunderbird.feature.mail.message.composer.richtext.RichTextHtmlConverter

private const val CURSOR_MARKER = "\uE000"

/**
 * Puts pasted HTML at the cursor of the signature in the editor, whatever mode the editor is in.
 */
internal class SignatureEditorPaste(
    private val richTextHtmlConverter: RichTextHtmlConverter,
) {
    /**
     * Replaces the selection from [start] to [end] by [html]. Text that formatted text can hold stays formatted text,
     * anything else (images, tables) turns the editor into the HTML code mode, which loses nothing.
     */
    fun paste(editor: Editor, html: String, start: Int, end: Int): Editor {
        return if (editor.mode == EditorMode.Formatted) {
            pasteIntoFormatted(editor, html, start, end)
        } else {
            pasteIntoCode(editor, html, start, end)
        }
    }

    private fun pasteIntoFormatted(editor: Editor, html: String, start: Int, end: Int): Editor {
        if (richTextHtmlConverter.canRepresent(html)) {
            val pasted = richTextHtmlConverter.fromHtmlFragment(html)
            val state = RichTextEditorState(editor.text, editor.ranges)
                .replace(start, end, pasted.text, pasted.ranges)

            return editor.copy(
                text = state.text,
                ranges = state.ranges.toImmutableList(),
                cursor = state.selectionEnd,
            )
        }

        // The cursor is a marker in the text, so it is still found in the HTML the text is written as.
        val withMarker = RichTextEditorState(editor.text, editor.ranges).replace(start, end, CURSOR_MARKER, emptyList())
        val existing = richTextHtmlConverter.toHtmlFragment(withMarker.text, withMarker.ranges)
        val markerAt = existing.indexOf(CURSOR_MARKER)
        val newHtml = if (markerAt >= 0) {
            existing.replace(CURSOR_MARKER, html)
        } else {
            existing + (if (existing.isBlank()) "" else "\n") + html
        }

        return editor.copy(
            mode = EditorMode.Code,
            text = newHtml,
            ranges = persistentListOf(),
            cursor = if (markerAt >= 0) markerAt + html.length else newHtml.length,
        )
    }

    private fun pasteIntoCode(editor: Editor, html: String, start: Int, end: Int): Editor {
        return editor.copy(
            text = editor.text.substring(0, start) + html + editor.text.substring(end),
            cursor = start + html.length,
        )
    }
}
