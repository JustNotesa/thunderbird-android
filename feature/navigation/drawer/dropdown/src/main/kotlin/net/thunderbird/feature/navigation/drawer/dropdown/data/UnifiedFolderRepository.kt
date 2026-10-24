package net.thunderbird.feature.navigation.drawer.dropdown.data

import app.k9mail.legacy.message.controller.MessageCountsProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.navigation.drawer.dropdown.domain.DomainContract
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedDisplayFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedDisplayFolderType
import net.thunderbird.feature.search.legacy.LocalMessageSearch
import net.thunderbird.feature.search.legacy.SearchAccount

internal class UnifiedFolderRepository(
    private val messageCountsProvider: MessageCountsProvider,
    private val accountManager: LegacyAccountManager,
) : DomainContract.UnifiedFolderRepository {

    override fun getUnifiedDisplayFolderFlow(unifiedFolderType: UnifiedDisplayFolderType): Flow<UnifiedDisplayFolder> {
        return messageCountsProvider.getMessageCountsFlow(createUnifiedFolderSearch(unifiedFolderType)).map {
            UnifiedDisplayFolder(
                folderId = unifiedFolderType.id,
                unifiedType = unifiedFolderType,
                unreadMessageCount = it.unread,
                starredMessageCount = it.starred,
            )
        }
    }

    override fun getUnifiedFolderTypesFlow(): Flow<List<UnifiedDisplayFolderType>> {
        return accountManager.observeAll().map { accounts ->
            UnifiedDisplayFolderType.entries.filter { unifiedFolderType ->
                unifiedFolderType == UnifiedDisplayFolderType.INBOX ||
                    accounts.any { account -> account.hasFolder(unifiedFolderType.folderType) }
            }
        }.distinctUntilChanged()
    }

    private fun createUnifiedFolderSearch(unifiedFolderType: UnifiedDisplayFolderType): LocalMessageSearch {
        return SearchAccount.createUnifiedSearch(
            folderType = unifiedFolderType.folderType,
            title = "",
            detail = "",
        ).relatedSearch
    }

    private fun LegacyAccount.hasFolder(folderType: FolderType): Boolean {
        return when (folderType) {
            FolderType.INBOX -> inboxFolderId != null
            FolderType.DRAFTS -> draftsFolderId != null
            FolderType.SENT -> sentFolderId != null
            FolderType.ARCHIVE -> archiveFolderId != null
            FolderType.SPAM -> spamFolderId != null
            FolderType.TRASH -> trashFolderId != null
            FolderType.OUTBOX, FolderType.REGULAR -> false
        }
    }
}
