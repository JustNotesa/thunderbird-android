package com.fsck.k9.ui.compose.richtext

import android.text.Spanned

/**
 * The number of the numbered list item at [position], on nesting [level].
 *
 * The lines of the same level above it are counted; lines nested deeper are skipped, they are items of the item above.
 * The first line above that isn't an item of the same level ends the list, so an item is always numbered from one.
 */
internal fun Spanned.numberedItemNumber(position: Int, level: Int): Int {
    val content = toString()
    var number = 1
    var lineStart = content.lineStartBefore(position)
    var levelOfLine = numberedListLevelAt(lineStart)

    while (levelOfLine != null && levelOfLine >= level) {
        if (levelOfLine == level) {
            number++
        }
        lineStart = content.lineStartBefore(lineStart)
        levelOfLine = numberedListLevelAt(lineStart)
    }

    return number
}

/**
 * The nesting level of the numbered list the line at [lineStart] belongs to, `null` when it belongs to no list.
 */
private fun Spanned.numberedListLevelAt(lineStart: Int): Int? {
    if (lineStart < 0 || lineStart >= length) return null

    return getSpans(lineStart, lineStart + 1, NumberedListSpan::class.java).maxOfOrNull { it.level }
}

/**
 * The start of the line before the one that starts at [lineStart], -1 when there is none.
 */
private fun String.lineStartBefore(lineStart: Int): Int {
    if (lineStart <= 1) return lineStart - 1

    return lastIndexOf('\n', lineStart - 2) + 1
}
