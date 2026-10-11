package net.thunderbird.feature.account.settings.impl.ui.signature

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * The formatting the signature editor can write as HTML for the user.
 */
internal enum class HtmlFormat {
    Bold,
    Italic,
    Link,
    LineBreak,
}

private const val LINK_OPENING_TAG_START = "<a href=\""
private const val LINK_URL_PLACEHOLDER = "https://"

/**
 * Applies [format] to the selected text, or inserts it at the cursor when nothing is selected. The cursor ends up
 * where the user continues typing: inside the new tags, in the address of a new link, or after a line break.
 */
internal fun TextFieldValue.applyHtmlFormat(format: HtmlFormat): TextFieldValue {
    return when (format) {
        HtmlFormat.Bold -> wrapSelection(openingTag = "<b>", closingTag = "</b>")
        HtmlFormat.Italic -> wrapSelection(openingTag = "<i>", closingTag = "</i>")
        HtmlFormat.Link -> insertLink()
        HtmlFormat.LineBreak -> replaceSelection("<br>")
    }
}

private fun TextFieldValue.wrapSelection(openingTag: String, closingTag: String): TextFieldValue {
    val selected = text.substring(selection.min, selection.max)
    val replacement = openingTag + selected + closingTag
    val newSelection = if (selected.isEmpty()) {
        TextRange(selection.min + openingTag.length)
    } else {
        TextRange(selection.min + replacement.length)
    }

    return copy(
        text = text.replaceRange(selection.min, selection.max, replacement),
        selection = newSelection,
    )
}

private fun TextFieldValue.insertLink(): TextFieldValue {
    val selected = text.substring(selection.min, selection.max)
    val replacement = "$LINK_OPENING_TAG_START$LINK_URL_PLACEHOLDER\">$selected</a>"
    val addressEnd = selection.min + LINK_OPENING_TAG_START.length + LINK_URL_PLACEHOLDER.length

    return copy(
        text = text.replaceRange(selection.min, selection.max, replacement),
        selection = TextRange(addressEnd),
    )
}

private fun TextFieldValue.replaceSelection(replacement: String): TextFieldValue {
    return copy(
        text = text.replaceRange(selection.min, selection.max, replacement),
        selection = TextRange(selection.min + replacement.length),
    )
}
