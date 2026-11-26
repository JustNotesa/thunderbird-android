package net.thunderbird.feature.account.settings.impl.ui.signature

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.core.android.account.AccountSignature
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.Editor
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.EditorEvent
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.EditorMode
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.Event
import net.thunderbird.feature.mail.message.composer.richtext.RichTextHtmlConverter
import net.thunderbird.feature.mail.message.composer.signature.HtmlSignatureSanitizer
import net.thunderbird.feature.mail.message.composer.signature.SignatureImageLimits

/**
 * Translates between a saved signature and what the signature editor shows in its two modes.
 *
 * A signature is saved as plain text or as HTML. The editor shows it as text the user formats directly whenever
 * that loses nothing, and as HTML code otherwise.
 */
internal class SignatureEditorMapper(
    private val htmlSignatureSanitizer: HtmlSignatureSanitizer,
    private val richTextHtmlConverter: RichTextHtmlConverter,
) {
    private val editorPaste = SignatureEditorPaste(richTextHtmlConverter)

    fun toEditor(signature: AccountSignature): Editor {
        val editor = Editor(signatureId = signature.id, name = signature.name)

        return when {
            !signature.isHtml -> editor.copy(mode = EditorMode.Formatted, text = signature.text)
            richTextHtmlConverter.canRepresent(signature.text) -> editor.withFormattedTextFrom(signature.text)
            else -> withPreview(editor.copy(mode = EditorMode.Code, text = signature.text))
        }
    }

    fun toSignature(editor: Editor, signatureId: String): AccountSignature {
        val isHtml = editor.mode == EditorMode.Code || editor.ranges.isNotEmpty()

        return AccountSignature(
            id = signatureId,
            name = editor.name.trim(),
            text = if (isHtml) toHtml(editor) else editor.text,
            isHtml = isHtml,
        )
    }

    /**
     * Applies a change the user made in the editor.
     */
    fun onEvent(editor: Editor, event: EditorEvent): Editor {
        val changedEditor = when (event) {
            is Event.OnNameChange -> editor.copy(name = event.name)

            is Event.OnTextChange -> editor.copy(text = event.text)

            is Event.OnFormattedTextChange -> editor.copy(text = event.text, ranges = event.ranges.toImmutableList())

            is Event.OnModeChange -> if (canSwitchWithoutLoss(editor, event.mode)) {
                switchMode(editor, event.mode)
            } else {
                editor.copy(isConfirmingFormattedMode = true)
            }

            Event.OnFormattedModeConfirm -> switchMode(editor, EditorMode.Formatted)
                .copy(isConfirmingFormattedMode = false)

            Event.OnFormattedModeDismiss -> editor.copy(isConfirmingFormattedMode = false)
        }

        return withPreview(changedEditor.copy(cursor = null))
    }

    /**
     * The size of the images the signature in [editor] embeds, which count towards the limit for pasted images.
     */
    fun embeddedImageBytes(editor: Editor): Int {
        if (editor.mode != EditorMode.Code) return 0

        return SignatureImageLimits.embeddedImageBytes(editor.text)
    }

    /**
     * Puts pasted HTML at the cursor of the signature, replacing the selection from [selectionStart] to
     * [selectionEnd]. Text that formatted text can hold stays formatted text, anything else (images, tables) turns
     * the editor into the HTML code mode, which loses nothing.
     */
    fun onPasted(editor: Editor, html: String, selectionStart: Int, selectionEnd: Int): Editor {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, editor.text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(start, editor.text.length)
        val pasted = editorPaste.paste(editor, html, start, end)

        return withPreview(pasted.copy(revision = editor.revision + 1))
    }

    /**
     * Whether switching [editor] to [mode] keeps everything. Only going from HTML code to formatted text can lose
     * something, namely the HTML the formatted editor can't show.
     */
    private fun canSwitchWithoutLoss(editor: Editor, mode: EditorMode): Boolean {
        return editor.mode == mode || mode == EditorMode.Code || richTextHtmlConverter.canRepresent(editor.text)
    }

    private fun switchMode(editor: Editor, mode: EditorMode): Editor {
        return when {
            editor.mode == mode -> editor

            mode == EditorMode.Code -> withPreview(
                editor.copy(mode = EditorMode.Code, text = toHtml(editor), ranges = persistentListOf()),
            )

            else -> editor.withFormattedTextFrom(editor.text)
        }
    }

    /**
     * Updates the rendered preview, which only the HTML code mode has.
     */
    private fun withPreview(editor: Editor): Editor {
        return editor.copy(
            previewHtml = if (editor.mode == EditorMode.Code) htmlSignatureSanitizer.sanitize(editor.text) else null,
        )
    }

    private fun toHtml(editor: Editor): String {
        return when (editor.mode) {
            EditorMode.Code -> editor.text
            EditorMode.Formatted -> richTextHtmlConverter.toHtmlFragment(editor.text, editor.ranges)
        }
    }

    private fun Editor.withFormattedTextFrom(html: String): Editor {
        val formattedText = richTextHtmlConverter.fromHtmlFragment(html)

        return copy(
            mode = EditorMode.Formatted,
            text = formattedText.text,
            ranges = formattedText.ranges.toImmutableList(),
            previewHtml = null,
        )
    }
}
