package net.thunderbird.feature.navigation.drawer.dropdown.domain.entity

import kotlinx.collections.immutable.ImmutableList

internal data class DisplayTreeFolder(
    val displayFolder: DisplayFolder?,
    val displayName: String?,
    val totalUnreadCount: Int,
    val totalStarredCount: Int,
    val children: ImmutableList<DisplayTreeFolder>,
) {
    /**
     * Whether the folder with the given ID is located somewhere below this folder.
     */
    fun containsFolder(folderId: String?): Boolean {
        if (folderId == null) return false

        return children.any { it.displayFolder?.id == folderId || it.containsFolder(folderId) }
    }
}
