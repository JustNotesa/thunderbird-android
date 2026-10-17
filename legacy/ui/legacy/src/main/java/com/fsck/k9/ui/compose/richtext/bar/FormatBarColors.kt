package com.fsck.k9.ui.compose.richtext.bar

import com.fsck.k9.ui.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

private const val BLACK = 0x000000
private const val DARK_GREY = 0x666666
private const val RED = 0xd93025
private const val ORANGE = 0xe8710a
private const val YELLOW = 0xf9ab00
private const val GREEN = 0x188038
private const val TEAL = 0x12b5cb
private const val BLUE = 0x1a73e8
private const val PURPLE = 0x9334e6
private const val PINK = 0xe52592
private const val LIGHT_RED = 0xfce8e6
private const val LIGHT_ORANGE = 0xfef0e1
private const val LIGHT_YELLOW = 0xfde293
private const val LIGHT_GREEN = 0xe6f4ea
private const val LIGHT_TEAL = 0xe4f7fb
private const val LIGHT_BLUE = 0xe8f0fe
private const val LIGHT_PURPLE = 0xf3e8fd
private const val LIGHT_PINK = 0xfde7f3
private const val LIGHT_GREY = 0xe8eaed

/**
 * A colour the format bar offers, 0xRRGGBB, with the string resource of its name.
 */
internal data class NamedColor(val color: Int, val nameRes: Int)

/**
 * The colours the format bar offers for text and for highlighting. Highlights are the light variants of the text
 * colours, so that dark text stays readable on them.
 */
internal object FormatBarColors {
    val TEXT: ImmutableList<NamedColor> = persistentListOf(
        NamedColor(BLACK, R.string.message_compose_color_black),
        NamedColor(DARK_GREY, R.string.message_compose_color_dark_grey),
        NamedColor(RED, R.string.message_compose_color_red),
        NamedColor(ORANGE, R.string.message_compose_color_orange),
        NamedColor(YELLOW, R.string.message_compose_color_yellow),
        NamedColor(GREEN, R.string.message_compose_color_green),
        NamedColor(TEAL, R.string.message_compose_color_teal),
        NamedColor(BLUE, R.string.message_compose_color_blue),
        NamedColor(PURPLE, R.string.message_compose_color_purple),
        NamedColor(PINK, R.string.message_compose_color_pink),
    )

    val HIGHLIGHT: ImmutableList<NamedColor> = persistentListOf(
        NamedColor(LIGHT_RED, R.string.message_compose_color_light_red),
        NamedColor(LIGHT_ORANGE, R.string.message_compose_color_light_orange),
        NamedColor(LIGHT_YELLOW, R.string.message_compose_color_light_yellow),
        NamedColor(LIGHT_GREEN, R.string.message_compose_color_light_green),
        NamedColor(LIGHT_TEAL, R.string.message_compose_color_light_teal),
        NamedColor(LIGHT_BLUE, R.string.message_compose_color_light_blue),
        NamedColor(LIGHT_PURPLE, R.string.message_compose_color_light_purple),
        NamedColor(LIGHT_PINK, R.string.message_compose_color_light_pink),
        NamedColor(LIGHT_GREY, R.string.message_compose_color_light_grey),
    )
}
