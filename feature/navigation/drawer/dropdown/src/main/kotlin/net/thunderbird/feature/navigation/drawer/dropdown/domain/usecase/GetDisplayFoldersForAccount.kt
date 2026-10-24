package net.thunderbird.feature.navigation.drawer.dropdown.domain.usecase

import app.k9mail.legacy.ui.folder.DisplayFolderRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.core.preference.display.inboxSettings.UnifiedFoldersLayout
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.UnifiedAccountId
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.navigation.drawer.dropdown.domain.DomainContract.UnifiedFolderRepository
import net.thunderbird.feature.navigation.drawer.dropdown.domain.DomainContract.UseCase
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.DisplayFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.MailDisplayFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedAccountFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedDisplayFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedDisplayFolderType
import app.k9mail.legacy.ui.folder.DisplayFolder as LegacyDisplayFolder

internal class GetDisplayFoldersForAccount(
    private val displayFolderRepository: DisplayFolderRepository,
    private val unifiedFolderRepository: UnifiedFolderRepository,
    private val accountManager: LegacyAccountManager,
) : UseCase.GetDisplayFoldersForAccount {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun invoke(
        accountId: AccountId,
        unifiedFoldersLayout: UnifiedFoldersLayout,
    ): Flow<List<DisplayFolder>> {
        if (accountId == UnifiedAccountId) {
            return getUnifiedFoldersFlow(unifiedFoldersLayout)
        } else {
            return displayFolderRepository.getDisplayFoldersFlow(accountId)
                // The account might have been removed in the meantime. In that case there are no folders to display.
                .catch { emit(emptyList()) }
                .map { displayFolders ->
                    displayFolders.map { displayFolder ->
                        toMailDisplayFolder(accountId, displayFolder)
                    }
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun getUnifiedFoldersFlow(unifiedFoldersLayout: UnifiedFoldersLayout): Flow<List<UnifiedDisplayFolder>> {
        return unifiedFolderRepository.getUnifiedFolderTypesFlow().flatMapLatest { unifiedFolderTypes ->
            val unifiedFolderFlows = unifiedFolderTypes.map { unifiedFolderType ->
                if (unifiedFoldersLayout == UnifiedFoldersLayout.Tree) {
                    getUnifiedFolderWithChildrenFlow(unifiedFolderType)
                } else {
                    unifiedFolderRepository.getUnifiedDisplayFolderFlow(unifiedFolderType)
                }
            }

            combineFlows(unifiedFolderFlows)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun getUnifiedFolderWithChildrenFlow(
        unifiedFolderType: UnifiedDisplayFolderType,
    ): Flow<UnifiedDisplayFolder> {
        return accountManager.observeAll().flatMapLatest { accounts ->
            val accountFolderFlows = accounts.map { account ->
                getAccountFoldersFlow(account, unifiedFolderType.folderType)
            }

            combine(
                unifiedFolderRepository.getUnifiedDisplayFolderFlow(unifiedFolderType),
                combineFlows(accountFolderFlows),
            ) { unifiedFolder, accountFolders ->
                unifiedFolder.copy(
                    children = accountFolders.flatten().toImmutableList(),
                )
            }
        }
    }

    private fun getAccountFoldersFlow(
        account: LegacyAccount,
        folderType: FolderType,
    ): Flow<List<UnifiedAccountFolder>> {
        return displayFolderRepository.getDisplayFoldersFlow(account.id).map { displayFolders ->
            displayFolders
                .filter { displayFolder -> displayFolder.folder.type == folderType }
                .map { displayFolder ->
                    UnifiedAccountFolder(
                        accountName = account.profile.name,
                        folder = toMailDisplayFolder(account.id, displayFolder),
                    )
                }
        }
    }

    private fun toMailDisplayFolder(accountId: AccountId, displayFolder: LegacyDisplayFolder): MailDisplayFolder {
        return MailDisplayFolder(
            accountId = accountId,
            folder = displayFolder.folder,
            isInTopGroup = displayFolder.isInTopGroup,
            unreadMessageCount = displayFolder.unreadMessageCount,
            starredMessageCount = displayFolder.starredMessageCount,
            pathDelimiter = displayFolder.pathDelimiter,
        )
    }

    /** Combines the [flows] into a single flow of their latest values. An empty list of flows emits an empty list. */
    private inline fun <reified T> combineFlows(flows: List<Flow<T>>): Flow<List<T>> {
        return if (flows.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(flows) { values -> values.toList() }
        }
    }
}
