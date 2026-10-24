package com.fsck.k9.ui.messagelist

import androidx.annotation.StringRes
import com.fsck.k9.ui.R
import net.thunderbird.feature.mail.folder.FolderType

/**
 * The title of the unified folder containing the messages of the special folder [folderType] of every account.
 *
 * @throws IllegalArgumentException if [folderType] isn't a folder that can be unified.
 */
@StringRes
internal fun unifiedFolderTitle(folderType: FolderType): Int = when (folderType) {
    FolderType.INBOX -> R.string.integrated_inbox_title
    FolderType.DRAFTS -> R.string.unified_drafts_title
    FolderType.SENT -> R.string.unified_sent_title
    FolderType.ARCHIVE -> R.string.unified_archive_title
    FolderType.SPAM -> R.string.unified_spam_title
    FolderType.TRASH -> R.string.unified_trash_title
    FolderType.OUTBOX, FolderType.REGULAR -> throw IllegalArgumentException(
        "Folder type can't be unified: $folderType",
    )
}
