package com.fsck.k9.ui.messageview

import java.util.concurrent.ConcurrentHashMap
import net.thunderbird.feature.account.AccountId

/**
 * Remembers the folders last chosen when moving or copying messages, so the folder chooser can scroll to the last one
 * and offer the most recent ones.
 *
 * Kept in memory only, so it resets when the app restarts.
 */
internal class LastSelectedFolderStore {
    private val folderIds = ConcurrentHashMap<AccountId, List<Long>>()

    fun get(accountId: AccountId): Long? = folderIds[accountId]?.firstOrNull()

    /** The most recently chosen folders of the account, the latest first. */
    fun getRecent(accountId: AccountId): List<Long> = folderIds[accountId].orEmpty()

    fun set(accountId: AccountId, folderId: Long) {
        folderIds.compute(accountId) { _, recent ->
            (listOf(folderId) + recent.orEmpty().filterNot { it == folderId }).take(MAX_RECENT_FOLDERS)
        }
    }

    private companion object {
        const val MAX_RECENT_FOLDERS = 5
    }
}
