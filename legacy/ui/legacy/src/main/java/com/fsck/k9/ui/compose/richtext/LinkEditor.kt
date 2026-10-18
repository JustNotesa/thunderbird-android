package com.fsck.k9.ui.compose.richtext

import android.widget.EditText

/**
 * Creates, changes and removes the link at the selection of [messageContent], asking for text and address first.
 */
internal class LinkEditor(private val messageContent: EditText) {

    /**
     * Opens the link dialog for the selection. [onChange] is called after the text field was changed.
     */
    fun edit(onChange: () -> Unit) {
        val text = messageContent.text
        val selectionStart = minOf(messageContent.selectionStart, messageContent.selectionEnd).coerceAtLeast(0)
        val selectionEnd = maxOf(messageContent.selectionStart, messageContent.selectionEnd).coerceAtLeast(0)
        val link = LinkFormatter.linkAt(text, selectionStart, selectionEnd)
        val start = link?.start ?: selectionStart
        val end = text.endWithoutEndMarker(link?.end ?: selectionEnd)
        val selectedText = text.substring(start, end)

        LinkDialog(messageContent.context).show(
            text = selectedText.ifEmpty { null },
            isTextEditable = start == end,
            url = link?.url,
            onConfirm = { linkText, url ->
                setLink(start, end, linkText, url)
                onChange()
            },
            onRemove = {
                LinkFormatter.removeLink(messageContent.text, start)
                onChange()
            },
        )
    }

    private fun setLink(start: Int, end: Int, linkText: String, url: String) {
        val text = messageContent.text
        var linkEnd = end
        // Without a selection the link text is typed in the dialog and inserted at the cursor.
        if (start == end) {
            text.insert(start, linkText)
            linkEnd = start + linkText.length
        }
        LinkFormatter.setLink(text, url, start, linkEnd)
        messageContent.setSelection(linkEnd)
    }
}
