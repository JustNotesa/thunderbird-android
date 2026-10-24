@file:JvmName("LocalSearchExtensions")

package com.fsck.k9.search

import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.search.legacy.LocalMessageSearch
import net.thunderbird.feature.search.legacy.LocalMessageSearchType
import net.thunderbird.feature.search.legacy.SearchConditionTreeNode
import net.thunderbird.feature.search.legacy.UnifiedFolderSelection
import net.thunderbird.feature.search.legacy.api.MessageSearchField
import net.thunderbird.feature.search.legacy.api.SearchAttribute
import net.thunderbird.feature.search.legacy.api.SearchCondition

// No folder has this id, so an account that doesn't have the special folder contributes no messages.
private const val NO_FOLDER_ID = -1L

val LocalMessageSearch.isUnified: Boolean
    get() = type is LocalMessageSearchType.Unified

val LocalMessageSearch.isUnifiedInbox: Boolean
    get() = (type as? LocalMessageSearchType.Unified)?.folder == UnifiedFolderSelection.Special(FolderType.INBOX)

/**
 * The special folder type of a unified search other than the Unified Inbox, or `null`.
 *
 * The Unified Inbox is backed by the legacy `INTEGRATE` condition instead of a folder id, so it has no folder type to
 * resolve for a single account.
 */
val LocalMessageSearch.unifiedSpecialFolderType: FolderType?
    get() = ((type as? LocalMessageSearchType.Unified)?.folder as? UnifiedFolderSelection.Special)
        ?.folderType
        ?.takeIf { it != FolderType.INBOX }

val LocalMessageSearch.isNewMessages: Boolean
    get() = type == LocalMessageSearchType.NewMessages

val LocalMessageSearch.isSingleAccount: Boolean
    get() = accountIds.size == 1

val LocalMessageSearch.isSingleFolder: Boolean
    get() = isSingleAccount && folderIds.size == 1

@JvmName("getLegacyAccountsFromLocalSearch")
fun LocalMessageSearch.getLegacyAccounts(accountManager: LegacyAccountManager): List<LegacyAccount> {
    val accounts = accountManager.findAll()
    return if (searchAllAccounts()) {
        accounts
    } else {
        val searchAccountIds = accountIds.toSet()
        accounts.filter { it.id in searchAccountIds }
    }
}

fun LocalMessageSearch.getLegacyAccountUuids(accountManager: LegacyAccountManager): List<String> {
    return getLegacyAccounts(accountManager).map { it.id.toString() }
}

/**
 * The search conditions to use when evaluating this search for [account].
 *
 * A unified search is evaluated once per account and every account has its own folder ids. Therefore the folder of the
 * unified special folder is added to the conditions of [account]. Accounts that don't have that special folder
 * contribute no messages.
 *
 * The search itself isn't modified, so the same search can be used for every account.
 */
fun LocalMessageSearch.conditionsForAccount(account: LegacyAccount): SearchConditionTreeNode {
    val folderType = unifiedSpecialFolderType ?: return conditions

    val folderId = account.specialFolderId(folderType) ?: NO_FOLDER_ID
    val folderCondition = SearchCondition(MessageSearchField.FOLDER, SearchAttribute.EQUALS, folderId.toString())

    // A search without any conditions reports a match-all placeholder, which must not be part of the query.
    return if (leafSet.isEmpty()) {
        SearchConditionTreeNode.Builder(folderCondition).build()
    } else {
        SearchConditionTreeNode.Builder(folderCondition).and(conditions).build()
    }
}

private fun LegacyAccount.specialFolderId(folderType: FolderType): Long? = when (folderType) {
    FolderType.DRAFTS -> draftsFolderId
    FolderType.SENT -> sentFolderId
    FolderType.ARCHIVE -> archiveFolderId
    FolderType.SPAM -> spamFolderId
    FolderType.TRASH -> trashFolderId
    FolderType.INBOX, FolderType.OUTBOX, FolderType.REGULAR -> null
}
