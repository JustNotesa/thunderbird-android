package net.thunderbird.feature.navigation.drawer.dropdown.domain.entity

import kotlinx.collections.immutable.ImmutableList

/**
 * @param accountLabel when set, the drawer shows this text instead of the folder name. It's used for the account
 * entries below a unified folder.
 */
internal data class DisplayTreeFolder(
    val displayFolder: DisplayFolder?,
    val displayName: String?,
    val totalUnreadCount: Int,
    val totalStarredCount: Int,
    val children: ImmutableList<DisplayTreeFolder>,
    val accountLabel: String? = null,
) {
    /**
     * Whether the folder with the given ID is located somewhere below this folder.
     */
    fun containsFolder(folderId: String?): Boolean {
        if (folderId == null) return false

        return children.any { it.displayFolder?.folderId == folderId || it.containsFolder(folderId) }
    }
}
