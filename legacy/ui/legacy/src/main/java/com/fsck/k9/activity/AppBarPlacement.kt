package com.fsck.k9.activity

/**
 * The screens of the message home activity that differ in where the toolbar belongs.
 *
 * The search results and the expanded search field are part of [MESSAGE_LIST]: the search field lives in the toolbar.
 */
internal enum class AppBarScreen {
    MESSAGE_LIST,
    MESSAGE_VIEW,
    SPLIT_VIEW,
}

/**
 * Where the toolbar is displayed.
 */
internal enum class AppBarPlacement {
    /** At the top edge, above the content. */
    TOP,

    /** At the bottom edge, drawn over the lower end of the content so the content can scroll underneath. */
    BOTTOM_OVER_CONTENT,

    /** At the bottom edge, with the content ending above it. */
    BOTTOM_BELOW_CONTENT,
}

internal val AppBarPlacement.isAtBottom: Boolean
    get() = this != AppBarPlacement.TOP

/**
 * Decides where the toolbar goes for the given [screen]. With the setting off it stays at the top.
 *
 * The split view has one toolbar across the width of both panes. It is drawn over the lower end of the content, like
 * in the single pane message list. The message pane has to leave room for it.
 */
internal fun appBarPlacement(isBottomAppBarEnabled: Boolean, screen: AppBarScreen): AppBarPlacement {
    if (!isBottomAppBarEnabled) return AppBarPlacement.TOP

    return when (screen) {
        AppBarScreen.MESSAGE_LIST -> AppBarPlacement.BOTTOM_OVER_CONTENT
        AppBarScreen.MESSAGE_VIEW -> AppBarPlacement.BOTTOM_BELOW_CONTENT
        AppBarScreen.SPLIT_VIEW -> AppBarPlacement.BOTTOM_OVER_CONTENT
    }
}

/**
 * Whether the message header sits at the bottom, pinned above the toolbar, instead of scrolling with the message at
 * the top. That goes along with the toolbar at the bottom, and only in the message view of the single pane layout.
 */
internal fun shouldPinMessageHeaderAtBottom(isBottomAppBarEnabled: Boolean, screen: AppBarScreen): Boolean {
    return appBarPlacement(isBottomAppBarEnabled, screen) == AppBarPlacement.BOTTOM_BELOW_CONTENT
}

/**
 * The height the message list reserves for the toolbar at the bottom, also in the split view where the list is the
 * left pane.
 *
 * The list stays alive, but hidden, while a message is displayed. It keeps its reserved height then, as changing it
 * would throw the list's layout (the reach area above the first message and the space below the messages) out of
 * order, which showed as a blank gap above the first message after going back to the list.
 */
internal fun listBottomBarHeight(isBottomAppBarEnabled: Boolean, screen: AppBarScreen, appBarHeight: Int): Int {
    if (!isBottomAppBarEnabled) return 0

    return when (screen) {
        AppBarScreen.MESSAGE_LIST, AppBarScreen.MESSAGE_VIEW, AppBarScreen.SPLIT_VIEW -> appBarHeight
    }
}
