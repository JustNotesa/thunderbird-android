package com.fsck.k9.ui.compose.richtext.bar

import android.text.Editable
import com.fsck.k9.ui.compose.richtext.ColorFormatter
import com.fsck.k9.ui.compose.richtext.ParagraphFormatter
import com.fsck.k9.ui.compose.richtext.RichTextFormatter
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

/**
 * Applies a [FormatBarEvent] that changes the formatting of the selection `selectionStart..selectionEnd` of [text].
 *
 * Returns `false` for the events that are not about formatting the text (links, images, undo), which the caller
 * handles itself.
 */
internal fun applyFormatBarEvent(
    text: Editable,
    event: FormatBarEvent,
    selectionStart: Int,
    selectionEnd: Int,
): Boolean {
    when (event) {
        is FormatBarEvent.ToggleFormat -> RichTextFormatter.toggle(text, event.format, selectionStart, selectionEnd)
        is FormatBarEvent.SelectSize ->
            RichTextFormatter.select(text, TextFormat.Group.Size, event.format, selectionStart, selectionEnd)
        is FormatBarEvent.SelectFont ->
            RichTextFormatter.select(text, TextFormat.Group.Font, event.format, selectionStart, selectionEnd)
        is FormatBarEvent.SetColor ->
            ColorFormatter.setColor(text, event.kind, event.color, selectionStart, selectionEnd)
        is FormatBarEvent.ToggleBlock -> ParagraphFormatter.setBlock(text, event.block, selectionStart, selectionEnd)
        is FormatBarEvent.ChangeLevel ->
            ParagraphFormatter.changeLevel(text, event.delta, selectionStart, selectionEnd)
        is FormatBarEvent.SetAlignment ->
            ParagraphFormatter.setAlignment(text, event.alignment, selectionStart, selectionEnd)
        FormatBarEvent.ClearFormatting -> RichTextFormatter.clearFormatting(text, selectionStart, selectionEnd)
        FormatBarEvent.EditLink, is FormatBarEvent.InsertImage, FormatBarEvent.Undo, FormatBarEvent.Redo -> return false
    }

    return true
}
