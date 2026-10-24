package com.fsck.k9.ui.settings

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.thunderbird.core.android.account.AccountRemovedListener
import net.thunderbird.core.android.account.AccountsChangeListener
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.account.settings.api.BackgroundAccountRemover
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val testCoroutineDispatcher = StandardTestDispatcher()
    private val accountRemover = FakeBackgroundAccountRemover()
    private val accountManager = FakeLegacyAccountDtoManager()

    private val viewModel: SettingsViewModel by lazy {
        SettingsViewModel(
            accountManager = accountManager,
            accountRemover = accountRemover,
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testCoroutineDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggling an account adds it to the selection`() {
        val accountId = AccountIdFactory.create()

        viewModel.toggleAccountSelection(accountId)

        assertThat(viewModel.selection.value).isEqualTo(setOf(accountId))
    }

    @Test
    fun `toggling a selected account removes it from the selection`() {
        val accountId = AccountIdFactory.create()
        viewModel.toggleAccountSelection(accountId)

        viewModel.toggleAccountSelection(accountId)

        assertThat(viewModel.selection.value).isEmpty()
    }

    @Test
    fun `selecting all accounts updates the selection`() {
        val firstAccountId = AccountIdFactory.create()
        val secondAccountId = AccountIdFactory.create()

        viewModel.selectAllAccounts(listOf(firstAccountId, secondAccountId))

        assertThat(viewModel.selection.value).isEqualTo(setOf(firstAccountId, secondAccountId))
        assertThat(viewModel.isAllSelected(2)).isTrue()
    }

    @Test
    fun `isAllSelected is false when not all accounts are selected`() {
        val accountId = AccountIdFactory.create()
        viewModel.toggleAccountSelection(accountId)

        assertThat(viewModel.isAllSelected(2)).isFalse()
    }

    @Test
    fun `clearing the selection removes all selected accounts`() {
        val accountId = AccountIdFactory.create()
        viewModel.toggleAccountSelection(accountId)

        viewModel.clearAccountSelection()

        assertThat(viewModel.selection.value).isEmpty()
    }

    @Test
    fun `deleting accounts removes all of them`() {
        val firstAccountId = AccountIdFactory.create()
        val secondAccountId = AccountIdFactory.create()

        viewModel.deleteAccounts(listOf(firstAccountId, secondAccountId))

        assertThat(accountRemover.removedAccountIds).containsExactly(firstAccountId, secondAccountId)
    }
}

private class FakeBackgroundAccountRemover : BackgroundAccountRemover {
    val removedAccountIds = mutableListOf<AccountId>()

    override fun removeAccountAsync(accountId: AccountId) {
        removedAccountIds.add(accountId)
    }
}

private class FakeLegacyAccountDtoManager : LegacyAccountDtoManager {
    private val accounts = mutableListOf<LegacyAccountDto>()

    override fun getAccounts(): List<LegacyAccountDto> = accounts

    override fun getAccountsFlow(): Flow<List<LegacyAccountDto>> = flowOf(accounts)

    override fun getById(accountId: AccountId): LegacyAccountDto? = accounts.find { it.id == accountId }

    override fun observeById(accountId: AccountId): Flow<LegacyAccountDto?> = flowOf(null)

    override fun addAccountRemovedListener(listener: AccountRemovedListener) = Unit

    override fun moveAccount(account: LegacyAccountDto, newPosition: Int) = Unit

    override fun addOnAccountsChangeListener(accountsChangeListener: AccountsChangeListener) = Unit

    override fun removeOnAccountsChangeListener(accountsChangeListener: AccountsChangeListener) = Unit

    override fun saveAccount(account: LegacyAccountDto) = Unit
}
