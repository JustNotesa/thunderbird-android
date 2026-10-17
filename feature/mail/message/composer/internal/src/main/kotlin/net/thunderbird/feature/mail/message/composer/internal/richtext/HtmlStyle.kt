package net.thunderbird.feature.mail.message.composer.internal.richtext

import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.COLOR_BY_PROPERTY
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.FORMAT_BY_STYLE
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.RGB_PREFIX
import net.thunderbird.feature.mail.message.composer.internal.richtext.HtmlNames.STYLE_ATTRIBUTE
import net.thunderbird.feature.mail.message.composer.richtext.ColorKind
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

/**
 * The declarations of a style attribute the composer knows: the sizes and fonts it offers and the two colours.
 */
internal fun parseStyle(style: String): SpanStyle {
    if (style.isBlank()) return EMPTY_STYLE

    val entries = mutableListOf<StyleEntry>()
    var known = true

    for (declaration in style.split(';')) {
        if (declaration.isBlank()) continue

        val property = declaration.substringBefore(':').trim().lowercase()
        val value = declaration.substringAfter(':', "").trim().lowercase()
        val entry = FORMAT_BY_STYLE["$property:${value.filterNot { it.isWhitespace() }}"]?.let(::FormatStyle)
            ?: COLOR_BY_PROPERTY[property]
                ?.let { kind -> parseColor(value)?.let { color -> ColorStyle(kind = kind, color = color) } }

        if (entry == null) known = false else entries.add(entry)
    }

    return SpanStyle(entries = entries, known = known)
}

/**
 * A colour as `#rgb`, `#rrggbb` or `rgb(r, g, b)`, `null` for every other notation: a colour the composer can not
 * show is a colour it does not read.
 */
internal fun parseColor(value: String): Int? {
    val color = value.filterNot { it.isWhitespace() }
    val hashed = color.startsWith('#')
    val digits = if (hashed) color.drop(1) else ""

    return when {
        hashed && color.length == SHORT_COLOR_LENGTH && digits.all { it.isHexDigit() } ->
            buildString { for (digit in digits) append(digit).append(digit) }.toInt(HEX_RADIX)

        hashed && color.length == LONG_COLOR_LENGTH && digits.all { it.isHexDigit() } ->
            digits.toInt(HEX_RADIX)

        color.startsWith(RGB_PREFIX) && color.endsWith(')') ->
            parseRgbChannels(color.removePrefix(RGB_PREFIX).dropLast(1))

        else -> null
    }
}

private fun Char.isHexDigit(): Boolean {
    return this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}

private fun parseRgbChannels(channels: String): Int? {
    val values = channels.split(',').map { it.trim().toIntOrNull() }
    if (values.size != CHANNEL_COUNT || values.any { it == null || it !in 0..CHANNEL_MAXIMUM }) return null

    val (red, green, blue) = values.filterNotNull()
    return (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
}

/**
 * What a style attribute says the composer knows.
 */
internal sealed interface StyleEntry

internal data class FormatStyle(val format: TextFormat) : StyleEntry

internal data class ColorStyle(val kind: ColorKind, val color: Int) : StyleEntry

/**
 * The declarations of a style attribute, and whether every one of them is one the composer knows.
 */
internal data class SpanStyle(val entries: List<StyleEntry>, val known: Boolean)

// A colour stands as `#rgb`, `#rrggbb` or `rgb(r, g, b)`, and is written as `#rrggbb` without any alpha channel.
internal const val HEX_RADIX = 16
internal const val SHORT_COLOR_LENGTH = 4
internal const val LONG_COLOR_LENGTH = 7
internal const val COLOR_DIGITS = 6
internal const val RGB_MASK = 0xFFFFFF
private const val CHANNEL_COUNT = 3
private const val CHANNEL_MAXIMUM = 255
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8

private val EMPTY_STYLE = SpanStyle(entries = emptyList(), known = true)
