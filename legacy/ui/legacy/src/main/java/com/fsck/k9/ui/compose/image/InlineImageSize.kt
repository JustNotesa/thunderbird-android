package com.fsck.k9.ui.compose.image

private const val SMALL_LONG_EDGE = 640
private const val MEDIUM_LONG_EDGE = 1280

/**
 * How large an image put into the text of a message is sent. [longEdge] is the most pixels the longer side may have;
 * `null` keeps the image as it is.
 */
enum class InlineImageSize(val longEdge: Int?) {
    Small(SMALL_LONG_EDGE),
    Medium(MEDIUM_LONG_EDGE),
    Original(null),
}
