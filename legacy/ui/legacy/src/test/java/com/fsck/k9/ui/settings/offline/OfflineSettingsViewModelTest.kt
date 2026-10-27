package com.fsck.k9.ui.settings.offline

import app.k9mail.legacy.mailstore.ListenableMessageStore
import app.k9mail.legacy.mailstore.MessageStoreManager
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.fsck.k9.FakeLegacyAccount
import com.fsck.k9.job.OfflineDownloadState
import com.fsck.k9.job.OfflineDownloadStatus
import com.fsck.k9.job.OfflineDownloadStatusStore
import com.fsck.k9.job.OfflineDownloadWorkerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.core.android.account.fake.FakeLegacyAccountManager
import net.thunderbird.core.common.exception.MessagingException
import net.thunderbird.core.preference.network.NetworkSettings
import net.thunderbird.core.preference.network.NetworkSettingsPreferenceManager
import net.thunderbird.feature.account.AccountIdFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private const val STORAGE_LIMIT_MEGABYTES = 2048
private const val KEEP_FREE_MEGABYTES = 500

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineSettingsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val networkSettingsManager = FakeNetworkSettingsManager()
    private val statusStore = mock<OfflineDownloadStatusStore>()
    private val workerManager = mock<OfflineDownloadWorkerManager>()
    private val accountManager = FakeLegacyAccountManager()
    private val messageStoreManager = mock<MessageStoreManager>()
    private val statusFlow = MutableStateFlow(DEFAULT_STATUS)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(statusStore.status).thenReturn(statusFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `settings are read from the general settings manager`() {
        val viewModel = createViewModel()

        val uiState = viewModel.uiState.value

        assertThat(uiState.isEnabled).isTrue()
        assertThat(uiState.isOnlyWhileCharging).isTrue()
        assertThat(uiState.storageLimitMegabytes).isEqualTo(STORAGE_LIMIT_MEGABYTES)
        assertThat(uiState.keepFreeMegabytes).isEqualTo(KEEP_FREE_MEGABYTES)
    }

    @Test
    fun `the status of the offline download is part of the ui state`() {
        val viewModel = createViewModel()

        val uiState = viewModel.uiState.value

        assertThat(uiState.status).isEqualTo(DEFAULT_STATUS)
    }

    @Test
    fun `enabling the offline download is saved and scheduled`() {
        val viewModel = createViewModel()

        viewModel.setEnabled(false)

        assertThat(networkSettingsManager.getConfig().offlineDownload).isFalse()
        verify(workerManager).schedule(any(), eq(true))
    }

    @Test
    fun `disabling the offline download is saved and scheduled`() {
        val viewModel = createViewModel()

        viewModel.setEnabled(true)

        assertThat(networkSettingsManager.getConfig().offlineDownload).isTrue()
        verify(workerManager).schedule(any(), eq(true))
    }

    @Test
    fun `the charging setting is saved and scheduled`() {
        val viewModel = createViewModel()

        viewModel.setOnlyWhileCharging(false)

        assertThat(networkSettingsManager.getConfig().offlineDownloadOnlyWhileCharging).isFalse()
        verify(workerManager).schedule(any(), eq(true))
    }

    @Test
    fun `the storage limit is saved and scheduled`() {
        val viewModel = createViewModel()

        viewModel.setStorageLimit(5120)

        assertThat(networkSettingsManager.getConfig().offlineStorageLimitMegabytes).isEqualTo(5120)
        verify(workerManager).schedule(any(), eq(true))
    }

    @Test
    fun `the amount of free storage is saved and scheduled`() {
        val viewModel = createViewModel()

        viewModel.setKeepFree(1024)

        assertThat(networkSettingsManager.getConfig().offlineKeepFreeMegabytes).isEqualTo(1024)
        verify(workerManager).schedule(any(), eq(true))
    }

    @Test
    fun `the used storage is the sum of the size of all message stores`() {
        val account = FakeLegacyAccount.create(id = AccountIdFactory.create())
        accountManager.replace(account)
        val messageStore = mock<ListenableMessageStore>()
        whenever(messageStore.getSize()).thenReturn(100L)
        whenever(messageStoreManager.getMessageStore(account.id)).thenReturn(messageStore)

        val viewModel = createViewModel()

        assertThat(viewModel.uiState.value.usedBytes).isEqualTo(100L)
    }

    @Test
    fun `the used storage is unknown when the size can't be determined`() {
        val account = FakeLegacyAccount.create(id = AccountIdFactory.create())
        accountManager.replace(account)
        val messageStore = mock<ListenableMessageStore>()
        doAnswer { throw MessagingException("failed") }.whenever(messageStore).getSize()
        whenever(messageStoreManager.getMessageStore(account.id)).thenReturn(messageStore)

        val viewModel = createViewModel()

        assertThat(viewModel.uiState.value.usedBytes).isNull()
    }

    private fun createViewModel(): OfflineSettingsViewModel {
        return OfflineSettingsViewModel(
            networkSettingsManager = networkSettingsManager,
            statusStore = statusStore,
            workerManager = workerManager,
            accountManager = accountManager,
            messageStoreManager = messageStoreManager,
            logger = TestLogger(),
            backgroundDispatcher = testDispatcher,
        )
    }
}

private val DEFAULT_STATUS = OfflineDownloadStatus(
    state = OfflineDownloadState.RUNNING,
    lastRunStart = 1_000L,
    lastRunDurationMillis = 2_000L,
    folderCount = 10,
    completeFolderCount = 5,
    lastCompleteTime = null,
    lastError = null,
)

private class FakeNetworkSettingsManager : NetworkSettingsPreferenceManager {
    private var settings = NetworkSettings(
        offlineDownload = true,
        offlineDownloadOnlyWhileCharging = true,
        offlineStorageLimitMegabytes = STORAGE_LIMIT_MEGABYTES,
        offlineKeepFreeMegabytes = KEEP_FREE_MEGABYTES,
    )

    override fun save(config: NetworkSettings) {
        settings = config
    }

    override fun getConfig(): NetworkSettings = settings

    override fun getConfigFlow(): Flow<NetworkSettings> = flowOf(settings)
}
