package net.thunderbird.feature.navigation.drawer.dropdown.ui

import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.thunderbird.core.preference.display.inboxSettings.UnifiedFoldersLayout
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.account.UnifiedAccountId
import net.thunderbird.feature.navigation.drawer.dropdown.domain.DomainContract.UseCase
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.DisplayAccount
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.DisplayFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.DisplayTreeFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.MailDisplayAccount
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.MailDisplayFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedDisplayFolder
import net.thunderbird.feature.navigation.drawer.dropdown.ui.DrawerContract.Effect
import net.thunderbird.feature.navigation.drawer.dropdown.ui.DrawerContract.Event
import net.thunderbird.feature.navigation.drawer.dropdown.ui.DrawerContract.State
import net.thunderbird.feature.navigation.drawer.dropdown.ui.DrawerContract.ViewModel

/**
 * Delay before closing the drawer to avoid the drawer being closed immediately and give time
 * for the ripple effect to finish.
 */
private const val DRAWER_CLOSE_DELAY = 250L
private const val ACCOUNT_CLOSE_DELAY = 150L

@Suppress("MagicNumber", "TooManyFunctions")
internal class DrawerViewModel(
    private val getDrawerConfig: UseCase.GetDrawerConfig,
    private val getDisplayAccounts: UseCase.GetDisplayAccounts,
    private val getDisplayFoldersForAccount: UseCase.GetDisplayFoldersForAccount,
    private val getDisplayTreeFolder: UseCase.GetDisplayTreeFolder,
    private val syncAccount: UseCase.SyncAccount,
    private val syncAllAccounts: UseCase.SyncAllAccounts,
    private val maxNestingLevel: Int = 2,
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(
    initialState = initialState,
),
    ViewModel {

    private var hasLoadedAccounts = false

    init {
        viewModelScope.launch {
            getDrawerConfig().collectLatest { config ->
                updateState {
                    it.copy(config = config)
                }
            }
        }

        viewModelScope.launch {
            loadAccounts()
        }

        viewModelScope.launch {
            loadFolders()
        }

        viewModelScope.launch {
            loadPinnedFolders()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun loadAccounts() {
        state.map { it.config.showUnifiedFolders }
            .distinctUntilChanged()
            .flatMapLatest { showUnifiedFolders ->
                getDisplayAccounts(showUnifiedFolders)
            }.collectLatest { accounts ->
                updateAccounts(accounts)
            }
    }

    private fun updateAccounts(accounts: List<DisplayAccount>) {
        hasLoadedAccounts = true

        val selectedAccount = accounts.find { it.id == state.value.selectedAccountId }
            ?: accounts.firstOrNull()

        updateState {
            it.copy(
                accounts = accounts.toImmutableList(),
                selectedAccountId = selectedAccount?.id,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun loadFolders() {
        state.map { currentState ->
            currentState.selectedAccountId?.let { accountId ->
                accountId to currentState.config.unifiedFoldersLayout
            }
        }.filterNotNull()
            .distinctUntilChanged()
            .flatMapLatest { (accountId, unifiedFoldersLayout) ->
                getDisplayFoldersForAccount(accountId, unifiedFoldersLayout)
            }.collect { folders ->
                updateFolders(folders, getDisplayTreeFolder(folders, maxNestingLevel))
            }
    }

    private fun updateFolders(displayFolders: List<DisplayFolder>, rootFolder: DisplayTreeFolder) {
        // A pinned unified folder is selected, but it isn't part of the account's folders.
        var selectedFolder = state.value.pinnedFolders
            .firstOrNull { it.displayFolder?.folderId == state.value.selectedFolderId }
            ?.displayFolder

        // First try to find the folder in the flat list
        if (selectedFolder == null) {
            selectedFolder = displayFolders.find {
                it.folderId == state.value.selectedFolderId
            }
        }

        // If not found, try to find it in the tree hierarchy
        if (selectedFolder == null) {
            selectedFolder = findFolderById(rootFolder, state.value.selectedFolderId)
        }

        // If still not found, default to the first folder
        if (selectedFolder == null) {
            selectedFolder = displayFolders.firstOrNull() ?: rootFolder.children.firstOrNull()?.displayFolder
        }

        updateState {
            it.copy(
                rootFolder = rootFolder,
                folders = displayFolders.toImmutableList(),
                selectedFolderId = selectedFolder?.folderId,
                selectedFolder = selectedFolder,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun loadPinnedFolders() {
        state.map { shouldShowPinnedFolders(it) }
            .distinctUntilChanged()
            .flatMapLatest { showPinnedFolders ->
                if (showPinnedFolders) {
                    getDisplayFoldersForAccount(UnifiedAccountId, UnifiedFoldersLayout.Flat)
                        .map { getDisplayTreeFolder(it, maxNestingLevel).children }
                } else {
                    flowOf(emptyList<DisplayTreeFolder>())
                }
            }.collect { pinnedFolders ->
                updatePinnedFolders(pinnedFolders)
            }
    }

    private fun shouldShowPinnedFolders(currentState: State): Boolean {
        val isPinnedLayout = currentState.config.showUnifiedFolders &&
            currentState.config.unifiedFoldersLayout == UnifiedFoldersLayout.Pinned
        val accountId = currentState.selectedAccountId

        return isPinnedLayout && accountId != null && accountId != UnifiedAccountId
    }

    private fun updatePinnedFolders(pinnedFolders: List<DisplayTreeFolder>) {
        updateState {
            it.copy(pinnedFolders = pinnedFolders.toImmutableList())
        }
    }

    /**
     * Recursively searches for a folder with the given ID in the DisplayTreeFolder hierarchy.
     */
    private fun findFolderById(treeFolder: DisplayTreeFolder, folderId: String?): DisplayFolder? {
        if (folderId == null) return null

        return if (treeFolder.displayFolder?.folderId == folderId) {
            treeFolder.displayFolder
        } else {
            // Recursively search in children
            var folder: DisplayFolder? = null
            for (child in treeFolder.children) {
                val found = findFolderById(child, folderId)
                if (found != null) {
                    folder = found
                    break
                }
            }

            folder
        }
    }

    override fun event(event: Event) {
        when (event) {
            is Event.SelectAccount -> selectAccount(event.accountId)

            is Event.SelectFolder -> selectFolder(event.folderId)

            is Event.OnAccountClick -> showAccountFolders(event.account)

            is Event.OnAccountAvatarClick -> openAccount(
                event.account,
                closeDrawer = true,
            )

            is Event.OnFolderClick -> openFolder(event.folder)

            is Event.OnAccountViewClick -> {
                openAccount(
                    state.value.accounts.nextOrFirst(event.account),
                )
            }

            Event.OnAccountSelectorClick -> {
                viewModelScope.launch {
                    delay(ACCOUNT_CLOSE_DELAY)
                    updateState {
                        it.copy(
                            showAccountSelection = it.showAccountSelection.not(),
                            showAccountSelector = it.showAccountSelector.not(),
                        )
                    }
                }
            }

            Event.OnSettingsClick -> emitEffect(Effect.OpenSettings)

            Event.OnSyncAccount -> onSyncAccount()

            Event.OnSyncAllAccounts -> onSyncAllAccounts()

            Event.OnAddAccountClick -> emitEffect(Effect.OpenAddAccount)
        }
    }

    private fun selectAccount(accountId: AccountId?) {
        val selectedAccountId = resolveSelectedAccountId(accountId)

        if (selectedAccountId != state.value.selectedAccountId && !keepCurrentAccount(accountId)) {
            viewModelScope.launch {
                updateState {
                    it.copy(
                        selectedAccountId = selectedAccountId,
                    )
                }
                delay(ACCOUNT_CLOSE_DELAY)
                updateState {
                    it.copy(
                        showAccountSelection = false,
                    )
                }
            }
        }
    }

    /**
     * A tap on an account in the account list shows the folders of that account. The drawer stays open and the message
     * list isn't changed until the user selects a folder.
     */
    private fun showAccountFolders(account: DisplayAccount) {
        viewModelScope.launch {
            updateState {
                it.copy(
                    selectedAccountId = account.id,
                )
            }
            delay(ACCOUNT_CLOSE_DELAY)
            updateState {
                it.copy(
                    showAccountSelection = false,
                )
            }
        }
    }

    /**
     * After a pinned unified folder was opened, the activity re-selects the unified account. The user should stay in
     * the account they were browsing, so the current account is kept.
     */
    private fun keepCurrentAccount(accountId: AccountId?): Boolean {
        val currentState = state.value
        val isPinnedLayout = currentState.config.unifiedFoldersLayout == UnifiedFoldersLayout.Pinned
        val isSwitchToUnifiedAccount = accountId == UnifiedAccountId &&
            currentState.selectedAccountId != UnifiedAccountId

        return isPinnedLayout && isSwitchToUnifiedAccount &&
            currentState.pinnedFolders.any { it.displayFolder?.folderId == currentState.selectedFolderId }
    }

    /**
     * Returns the id of the account that should be selected. Accounts that are not present anymore, e.g. because
     * they were deleted, are replaced by the first available account. If there are no accounts at all, no account
     * is selected.
     */
    private fun resolveSelectedAccountId(accountId: AccountId?): AccountId? {
        // The unified account isn't a real account, so it's never replaced by another account.
        if (!hasLoadedAccounts || accountId == UnifiedAccountId) return accountId

        val accounts = state.value.accounts
        return accountId?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id
    }

    private fun selectFolder(folderId: String?) {
        // Find the folder with the given ID
        val folder = folderId?.let {
            // A pinned unified folder isn't part of the account's folders.
            state.value.pinnedFolders.find { it.displayFolder?.folderId == folderId }?.displayFolder
                ?: state.value.folders.find { it.folderId == folderId }
                // If not found, try to find it in the tree hierarchy
                ?: findFolderById(state.value.rootFolder, folderId)
        }

        updateState {
            it.copy(
                selectedFolderId = folderId,
                selectedFolder = folder,
            )
        }
    }

    /**
     * Opens the account. When [closeDrawer] is set, the drawer is always closed, no matter whether the account has an
     * auto expand folder. This is used when the account's avatar is clicked, as the user expects to end up in the
     * inbox of the account.
     */
    private fun openAccount(account: DisplayAccount?, closeDrawer: Boolean = false) {
        if (account != null) {
            // The user explicitly selected the unified account. The activity selects it again when the search is
            // displayed. Selecting it here makes sure that this doesn't get reverted.
            if (account.id == UnifiedAccountId) {
                updateState {
                    it.copy(selectedAccountId = UnifiedAccountId)
                }
            }

            emitEffect(Effect.OpenAccount(account.id))
            viewModelScope.launch {
                delay(DRAWER_CLOSE_DELAY)
                updateState {
                    it.copy(
                        showAccountSelection = false,
                    )
                }
                if (closeDrawer || (account is MailDisplayAccount && account.hasAutoExpandFolder)) {
                    emitEffect(Effect.CloseDrawer)
                }
            }
        }
    }

    private fun ImmutableList<DisplayAccount>.nextOrFirst(account: DisplayAccount): DisplayAccount? {
        val index = indexOf(account)
        return if (index == -1) {
            null
        } else if (index == size - 1) {
            get(0)
        } else {
            get(index + 1)
        }
    }

    private fun openFolder(folder: DisplayFolder) {
        // Update the selected type ID in the state
        selectFolder(folder.folderId)

        if (folder is MailDisplayFolder) {
            emitEffect(
                Effect.OpenFolder(
                    accountId = folder.accountId,
                    folderId = folder.folder.id,
                ),
            )
        } else if (folder is UnifiedDisplayFolder) {
            emitEffect(Effect.OpenUnifiedFolder(folder.unifiedType.folderType))
        }

        viewModelScope.launch {
            delay(DRAWER_CLOSE_DELAY)
            emitEffect(Effect.CloseDrawer)
        }
    }

    private fun onSyncAccount() {
        if (state.value.isLoading || state.value.selectedAccountId == null) return

        viewModelScope.launch {
            updateState {
                it.copy(isLoading = true)
            }

            state.value.selectedAccountId?.let { syncAccount(it).collect() }

            updateState {
                it.copy(isLoading = false)
            }
        }
    }

    private fun onSyncAllAccounts() {
        if (state.value.isLoading) return

        viewModelScope.launch {
            updateState {
                it.copy(isLoading = true)
            }

            syncAllAccounts().collect()

            updateState {
                it.copy(isLoading = false)
            }
        }
    }
}
