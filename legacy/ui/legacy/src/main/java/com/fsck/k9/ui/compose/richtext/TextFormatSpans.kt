package com.fsck.k9.ui.compose.richtext

import android.graphics.Typeface
import android.text.style.CharacterStyle
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

// The sizes relative to the normal text, close to what mail clients show for small, large and x-large.
private const val SIZE_SMALL = 0.8f
private const val SIZE_LARGE = 1.25f
private const val SIZE_HUGE = 1.6f

private const val FONT_SERIF = "serif"
private const val FONT_MONOSPACE = "monospace"

private val FORMAT_BY_SIZE = mapOf(
    SIZE_SMALL to TextFormat.SizeSmall,
    SIZE_LARGE to TextFormat.SizeLarge,
    SIZE_HUGE to TextFormat.SizeHuge,
)
private val FORMAT_BY_FONT = mapOf(
    FONT_SERIF to TextFormat.FontSerif,
    FONT_MONOSPACE to TextFormat.FontMonospace,
)

/**
 * The Android span that shows text in this format.
 */
internal fun TextFormat.createSpan(): CharacterStyle {
    return when (this) {
        TextFormat.Bold -> StyleSpan(Typeface.BOLD)
        TextFormat.Italic -> StyleSpan(Typeface.ITALIC)
        TextFormat.Underline -> UnderlineSpan()
        TextFormat.Strikethrough -> StrikethroughSpan()
        TextFormat.SizeSmall -> RelativeSizeSpan(SIZE_SMALL)
        TextFormat.SizeLarge -> RelativeSizeSpan(SIZE_LARGE)
        TextFormat.SizeHuge -> RelativeSizeSpan(SIZE_HUGE)
        TextFormat.FontSerif -> TypefaceSpan(FONT_SERIF)
        TextFormat.FontMonospace -> TypefaceSpan(FONT_MONOSPACE)
    }
}

/**
 * The formats this span shows. Empty for spans that are not formatting of the composer, one for most spans, and two
 * for a bold italic span.
 */
internal fun CharacterStyle.textFormats(): Set<TextFormat> {
    return when (this) {
        is StyleSpan -> when (style) {
            Typeface.BOLD -> setOf(TextFormat.Bold)
            Typeface.ITALIC -> setOf(TextFormat.Italic)
            Typeface.BOLD_ITALIC -> setOf(TextFormat.Bold, TextFormat.Italic)
            else -> emptySet()
        }

        is UnderlineSpan -> setOf(TextFormat.Underline)

        is StrikethroughSpan -> setOf(TextFormat.Strikethrough)

        is RelativeSizeSpan -> setOfNotNull(FORMAT_BY_SIZE[sizeChange])

        is TypefaceSpan -> setOfNotNull(FORMAT_BY_FONT[family])

        else -> emptySet()
    }
}
