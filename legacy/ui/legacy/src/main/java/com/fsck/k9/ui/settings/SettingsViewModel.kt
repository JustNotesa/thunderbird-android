package com.fsck.k9.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.api.BackgroundAccountRemover

internal class SettingsViewModel(
    private val accountManager: LegacyAccountManager,
    private val accountRemover: BackgroundAccountRemover,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val pendingDeletionAccountIds = MutableStateFlow<Set<AccountId>>(emptySet())

    /**
     * The accounts to display. Accounts that are being deleted are hidden right away, before they are actually removed.
     */
    val accounts: StateFlow<List<LegacyAccount>?> = accountManager.observeAll()
        .onEach { removeDeletedAccountIds(it) }
        .combine(pendingDeletionAccountIds) { accounts, pendingDeletionIds ->
            accounts.filterNot { it.id in pendingDeletionIds }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val accountSelection = AccountSelection()
    private val selectedAccountIds = MutableStateFlow<Set<AccountId>>(emptySet())

    val selection: StateFlow<Set<AccountId>> = selectedAccountIds

    fun toggleAccountSelection(accountId: AccountId) {
        accountSelection.toggle(accountId)
        publishSelection()
    }

    fun selectAllAccounts(accountIds: List<AccountId>) {
        accountSelection.selectAll(accountIds)
        publishSelection()
    }

    fun clearAccountSelection() {
        accountSelection.clear()
        publishSelection()
    }

    fun isAllSelected(totalCount: Int): Boolean = accountSelection.isAllSelected(totalCount)

    fun deleteAccounts(accountIds: List<AccountId>) {
        pendingDeletionAccountIds.value = pendingDeletionAccountIds.value + accountIds

        accountIds.forEach { accountId ->
            accountRemover.removeAccountAsync(accountId)
        }
    }

    fun moveAccount(accountId: AccountId, newPosition: Int) {
        viewModelScope.launch(coroutineDispatcher) {
            // Delay saving the account so the animation is not disturbed
            delay(500)

            withContext(NonCancellable) {
                accountManager.moveAccount(accountId, newPosition)
            }
        }
    }

    private fun publishSelection() {
        selectedAccountIds.value = accountSelection.selectedIds
    }

    private fun removeDeletedAccountIds(accounts: List<LegacyAccount>) {
        val pendingIds = pendingDeletionAccountIds.value
        val remainingIds = pendingIds.filter { accountId -> accounts.any { it.id == accountId } }.toSet()

        if (remainingIds != pendingIds) {
            pendingDeletionAccountIds.value = remainingIds
        }
    }
}
