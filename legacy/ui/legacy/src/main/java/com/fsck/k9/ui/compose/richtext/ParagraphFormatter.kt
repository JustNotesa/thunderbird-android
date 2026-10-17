package com.fsck.k9.ui.compose.richtext

import android.text.Editable
import android.text.Spannable
import android.text.Spanned
import net.thunderbird.feature.mail.message.composer.richtext.Alignment
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange

/**
 * Applies the paragraph formatting of the message composer - block type, indentation and alignment - to the lines the
 * selection touches, and reads it back.
 */
internal object ParagraphFormatter {

    /**
     * Gives the lines the selection touches the [block], or takes it away again when all of them have it already.
     */
    fun setBlock(text: Editable, block: BlockType, selectionStart: Int, selectionEnd: Int) {
        val lines = text.lineRanges(selectionStart, selectionEnd)
        val turnOff = lines.isNotEmpty() && lines.all { line -> text.paragraphRangeAt(line.first).block == block }

        for (line in lines) {
            val paragraph = text.paragraphRangeAt(line.first)
            val newBlock = if (turnOff) BlockType.Normal else block

            text.setBlockOf(line, newBlock, paragraph)
        }
    }

    /**
     * Indents the lines the selection touches by [delta] levels, within the levels the model knows.
     *
     * Outdenting an item of the outermost level ends the list it is the only item of.
     */
    fun changeLevel(text: Editable, delta: Int, selectionStart: Int, selectionEnd: Int) {
        for (line in text.lineRanges(selectionStart, selectionEnd)) {
            val paragraph = text.paragraphRangeAt(line.first)
            val level = (paragraph.level + delta).coerceIn(0, ParagraphRange.MAX_LEVEL)
            val block = if (paragraph.level == 0 && level == 0 && paragraph.block.isList()) {
                BlockType.Normal
            } else {
                paragraph.block
            }

            text.setParagraphSpans(line, paragraph.copy(block = block, level = level))
        }
    }

    /**
     * Aligns the lines the selection touches, `Alignment.Start` to let them run from the left again.
     */
    fun setAlignment(text: Editable, alignment: Alignment, selectionStart: Int, selectionEnd: Int) {
        for (line in text.lineRanges(selectionStart, selectionEnd)) {
            val paragraph = text.paragraphRangeAt(line.first)

            text.setParagraphSpans(line, paragraph.copy(alignment = alignment))
        }
    }

    /**
     * The formatting of the first line the selection touches, the default one when the text has no lines.
     */
    fun paragraphAt(text: Spanned, selectionStart: Int, selectionEnd: Int): ParagraphRange {
        val line = text.lineRanges(selectionStart, selectionEnd).firstOrNull()
        val at = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        if (line == null) return ParagraphRange(at, at)

        return text.paragraphRangeAt(line.first)
    }

    /**
     * The paragraph formatting of [text] for the model of the composer: the lines that share their formatting as one
     * range, without the lines that have the default formatting.
     */
    fun paragraphs(text: Spanned): List<ParagraphRange> {
        val merged = mutableListOf<ParagraphRange>()
        var lineStart = 0
        while (lineStart <= text.length) {
            val paragraph = text.paragraphRangeAt(lineStart)
            val last = merged.lastOrNull()
            if (last != null && last.hasSameFormattingAs(paragraph)) {
                merged[merged.lastIndex] = last.copy(end = paragraph.end)
            } else {
                merged.add(paragraph)
            }
            lineStart = paragraph.end + 1
        }

        return merged.filterNot { it.hasDefaultFormatting() }
    }

    /**
     * Puts the paragraph formatting of [paragraphs] onto the lines they cover. A range of the model reaches over as
     * many lines as it covers, and every one of them gets the formatting.
     */
    fun applyParagraphs(text: Spannable, paragraphs: List<ParagraphRange>) {
        for (paragraph in paragraphs) {
            for (line in text.lineRanges(paragraph.start, paragraph.end)) {
                text.setParagraphSpans(line, paragraph)
            }
        }
    }

    /**
     * Takes the block away from the line when [cursor] stands at its start, for Backspace where there is no line break
     * before the cursor to delete. Returns whether it did.
     */
    fun removeBlockAtLineStart(text: Editable, cursor: Int): Boolean {
        val lineStart = text.lineStartAt(cursor)
        val paragraph = text.paragraphRangeAt(lineStart)
        if (cursor != lineStart || paragraph.block == BlockType.Normal) return false

        text.setBlockOf(lineStart..text.lineEndAt(lineStart), BlockType.Normal, paragraph)
        return true
    }

    private fun Spannable.setBlockOf(line: IntRange, block: BlockType, paragraph: ParagraphRange) {
        val level = if (block == BlockType.Normal) 0 else paragraph.level.coerceIn(0, ParagraphRange.MAX_LEVEL)

        setParagraphSpans(line, paragraph.copy(block = block, level = level))
    }
}

private fun BlockType.isList(): Boolean {
    return this == BlockType.BulletList || this == BlockType.NumberedList
}

internal fun ParagraphRange.hasSameFormattingAs(other: ParagraphRange): Boolean {
    return block == other.block && level == other.level && alignment == other.alignment
}

internal fun ParagraphRange.hasDefaultFormatting(): Boolean {
    return block == BlockType.Normal && level == 0 && alignment == Alignment.Start
}
