package app.k9mail.legacy.ui.folder

import app.k9mail.legacy.mailstore.ListenableMessageStore
import app.k9mail.legacy.mailstore.MessageStoreFactory
import app.k9mail.legacy.mailstore.MessageStoreManager
import app.k9mail.legacy.message.controller.MessagingControllerRegistry
import app.k9mail.legacy.message.controller.MessagingListener
import assertk.assertThat
import assertk.assertions.isEmpty
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.ServerSettings
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.components.core.outcome.Outcome
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.fake.FakeLegacyAccountManager
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.account.storage.profile.AvatarDto
import net.thunderbird.feature.account.storage.profile.AvatarTypeDto
import net.thunderbird.feature.account.storage.profile.ProfileDto
import net.thunderbird.feature.mail.folder.api.OutboxFolderManager
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DefaultDisplayFolderRepositoryTest {
    private val account = createLegacyAccount()
    private val messageStore = mock<ListenableMessageStore>()

    @Test
    fun `should return empty list when account doesn't exist`() = runTest {
        val testSubject = createTestSubject(accounts = emptyList())

        val displayFolders = testSubject.getDisplayFoldersFlow(account.id).first()

        assertThat(displayFolders).isEmpty()
    }

    @Test
    fun `should return empty list when message store of account isn't available anymore`() = runTest {
        whenever(messageStore.getDisplayFolders<Any>(any(), any(), any()))
            .thenThrow(IllegalStateException("attempt to re-open an already-closed object"))
        val testSubject = createTestSubject(accounts = listOf(account))

        val displayFolders = testSubject.getDisplayFoldersFlow(account.id).first()

        assertThat(displayFolders).isEmpty()
    }

    @Test
    fun `should return empty list when account of display folders doesn't exist anymore`() = runTest {
        val testSubject = createTestSubject(accounts = emptyList())

        val displayFolders = testSubject.getDisplayFoldersFlow(account.id, includeHiddenFolders = false).first()

        assertThat(displayFolders).isEmpty()
    }

    private fun createTestSubject(accounts: List<LegacyAccount>): DefaultDisplayFolderRepository {
        val accountManager = FakeLegacyAccountManager(accounts)

        return DefaultDisplayFolderRepository(
            logger = TestLogger(),
            accountManager = accountManager,
            messagingController = FakeMessagingControllerRegistry(),
            messageStoreManager = MessageStoreManager(
                messageStoreFactory = FakeMessageStoreFactory(messageStore),
            ),
            outboxFolderManager = FakeOutboxFolderManager(),
            ioDispatcher = Dispatchers.Unconfined,
        )
    }
}

private fun createLegacyAccount(id: AccountId = AccountIdFactory.create()): LegacyAccount {
    return LegacyAccount(
        id = id,
        name = "test",
        email = "test@example.com",
        profile = ProfileDto(
            id = id,
            name = "test",
            color = -1,
            avatar = AvatarDto(
                accountId = id,
                avatarType = AvatarTypeDto.MONOGRAM,
                avatarMonogram = "TE",
                avatarImageUri = null,
                avatarIconName = null,
            ),
        ),
        incomingServerSettings = ServerSettings(
            type = "imap",
            host = "host",
            port = 993,
            connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
            authenticationType = AuthType.PLAIN,
            username = "user",
            password = "pass",
            clientCertificateAlias = null,
        ),
        outgoingServerSettings = ServerSettings(
            type = "smtp",
            host = "host",
            port = 465,
            connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
            authenticationType = AuthType.PLAIN,
            username = "user",
            password = "pass",
            clientCertificateAlias = null,
        ),
        identities = emptyList(),
    )
}

private class FakeMessageStoreFactory(
    private val messageStore: ListenableMessageStore,
) : MessageStoreFactory {
    override fun create(accountId: AccountId): ListenableMessageStore = messageStore
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
