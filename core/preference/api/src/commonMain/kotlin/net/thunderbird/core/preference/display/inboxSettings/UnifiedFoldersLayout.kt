package net.thunderbird.core.preference.display.inboxSettings

/** How the unified folders are shown in the navigation drawer. */
enum class UnifiedFoldersLayout {

    /**
     * The default setting.
     *  - The unified folders of the unified account are shown as a plain list
     */
    Flat,

    /**
     * Expandable unified folders
     *  - Each unified folder expands to the matching folder of every account
     */
    Tree,

    /**
     * Pinned unified folders
     *  - Like [Flat], and additionally shown above the folders of every account
     */
    Pinned,
}
