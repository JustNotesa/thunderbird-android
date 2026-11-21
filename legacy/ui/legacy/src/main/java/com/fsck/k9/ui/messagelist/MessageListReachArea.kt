package com.fsck.k9.ui.messagelist

/**
 * The empty area above the first message that lets the user pull the list down within reach of the thumb.
 *
 * It is only used while the toolbar is at the bottom of the screen, which is the case for the single-pane layout in
 * one-handed mode.
 */
internal object MessageListReachArea {
    const val LIST_HEIGHT_FRACTION = 0.45f

    /**
     * @param listHeight The height of the message list in pixels.
     * @param bottomBarHeight The height of the toolbar at the bottom, or 0 when the toolbar is at the top.
     * @param hasMessages Whether the list contains messages. A list without messages is left as it is.
     */
    fun height(listHeight: Int, bottomBarHeight: Int, hasMessages: Boolean): Int {
        if (bottomBarHeight <= 0 || listHeight <= 0 || !hasMessages) return 0

        return (listHeight * LIST_HEIGHT_FRACTION).toInt()
    }
}
