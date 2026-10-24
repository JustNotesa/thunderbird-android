package com.fsck.k9.ui.settings

import net.thunderbird.feature.account.AccountId

/**
 * Keeps track of the accounts the user selected in the settings list.
 */
internal class AccountSelection {
    private val selectedAccountIds = mutableSetOf<AccountId>()

    val selectedIds: Set<AccountId>
        get() = selectedAccountIds.toSet()

    val size: Int
        get() = selectedAccountIds.size

    fun toggle(accountId: AccountId) {
        if (!selectedAccountIds.remove(accountId)) {
            selectedAccountIds.add(accountId)
        }
    }

    fun selectAll(accountIds: List<AccountId>) {
        selectedAccountIds.clear()
        selectedAccountIds.addAll(accountIds)
    }

    fun clear() {
        selectedAccountIds.clear()
    }

    fun isSelected(accountId: AccountId): Boolean = accountId in selectedAccountIds

    fun isAllSelected(totalCount: Int): Boolean = totalCount > 0 && selectedAccountIds.size == totalCount
}
