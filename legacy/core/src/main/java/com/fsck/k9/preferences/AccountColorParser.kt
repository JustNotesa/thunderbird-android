package com.fsck.k9.preferences

internal const val ACCOUNT_COLOR_KEY = "chipColor"

private const val HEX_COLOR_LENGTH = 7
private const val HEX_RADIX = 16
private const val OPAQUE_ALPHA = 0xFF000000.toInt()

/**
 * Parses the account color as written by the settings exporter (`chipColor`).
 *
 * The exporter writes the color as signed ARGB integer. The pretty form `#rrggbb` is accepted as well.
 *
 * @return The color as ARGB integer, or `null` if the value is missing or invalid.
 */
internal fun parseAccountColor(value: String?): Int? {
    val trimmed = value?.trim().orEmpty()

    return trimmed.toIntOrNull() ?: parsePrettyColor(trimmed)
}

private fun parsePrettyColor(value: String): Int? {
    if (value.length != HEX_COLOR_LENGTH || !value.startsWith("#")) return null

    return value.substring(1).toIntOrNull(HEX_RADIX)?.let { it or OPAQUE_ALPHA }
}
