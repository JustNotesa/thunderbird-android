package net.thunderbird.feature.navigation.drawer.dropdown.domain.entity

import net.thunderbird.feature.mail.folder.FolderType

/**
 * Represents a unified folder in the drawer.
 *
 * The id is unique for each unified folder type. The entries are declared in the order they are shown in the drawer.
 */
internal enum class UnifiedDisplayFolderType(
    val id: String,
    val folderType: FolderType,
) {
    INBOX("unified_inbox", FolderType.INBOX),
    DRAFTS("unified_drafts", FolderType.DRAFTS),
    SENT("unified_sent", FolderType.SENT),
    ARCHIVE("unified_archive", FolderType.ARCHIVE),
    SPAM("unified_spam", FolderType.SPAM),
    TRASH("unified_trash", FolderType.TRASH),
    ;

    companion object {
        fun fromFolderType(folderType: FolderType): UnifiedDisplayFolderType? {
            return entries.firstOrNull { it.folderType == folderType }
        }
    }
}
