package app.k9mail.legacy.ui.folder

import app.k9mail.legacy.mailstore.ListenableMessageStore
import app.k9mail.legacy.mailstore.MessageStoreFactory
import app.k9mail.legacy.mailstore.MessageStoreManager
import app.k9mail.legacy.message.controller.MessagingControllerRegistry
import app.k9mail.legacy.message.controller.MessagingListener
import assertk.assertThat
import assertk.assertions.isEmpty
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.thunderbird.components.core.outcome.Outcome
import net.thunderbird.core.android.account.AccountRemovedListener
import net.thunderbird.core.android.account.AccountsChangeListener
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.mail.folder.api.OutboxFolderManager
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DefaultDisplayFolderRepositoryTest {
    private val account = LegacyAccountDto(AccountIdFactory.create())
    private val messageStore = mock<ListenableMessageStore>()

    @Test
    fun `should return empty list when account doesn't exist`() = runTest {
        val testSubject = createTestSubject(accounts = emptyList())

        val displayFolders = testSubject.getDisplayFoldersFlow(account.id.toString()).first()

        assertThat(displayFolders).isEmpty()
    }

    @Test
    fun `should return empty list when message store of account isn't available anymore`() = runTest {
        whenever(messageStore.getDisplayFolders<Any>(any(), any(), any()))
            .thenThrow(IllegalStateException("attempt to re-open an already-closed object"))
        val testSubject = createTestSubject(accounts = listOf(account))

        val displayFolders = testSubject.getDisplayFoldersFlow(account.id.toString()).first()

        assertThat(displayFolders).isEmpty()
    }

    @Test
    fun `should return empty list when account of display folders doesn't exist anymore`() = runTest {
        val testSubject = createTestSubject(accounts = emptyList())

        val displayFolders = testSubject.getDisplayFoldersFlow(account, includeHiddenFolders = false).first()

        assertThat(displayFolders).isEmpty()
    }

    private fun createTestSubject(accounts: List<LegacyAccountDto>): DefaultDisplayFolderRepository {
        val accountManager = FakeLegacyAccountDtoManager(accounts)

        return DefaultDisplayFolderRepository(
            logger = TestLogger(),
            accountManager = accountManager,
            messagingController = FakeMessagingControllerRegistry(),
            messageStoreManager = MessageStoreManager(
                accountManager = accountManager,
                messageStoreFactory = FakeMessageStoreFactory(messageStore),
            ),
            outboxFolderManager = FakeOutboxFolderManager(),
            ioDispatcher = Dispatchers.Unconfined,
        )
    }
}

private class FakeLegacyAccountDtoManager(
    private val accounts: List<LegacyAccountDto> = emptyList(),
) : LegacyAccountDtoManager {
    override fun getAccounts(): List<LegacyAccountDto> = accounts

    override fun getAccountsFlow(): Flow<List<LegacyAccountDto>> = flowOf(accounts)

    override fun getById(accountId: AccountId): LegacyAccountDto? = accounts.find { it.id == accountId }

    override fun observeById(accountId: AccountId): Flow<LegacyAccountDto?> = flowOf(getById(accountId))

    override fun addAccountRemovedListener(listener: AccountRemovedListener) = Unit

    override fun moveAccount(account: LegacyAccountDto, newPosition: Int) = Unit

    override fun addOnAccountsChangeListener(accountsChangeListener: AccountsChangeListener) = Unit

    override fun removeOnAccountsChangeListener(accountsChangeListener: AccountsChangeListener) = Unit

    override fun saveAccount(account: LegacyAccountDto) = Unit
}

private class FakeMessageStoreFactory(
    private val messageStore: ListenableMessageStore,
) : MessageStoreFactory {
    override fun create(account: LegacyAccountDto): ListenableMessageStore = messageStore
}

private class FakeOutboxFolderManager : OutboxFolderManager {
    override suspend fun getOutboxFolderId(accountId: AccountId, createIfMissing: Boolean): Long = 0L

    override suspend fun createOutboxFolder(accountId: AccountId): Outcome<Long, Exception> =
        error("Not implemented")

    override suspend fun hasPendingMessages(accountId: AccountId): Boolean = error("Not implemented")
}

private class FakeMessagingControllerRegistry : MessagingControllerRegistry {
    override fun addListener(listener: MessagingListener) = Unit

    override fun removeListener(listener: MessagingListener) = Unit
}
