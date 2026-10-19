package com.fsck.k9.ui.settings.account

import app.k9mail.legacy.mailstore.ListenableMessageStore
import app.k9mail.legacy.mailstore.MessageStore
import app.k9mail.legacy.mailstore.MessageStoreFactory
import app.k9mail.legacy.mailstore.MessageStoreManager
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.backend.BackendManager
import com.fsck.k9.backend.api.Backend
import com.fsck.k9.backend.api.StorageQuota
import com.fsck.k9.mailstore.SpecialFolderSelectionStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.core.android.testing.RobolectricTest
import net.thunderbird.core.common.exception.MessagingException
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.AccountIdFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSettingsViewModelTest : RobolectricTest() {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val account = LegacyAccountDto(AccountIdFactory.create())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadLocalStorageSize should provide size of the message store`() {
        val testSubject = createTestSubject(mock<MessageStore> { on { getSize() } doReturn 1_234_567L })

        testSubject.loadLocalStorageSize(account)

        assertThat(testSubject.localStorageSize.value).isEqualTo(LocalStorageSize.Known(bytes = 1_234_567L))
    }

    @Test
    fun `loadLocalStorageSize should report unknown size when it can't be determined`() {
        val testSubject = createTestSubject(
            mock<MessageStore> { on { getSize() } doThrow MessagingException("Database unavailable") },
        )

        testSubject.loadLocalStorageSize(account)

        assertThat(testSubject.localStorageSize.value).isEqualTo(LocalStorageSize.Unknown)
    }

    @Test
    fun `loadServerStorage should provide the storage quota of the server`() {
        val backend = mock<Backend> {
            on { getStorageQuota() } doReturn StorageQuota(usedBytes = 1_000L, limitBytes = 5_000L)
        }
        val testSubject = createTestSubject(backend = backend)

        testSubject.loadServerStorage(account)

        assertThat(testSubject.serverStorage.value)
            .isEqualTo(ServerStorage.Known(usedBytes = 1_000L, limitBytes = 5_000L))
    }

    @Test
    fun `loadServerStorage should tell when the server doesn't report its storage`() {
        val testSubject = createTestSubject(backend = mock<Backend> { on { getStorageQuota() } doReturn null })

        testSubject.loadServerStorage(account)

        assertThat(testSubject.serverStorage.value).isEqualTo(ServerStorage.NotReported)
    }

    @Test
    fun `loadServerStorage should report unknown storage when the server can't be asked`() {
        val backend = mock<Backend> { on { getStorageQuota() } doThrow MessagingException("No connection") }
        val testSubject = createTestSubject(backend = backend)

        testSubject.loadServerStorage(account)

        assertThat(testSubject.serverStorage.value).isEqualTo(ServerStorage.Unknown)
    }

    @Test
    fun `free server storage should not be negative when the quota is exceeded`() {
        val serverStorage = ServerStorage.Known(usedBytes = 6_000L, limitBytes = 5_000L)

        assertThat(serverStorage.freeBytes).isEqualTo(0L)
    }

    private fun createTestSubject(
        messageStore: MessageStore = mock(),
        backend: Backend = mock(),
    ): AccountSettingsViewModel {
        val messageStoreFactory = object : MessageStoreFactory {
            override fun create(account: LegacyAccountDto) = ListenableMessageStore(messageStore)
        }

        return AccountSettingsViewModel(
            accountManager = mock<LegacyAccountDtoManager> { on { getAccountsFlow() } doReturn emptyFlow() },
            remoteFolderQueryRepository = mock(),
            specialFolderSelectionStrategy = SpecialFolderSelectionStrategy(),
            messageStoreManager = MessageStoreManager(
                accountManager = mock(),
                messageStoreFactory = messageStoreFactory,
            ),
            backendManager = mock<BackendManager> { on { getBackend(any<AccountId>()) } doReturn backend },
            logger = TestLogger(),
            backgroundDispatcher = testDispatcher,
        )
    }
}
