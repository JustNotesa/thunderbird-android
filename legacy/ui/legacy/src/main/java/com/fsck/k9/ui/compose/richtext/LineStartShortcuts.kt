package com.fsck.k9.ui.compose.richtext

import android.text.Editable
import android.text.Spannable
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange

private const val BULLET_MARKER_1 = "-"
private const val BULLET_MARKER_2 = "*"
private const val QUOTE_MARKER = ">"
private const val NUMBER_MARKER_END = '.'
private const val MINIMUM_NUMBER_MARKER_LENGTH = 2

private val BULLET_MARKERS = setOf(BULLET_MARKER_1, BULLET_MARKER_2)

/**
 * Replaces the marker typed at the start of a normal line with the block it stands for. The space after the marker is
 * what triggers it, so that a marker in the middle of the text is left alone.
 */
internal fun Editable.applyLineStartShortcut(spacePosition: Int) {
    val lineStart = lineStartAt(spacePosition)
    val paragraph = paragraphRangeAt(lineStart)
    val block = if (paragraph.block == BlockType.Normal) blockForShortcut(markerBefore(spacePosition)) else null
    if (block == null) return

    delete(lineStart, spacePosition + 1)
    val lineEnd = lineEndAt(lineStart)
    setParagraphSpans(lineStart..lineEnd, ParagraphRange(lineStart, lineEnd, block, 0, paragraph.alignment))
}

/**
 * The block the [marker] asks for, `null` for text that isn't a marker of a list or a quote.
 */
internal fun blockForShortcut(marker: String): BlockType? {
    return when {
        marker in BULLET_MARKERS -> BlockType.BulletList
        marker == QUOTE_MARKER -> BlockType.Quote
        marker.isNumberMarker() -> BlockType.NumberedList
        else -> null
    }
}

private fun Spannable.markerBefore(position: Int): String {
    return toString().substring(lineStartAt(position), position)
}

private fun String.isNumberMarker(): Boolean {
    return length >= MINIMUM_NUMBER_MARKER_LENGTH &&
        endsWith(NUMBER_MARKER_END) &&
        dropLast(1).all { character -> character.isDigit() }
}
