package net.thunderbird.feature.account.settings.impl.ui.signature

import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

/**
 * The text of a text field the user formats directly, together with the selection the formatting applies to.
 *
 * A text field only reports its new text, so [onTextFieldChange] works out what was typed or deleted and moves the
 * formatted [ranges] along with it.
 *
 * @param typingFormats The formats of the text typed next, set when the user switched a format on or off without a
 * selection. `null` means the text typed next looks like the text before the cursor.
 */
internal data class RichTextEditorState(
    val text: String = "",
    val ranges: List<FormattedRange> = emptyList(),
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val typingFormats: Set<TextFormat>? = null,
) {
    private val selectionMin: Int
        get() = minOf(selectionStart, selectionEnd)

    private val selectionMax: Int
        get() = maxOf(selectionStart, selectionEnd)

    /**
     * The formats the whole selection has or, without a selection, the formats of the text typed next.
     */
    val activeFormats: Set<TextFormat>
        get() = if (selectionMin == selectionMax) {
            typingFormats ?: formatsBefore(selectionMin)
        } else {
            ranges.filter { it.start <= selectionMin && selectionMax <= it.end }.map { it.format }.toSet()
        }

    fun toggle(format: TextFormat): RichTextEditorState {
        val wasActive = format in activeFormats
        if (selectionMin == selectionMax) {
            return copy(typingFormats = if (wasActive) activeFormats - format else activeFormats + format)
        }

        val withoutFormat = ranges.withoutFormat(format, selectionMin, selectionMax)
        val newRanges = if (wasActive) {
            withoutFormat
        } else {
            withoutFormat + FormattedRange(selectionMin, selectionMax, format)
        }

        return copy(ranges = newRanges.normalized())
    }

    /**
     * The format of [group] the whole selection has or, without a selection, the text typed next gets. `null` means
     * the normal size or the default font.
     */
    fun activeFormat(group: TextFormat.Group): TextFormat? = activeFormats.firstOrNull { it.group == group }

    /**
     * Gives the selection or, without a selection, the text typed next the [format] of [group]. `null` removes the
     * formats of the group, which leaves the normal size or the default font.
     */
    fun select(group: TextFormat.Group, format: TextFormat?): RichTextEditorState {
        val groupFormats = TextFormat.of(group)
        if (selectionMin == selectionMax) {
            return copy(typingFormats = activeFormats - groupFormats.toSet() + listOfNotNull(format))
        }

        var newRanges = ranges
        for (groupFormat in groupFormats) {
            newRanges = newRanges.withoutFormat(groupFormat, selectionMin, selectionMax)
        }
        if (format != null) {
            newRanges = newRanges + FormattedRange(selectionMin, selectionMax, format)
        }

        return copy(ranges = newRanges.normalized())
    }

    fun onTextFieldChange(newText: String, newSelectionStart: Int, newSelectionEnd: Int): RichTextEditorState {
        if (newText == text) {
            val selectionMoved = newSelectionStart != selectionStart || newSelectionEnd != selectionEnd
            return copy(
                selectionStart = newSelectionStart,
                selectionEnd = newSelectionEnd,
                typingFormats = if (selectionMoved) null else typingFormats,
            )
        }

        val changeStart = text.commonPrefixWith(newText).length
        val unchangedEndLength = text.drop(changeStart).commonSuffixWith(newText.drop(changeStart)).length
        val removedEnd = text.length - unchangedEndLength
        val insertedEnd = newText.length - unchangedEndLength

        val insertedFormats = typingFormats ?: if (removedEnd > changeStart) {
            // Text was replaced, e.g. by autocorrect. The replacement looks like what it replaces.
            ranges.filter { it.start <= changeStart && changeStart < it.end }.map { it.format }.toSet()
        } else {
            formatsBefore(changeStart)
        }

        var newRanges = ranges.mapNotNull { it.movedBy(changeStart, removedEnd, insertedEnd) }
        for (format in TextFormat.entries) {
            newRanges = newRanges.withoutFormat(format, changeStart, insertedEnd)
            if (format in insertedFormats && changeStart < insertedEnd) {
                newRanges = newRanges + FormattedRange(changeStart, insertedEnd, format)
            }
        }

        return copy(
            text = newText,
            ranges = newRanges.normalized(),
            selectionStart = newSelectionStart,
            selectionEnd = newSelectionEnd,
        )
    }

    private fun formatsBefore(position: Int): Set<TextFormat> {
        return ranges.filter { it.start < position && position <= it.end }.map { it.format }.toSet()
    }

    /**
     * Where this range is after the text from [changeStart] to [removedEnd] was replaced by text that ends at
     * [insertedEnd]. The inserted text itself is left out; the caller decides which formats it gets.
     */
    private fun FormattedRange.movedBy(changeStart: Int, removedEnd: Int, insertedEnd: Int): FormattedRange? {
        val shift = insertedEnd - removedEnd
        val newStart = when {
            start < changeStart -> start
            start >= removedEnd -> start + shift
            else -> insertedEnd
        }
        val newEnd = when {
            end <= changeStart -> end
            end >= removedEnd -> end + shift
            else -> changeStart
        }

        return copy(start = newStart, end = newEnd).takeIf { newStart < newEnd }
    }
}

/**
 * Removes [format] between [start] and [end], keeping it on the text before and after.
 */
private fun List<FormattedRange>.withoutFormat(format: TextFormat, start: Int, end: Int): List<FormattedRange> {
    return flatMap { range ->
        if (range.format != format || range.end <= start || range.start >= end) {
            listOf(range)
        } else {
            listOfNotNull(
                range.copy(end = start).takeIf { it.start < it.end },
                range.copy(start = end).takeIf { it.start < it.end },
            )
        }
    }
}

/**
 * Merges ranges of the same format that touch or overlap, and puts the ranges in a stable order.
 */
private fun List<FormattedRange>.normalized(): List<FormattedRange> {
    val merged = mutableListOf<FormattedRange>()
    for (range in filter { it.start < it.end }.sortedWith(compareBy({ it.format }, { it.start }))) {
        val last = merged.lastOrNull()
        if (last != null && last.format == range.format && range.start <= last.end) {
            merged[merged.lastIndex] = last.copy(end = maxOf(last.end, range.end))
        } else {
            merged.add(range)
        }
    }

    return merged.sortedWith(compareBy({ it.start }, { it.format }))
}
