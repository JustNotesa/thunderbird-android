package com.fsck.k9.ui.compose.richtext.bar

import android.text.Spanned
import com.fsck.k9.ui.compose.richtext.ColorFormatter
import com.fsck.k9.ui.compose.richtext.LinkFormatter
import com.fsck.k9.ui.compose.richtext.ParagraphFormatter
import com.fsck.k9.ui.compose.richtext.RichTextFormatter
import net.thunderbird.feature.mail.message.composer.richtext.ColorKind
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

/**
 * The formatting the format bar shows for the selection `selectionStart..selectionEnd` of [text], on top of
 * [current], which keeps what doesn't depend on the text (e.g. which buttons are shown).
 */
internal fun formatBarStateOf(
    text: Spanned,
    selectionStart: Int,
    selectionEnd: Int,
    current: FormatBarState,
): FormatBarState {
    val paragraph = ParagraphFormatter.paragraphAt(text, selectionStart, selectionEnd)

    return current.copy(
        activeFormats = TextFormat.toggles
            .filter { format -> RichTextFormatter.isActive(text, format, selectionStart, selectionEnd) }
            .toSet(),
        size = RichTextFormatter.activeFormat(text, TextFormat.Group.Size, selectionStart, selectionEnd),
        font = RichTextFormatter.activeFormat(text, TextFormat.Group.Font, selectionStart, selectionEnd),
        textColor = ColorFormatter.activeColor(text, ColorKind.Text, selectionStart, selectionEnd),
        highlightColor = ColorFormatter.activeColor(text, ColorKind.Highlight, selectionStart, selectionEnd),
        block = paragraph.block,
        alignment = paragraph.alignment,
        isInLink = LinkFormatter.linkAt(text, selectionStart, selectionEnd) != null,
    )
}
