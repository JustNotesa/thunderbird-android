package net.thunderbird.feature.navigation.drawer.dropdown

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.feature.account.AccountId

/**
 * In-memory [LegacyAccountManager] holding the accounts it was created with.
 */
internal class FakeLegacyAccountManager(
    initialAccounts: List<LegacyAccount> = emptyList(),
) : LegacyAccountManager {

    private val accounts = MutableStateFlow(initialAccounts)

    /** Adds or replaces an account, e.g. to simulate a change made by another component. */
    fun replace(account: LegacyAccount) {
        val list = accounts.value
        val index = list.indexOfFirst { it.id == account.id }
        accounts.value = if (index == -1) list + account else list.toMutableList().apply { set(index, account) }
    }

    override fun findAll(): List<LegacyAccount> = accounts.value

    override fun observeAll(): Flow<List<LegacyAccount>> = accounts

    override fun findById(accountId: AccountId): LegacyAccount? = accounts.value.find { it.id == accountId }

    override fun observeById(accountId: AccountId): Flow<LegacyAccount?> =
        accounts.map { list -> list.find { it.id == accountId } }

    override fun moveAccount(accountId: AccountId, newPosition: Int) {
        TODO("Not yet implemented")
    }

    override suspend fun create(account: LegacyAccount) {
        TODO("Not yet implemented")
    }

    override fun createSync(account: LegacyAccount) {
        TODO("Not yet implemented")
    }

    override suspend fun update(accountId: AccountId, transform: (LegacyAccount) -> LegacyAccount): LegacyAccount? {
        TODO("Not yet implemented")
    }

    override fun updateSync(accountId: AccountId, transform: (LegacyAccount) -> LegacyAccount): LegacyAccount? {
        TODO("Not yet implemented")
    }
}
