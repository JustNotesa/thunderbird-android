package com.fsck.k9.ui.compose.richtext

import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange

private const val LINE_BREAK = '\n'
private const val LINE_BREAK_TEXT = "\n"
private const val SPACE = ' '

/**
 * Keeps the paragraph formatting of the message composer in step with what is typed: Enter continues a list or a quote
 * and ends it on an empty item, Backspace at the start of an item takes the block away first, and the markers `- `,
 * `* `, `1. ` and `> ` turn the line they are typed on into a list item or a quote.
 *
 * Every line an edit touches gets one set of paragraph spans again afterwards. The formatting of the lines is taken
 * before the edit, so that it doesn't depend on how Android moves the spans while the text changes: the lines an edit
 * creates get the formatting of the line it started on, the last of them that of the line it ended on.
 *
 * [isEnabled] tells whether the message is written as formatted text; none of this applies to the mode in which the
 * HTML code itself is edited.
 */
internal class ParagraphEditWatcher(private val isEnabled: () -> Boolean) : TextWatcher {
    private var isChanging = false
    private var edit: Edit? = null

    override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {
        edit = null
        if (isChanging || text !is Spanned || !isEnabled()) return

        val lineStarts = text.lineStartsTouched(start, start + count)
        edit = Edit(
            text = text,
            start = start,
            removed = text.substring(start, start + count),
            added = after,
            formats = lineStarts.map { lineStart -> text.paragraphRangeAt(lineStart) },
            startedOnEmptyLine = text.lineStartAt(start) == start && text.isEmptyLine(start),
        )
    }

    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(text: Editable?) {
        val edit = edit ?: return
        this.edit = null
        // Setting the text as a whole brings a new Editable with formatting of its own.
        if (text == null || text !== edit.text || isChanging) return

        isChanging = true
        try {
            handleEdit(text, edit)
            text.keepEndMarker()
        } finally {
            isChanging = false
        }
    }

    private fun handleEdit(text: Editable, edit: Edit) {
        when {
            edit.isTyped(text, LINE_BREAK) && edit.startedOnEmptyLine && edit.firstFormat.block != BlockType.Normal ->
                endBlock(text, edit)

            edit.isBackspaceOverLineBreak() && edit.formats.last().block != BlockType.Normal ->
                takeBlockAwayInsteadOfJoining(text, edit)

            else -> {
                restoreFormats(text, edit)
                if (edit.isTyped(text, SPACE)) text.applyLineStartShortcut(edit.start)
            }
        }
    }

    /**
     * Enter on an empty item ends the list or the quote instead of adding another empty item.
     */
    private fun endBlock(text: Editable, edit: Edit) {
        text.delete(edit.start, edit.start + 1)
        val lineEnd = text.lineEndAt(edit.start)
        text.setParagraphSpans(edit.start..lineEnd, edit.firstFormat.withoutBlock())
    }

    /**
     * Backspace at the start of an item puts the line break back and takes the block away from the item instead.
     */
    private fun takeBlockAwayInsteadOfJoining(text: Editable, edit: Edit) {
        text.insert(edit.start, LINE_BREAK_TEXT)
        val firstLineStart = text.lineStartAt(edit.start)
        text.setParagraphSpans(firstLineStart..edit.start, edit.firstFormat)
        val itemStart = edit.start + 1
        text.setParagraphSpans(itemStart..text.lineEndAt(itemStart), edit.formats.last().withoutBlock())
    }

    private fun restoreFormats(text: Editable, edit: Edit) {
        val lineStarts = text.lineStartsTouched(edit.start, edit.start + edit.added)
        lineStarts.forEachIndexed { index, lineStart ->
            val format = if (index == lineStarts.lastIndex && index > 0) edit.formats.last() else edit.firstFormat
            text.setParagraphSpans(lineStart..text.lineEndAt(lineStart), format)
        }
    }

    private class Edit(
        val text: CharSequence,
        val start: Int,
        val removed: String,
        val added: Int,
        val formats: List<ParagraphRange>,
        val startedOnEmptyLine: Boolean,
    ) {
        val firstFormat: ParagraphRange get() = formats.first()

        fun isTyped(text: CharSequence, character: Char): Boolean {
            return removed.isEmpty() && added == 1 && start < text.length && text[start] == character
        }

        fun isBackspaceOverLineBreak(): Boolean = removed == LINE_BREAK_TEXT && added == 0
    }
}

private fun ParagraphRange.withoutBlock(): ParagraphRange = copy(block = BlockType.Normal, level = 0)

/**
 * The starts of the lines `start..end` touches.
 */
private fun Spanned.lineStartsTouched(start: Int, end: Int): List<Int> {
    val starts = mutableListOf(lineStartAt(start))
    var lineEnd = lineEndAt(start)
    while (lineEnd < end.coerceAtMost(length) && lineEnd < length) {
        starts.add(lineEnd + 1)
        lineEnd = lineEndAt(lineEnd + 1)
    }

    return starts
}
