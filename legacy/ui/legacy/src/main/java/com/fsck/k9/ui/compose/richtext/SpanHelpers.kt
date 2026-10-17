package com.fsck.k9.ui.compose.richtext

import android.text.Spanned
import android.text.style.CharacterStyle
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

private val SPAN_FLAGS_INCLUDING_START = setOf(Spanned.SPAN_INCLUSIVE_INCLUSIVE, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
private val SPAN_FLAGS_INCLUDING_END = setOf(Spanned.SPAN_INCLUSIVE_INCLUSIVE, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)

/**
 * The flags for a span over `start..end`. An empty span includes its position, so that the text typed at the cursor is
 * part of it; a span over a range excludes its end, so that text typed at the end is not.
 */
internal fun getSpanFlagsFor(start: Int, end: Int): Int {
    return if (start == end) Spanned.SPAN_INCLUSIVE_INCLUSIVE else Spanned.SPAN_EXCLUSIVE_INCLUSIVE
}

/**
 * The spans of [format] in this text, without the ones that only show what the keyboard is composing.
 */
internal fun Spanned.formatSpans(format: TextFormat): List<CharacterStyle> {
    return getSpans(0, length, CharacterStyle::class.java)
        .filter { span -> !isComposingSpan(span) }
        .filter { span -> format in span.textFormats() }
}

/**
 * Whether the text typed at [position] would be part of [span].
 */
internal fun Spanned.wouldFormatTextTypedAt(span: CharacterStyle, position: Int): Boolean {
    val spanStart = getSpanStart(span)
    val spanEnd = getSpanEnd(span)
    val pointFlags = getSpanFlags(span) and Spanned.SPAN_POINT_MARK_MASK
    val includesStart = pointFlags in SPAN_FLAGS_INCLUDING_START
    val includesEnd = pointFlags in SPAN_FLAGS_INCLUDING_END

    return when (position) {
        spanEnd -> includesEnd && (spanStart < spanEnd || includesStart)
        spanStart -> includesStart
        else -> position in (spanStart + 1) until spanEnd
    }
}

/**
 * Whether [span] is the span of the text the keyboard is composing, e.g. the underline of the word being typed.
 */
internal fun Spanned.isComposingSpan(span: Any): Boolean {
    return getSpanFlags(span) and Spanned.SPAN_COMPOSING != 0
}
