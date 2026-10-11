package com.fsck.k9.ui.managefolders

import net.thunderbird.feature.mail.folder.api.Folder

/**
 * A folder as a row of a list that displays subfolders below the folder they are in.
 *
 * @param parent The folder this folder is in, or `null` for a folder at the top level.
 */
internal data class FolderTreeRow(
    val folder: Folder,
    val parent: Folder?,
    val depth: Int,
    val hasSubfolders: Boolean,
    val isExpanded: Boolean,
)

internal object FolderTree {
    /**
     * Arrange folders so that subfolders follow the folder they are in. Only the subfolders of expanded folders are
     * part of the result.
     *
     * A folder is the subfolder of another one when its name starts with the name of that folder, followed by
     * [pathDelimiter]. Folders whose parent isn't in [folders] are displayed at the top level.
     */
    fun build(folders: List<Folder>, pathDelimiter: String, expandedFolderIds: Set<Long>): List<FolderTreeRow> {
        val foldersByName = folders.associateBy { it.name }
        val parents = folders.associateWith { folder -> findParent(folder, foldersByName, pathDelimiter) }
        val subfolders = folders.filter { parents[it] != null }.groupBy { parents.getValue(it) }

        val rows = mutableListOf<FolderTreeRow>()
        fun addRows(folder: Folder, depth: Int) {
            val folderSubfolders = subfolders[folder].orEmpty()
            val isExpanded = folder.id in expandedFolderIds
            rows.add(
                FolderTreeRow(
                    folder = folder,
                    parent = parents[folder],
                    depth = depth,
                    hasSubfolders = folderSubfolders.isNotEmpty(),
                    isExpanded = isExpanded,
                ),
            )
            if (isExpanded) {
                folderSubfolders.forEach { addRows(it, depth + 1) }
            }
        }
        folders.filter { parents[it] == null }.forEach { addRows(it, depth = 0) }

        return rows
    }

    private fun findParent(folder: Folder, foldersByName: Map<String, Folder>, pathDelimiter: String): Folder? {
        if (pathDelimiter.isEmpty() || folder.isLocalOnly) return null

        val parentName = folder.name.substringBeforeLast(pathDelimiter, missingDelimiterValue = "")
        return foldersByName[parentName]?.takeUnless { it.isLocalOnly }
    }
}
