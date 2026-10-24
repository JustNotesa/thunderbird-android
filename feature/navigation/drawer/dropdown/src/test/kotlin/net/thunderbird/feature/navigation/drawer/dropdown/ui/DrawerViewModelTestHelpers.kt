package net.thunderbird.feature.navigation.drawer.dropdown.ui

import kotlin.collections.emptyMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import net.thunderbird.core.preference.display.inboxSettings.UnifiedFoldersLayout
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.mail.folder.api.Folder
import net.thunderbird.feature.navigation.drawer.api.NavigationDrawerExternalContract.DrawerConfig
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.DisplayFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.DisplayTreeFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.MailDisplayAccount
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.MailDisplayFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedDisplayFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedDisplayFolderType
import net.thunderbird.feature.navigation.drawer.dropdown.ui.DrawerContract.State

internal fun createTestSubject(
    initialState: State = State(),
    drawerConfigFlow: Flow<DrawerConfig> = flow { emit(createDrawerConfig()) },
    displayAccountsFlow: Flow<List<MailDisplayAccount>> = flow { emit(emptyList()) },
    displayFoldersFlow: Flow<Map<AccountId, List<DisplayFolder>>> = flow { emit(emptyMap()) },
    displayTreeFolder: (List<DisplayFolder>) -> DisplayTreeFolder = {
        DisplayTreeFolder(
            displayFolder = null,
            displayName = null,
            totalUnreadCount = 0,
            totalStarredCount = 0,
            children = persistentListOf(),
        )
    },
    syncAccountFlow: Flow<Result<Unit>> = flow { emit(Result.success(Unit)) },
    syncAllAccounts: Flow<Result<Unit>> = flow { emit(Result.success(Unit)) },
    requestedLayouts: MutableList<UnifiedFoldersLayout> = mutableListOf(),
    requestedAccounts: MutableList<AccountId> = mutableListOf(),
): DrawerViewModel {
    return DrawerViewModel(
        initialState = initialState,
        getDrawerConfig = { drawerConfigFlow },
        getDisplayAccounts = { displayAccountsFlow },
        getDisplayFoldersForAccount = { accountId, unifiedFoldersLayout ->
            requestedLayouts.add(unifiedFoldersLayout)
            requestedAccounts.add(accountId)
            displayFoldersFlow.map { it[accountId] ?: emptyList() }
        },
        getDisplayTreeFolder = { folders, maxDepth ->
            displayTreeFolder(folders)
        },
        syncAccount = { syncAccountFlow },
        syncAllAccounts = { syncAllAccounts },
    )
}

internal fun createDisplayTreeFolder(folders: List<DisplayFolder>): DisplayTreeFolder {
    return DisplayTreeFolder(
        displayFolder = null,
        displayName = null,
        totalUnreadCount = 0,
        totalStarredCount = 0,
        children = folders.map { folder ->
            val accountName = (folder as? UnifiedDisplayFolder)?.children?.firstOrNull()?.accountName
            DisplayTreeFolder(
                displayFolder = folder,
                displayName = folder.folderId,
                totalUnreadCount = folder.unreadMessageCount,
                totalStarredCount = folder.starredMessageCount,
                children = persistentListOf(),
                accountLabel = accountName,
            )
        }.toImmutableList(),
    )
}

internal fun createDrawerConfig(
    showUnifiedInbox: Boolean = false,
    showStarredCount: Boolean = false,
    expandAllFolder: Boolean = false,
    unifiedFoldersLayout: UnifiedFoldersLayout = UnifiedFoldersLayout.Flat,
): DrawerConfig {
    return DrawerConfig(
        showUnifiedFolders = showUnifiedInbox,
        showStarredCount = showStarredCount,
        expandAllFolder = expandAllFolder,
        unifiedFoldersLayout = unifiedFoldersLayout,
    )
}

internal fun createDisplayAccount(
    id: AccountId = AccountIdFactory.create(),
    name: String = "name",
    email: String = "test@example.com",
    unreadCount: Int = 0,
    starredCount: Int = 0,
    hasAutoExpandFolder: Boolean = false,
): MailDisplayAccount {
    return MailDisplayAccount(
        id = id,
        name = name,
        email = email,
        color = 0,
        unreadMessageCount = unreadCount,
        starredMessageCount = starredCount,
        hasError = false,
        hasAutoExpandFolder = hasAutoExpandFolder,
    )
}

internal fun createDisplayAccountList(count: Int): List<MailDisplayAccount> {
    return List(count) {
        createDisplayAccount(
            id = AccountIdFactory.create(),
        )
    }
}

internal fun createDisplayFolder(
    accountId: AccountId = AccountIdFactory.create(),
    id: Long = 1234,
    name: String = "name",
    type: FolderType = FolderType.REGULAR,
    unreadCount: Int = 0,
    starredCount: Int = 0,
): MailDisplayFolder {
    val folder = Folder(
        id = id,
        name = name,
        type = type,
        isLocalOnly = false,
    )

    return MailDisplayFolder(
        accountId = accountId,
        folder = folder,
        isInTopGroup = false,
        unreadMessageCount = unreadCount,
        starredMessageCount = starredCount,
        pathDelimiter = "/",
    )
}

internal fun createDisplayFolderList(count: Int): List<MailDisplayFolder> {
    return List(count) { index ->
        createDisplayFolder(
            id = index.toLong() + 100,
        )
    }
}

internal fun createDisplayUnifiedFolder(
    id: String = "unified_inbox",
    unifiedType: UnifiedDisplayFolderType = UnifiedDisplayFolderType.INBOX,
    unreadCount: Int = 0,
    starredCount: Int = 0,
): UnifiedDisplayFolder {
    return UnifiedDisplayFolder(
        folderId = id,
        unifiedType = unifiedType,
        unreadMessageCount = unreadCount,
        starredMessageCount = starredCount,
    )
}
