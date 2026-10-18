package com.fsck.k9.ui.compose.richtext

import android.text.Editable
import android.text.Selection
import android.text.Spanned
import net.thunderbird.feature.mail.message.composer.richtext.ColorRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.LinkRange
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange

/**
 * The zero-width space that gives the empty line at the end of the message text the character its paragraph formatting
 * needs. It never leaves the editor.
 */
internal const val END_MARKER = '\u200B'

private val END_MARKER_TEXT = END_MARKER.toString()

/**
 * Gives the empty last line of the text an [END_MARKER] when that line has paragraph formatting of its own, and takes
 * every marker out that this line doesn't need.
 *
 * The paragraph spans of an empty line at the end of the text can only cover nothing, which Android's layout counts as
 * belonging to the line before it: that line is indented twice and the empty one not at all, until the first character
 * is typed. The marker is a character, so the spans have something to cover.
 *
 * The formatting of the empty line is read from the zero-length spans it carries, before they are replaced.
 */
internal fun Editable.keepEndMarker() {
    val lineStart = lineStartAt(length)
    val paragraph = paragraphRangeAt(lineStart)
    if (isEmptyLine(lineStart) && !paragraph.hasDefaultFormatting()) {
        markEmptyLine(lineStart, paragraph)
    } else {
        removeEndMarkers()
    }
}

/**
 * Makes the line that starts at [lineStart] hold the marker and nothing else, with the paragraph spans of [paragraph]
 * over it.
 */
private fun Editable.markEmptyLine(lineStart: Int, paragraph: ParagraphRange) {
    if (!holdsOnlyEndMarker(lineStart)) {
        insert(lineStart, END_MARKER_TEXT)
        moveSelectionBeforeMarker(lineStart)
    }

    setParagraphSpans(lineStart..lineEndAt(lineStart), paragraph)
}

/**
 * Puts the cursor in front of the marker that was just inserted, where the insertion left it behind it.
 */
private fun Editable.moveSelectionBeforeMarker(markerPosition: Int) {
    if (Selection.getSelectionStart(this) == length) {
        Selection.setSelection(this, markerPosition, markerPosition)
    }
}

/**
 * Takes every marker out of the text that the last line doesn't need.
 */
private fun Editable.removeEndMarkers() {
    var position = length - 1
    while (position >= 0) {
        if (this[position] == END_MARKER) delete(position, position + 1)
        position--
    }
}

/**
 * Whether the line that starts at [lineStart] has no text of its own: it is empty or holds the marker alone.
 */
internal fun Spanned.isEmptyLine(lineStart: Int): Boolean {
    return lineEndAt(lineStart) == lineStart || holdsOnlyEndMarker(lineStart)
}

/**
 * Whether the line that starts at [lineStart] holds the marker and nothing else.
 */
private fun Spanned.holdsOnlyEndMarker(lineStart: Int): Boolean {
    return lineEndAt(lineStart) == lineStart + 1 && get(lineStart) == END_MARKER
}

/**
 * The end of a selection, moved in front of the marker at the end of the text: the marker is formatting of the empty
 * line, not text the user could select or turn into a link.
 */
internal fun Spanned.endWithoutEndMarker(end: Int): Int {
    return if (end == length && length > 0 && get(length - 1) == END_MARKER) end - 1 else end
}

/**
 * The text of the message without the markers. Everything behind a marker moves one character to the front, and the
 * formats that only covered a marker are left out; an empty paragraph stays, it is a line of the message.
 */
internal fun FormattedText.withoutEndMarkers(): FormattedText {
    val markers = text.indices.filter { index -> text[index] == END_MARKER }
    if (markers.isEmpty()) return this

    return copy(
        text = text.filterNot { character -> character == END_MARKER },
        ranges = ranges.mapNotNull { range ->
            val start = range.start.inFrontOf(markers)
            val end = range.end.inFrontOf(markers)
            if (start < end) FormattedRange(start, end, range.format) else null
        },
        colors = colors.mapNotNull { range ->
            val start = range.start.inFrontOf(markers)
            val end = range.end.inFrontOf(markers)
            if (start < end) ColorRange(start, end, range.kind, range.color) else null
        },
        links = links.mapNotNull { range ->
            val start = range.start.inFrontOf(markers)
            val end = range.end.inFrontOf(markers)
            if (start < end) LinkRange(start, end, range.url) else null
        },
        paragraphs = paragraphs.map { range ->
            range.copy(start = range.start.inFrontOf(markers), end = range.end.inFrontOf(markers))
        },
        images = images.map { image -> image.copy(position = image.position.inFrontOf(markers)) },
    )
}

/**
 * This position moved in front of the markers that stand before it, of which there is at most one per character.
 */
private fun Int.inFrontOf(markers: List<Int>): Int {
    return this - markers.count { marker -> marker < this }
}
