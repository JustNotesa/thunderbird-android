package com.fsck.k9.ui.settings.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.k9mail.legacy.mailstore.MessageStoreManager
import com.fsck.k9.job.OfflineDownloadStatus
import com.fsck.k9.job.OfflineDownloadStatusStore
import com.fsck.k9.job.OfflineDownloadWorkerManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.thunderbird.components.core.logging.Logger
import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.core.common.exception.MessagingException
import net.thunderbird.core.preference.network.NetworkSettingsPreferenceManager
import net.thunderbird.core.preference.network.NetworkSettings
import net.thunderbird.core.preference.update

private const val TAG = "OfflineSettingsViewModel"

/**
 * The state of the "Available offline" settings screen.
 */
data class OfflineSettingsUiState(
    val isEnabled: Boolean,
    val isOnlyWhileCharging: Boolean,
    val storageLimitMegabytes: Int,
    val keepFreeMegabytes: Int,
    val status: OfflineDownloadStatus,
    val usedBytes: Long?,
)

/**
 * Configures the background download of all messages and reports how it is doing.
 */
class OfflineSettingsViewModel(
    private val networkSettingsManager: NetworkSettingsPreferenceManager,
    private val statusStore: OfflineDownloadStatusStore,
    private val workerManager: OfflineDownloadWorkerManager,
    private val accountManager: LegacyAccountManager,
    private val messageStoreManager: MessageStoreManager,
    private val logger: Logger,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val networkSettings = MutableStateFlow(networkSettingsManager.getConfig())
    private val usedBytesState = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<OfflineSettingsUiState> = combine(
        networkSettings,
        statusStore.status,
        usedBytesState,
    ) { settings, status, usedBytes ->
        OfflineSettingsUiState(
            isEnabled = settings.offlineDownload,
            isOnlyWhileCharging = settings.offlineDownloadOnlyWhileCharging,
            storageLimitMegabytes = settings.offlineStorageLimitMegabytes,
            keepFreeMegabytes = settings.offlineKeepFreeMegabytes,
            status = status,
            usedBytes = usedBytes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = createUiState(
            settings = networkSettings.value,
            status = statusStore.status.value,
            usedBytes = null,
        ),
    )

    init {
        viewModelScope.launch {
            statusStore.status.collect {
                usedBytesState.value = loadUsedBytes()
            }
        }
    }

    fun setEnabled(isEnabled: Boolean) {
        updateNetworkSettings { it.copy(offlineDownload = isEnabled) }
    }

    fun setOnlyWhileCharging(isOnlyWhileCharging: Boolean) {
        updateNetworkSettings { it.copy(offlineDownloadOnlyWhileCharging = isOnlyWhileCharging) }
    }

    fun setStorageLimit(storageLimitMegabytes: Int) {
        updateNetworkSettings { it.copy(offlineStorageLimitMegabytes = storageLimitMegabytes) }
    }

    fun setKeepFree(keepFreeMegabytes: Int) {
        updateNetworkSettings { it.copy(offlineKeepFreeMegabytes = keepFreeMegabytes) }
    }

    private fun updateNetworkSettings(transform: (NetworkSettings) -> NetworkSettings) {
        val newSettings = transform(networkSettings.value)
        networkSettings.value = newSettings

        // Saved with the manager of the network settings only. The manager of all general settings may not have
        // loaded the other settings yet and would overwrite them with their defaults.
        networkSettingsManager.save(newSettings)

        workerManager.schedule(newSettings, isSettingsChange = true)
    }

    /**
     * The storage the messages of all accounts occupy on this device.
     */
    private suspend fun loadUsedBytes(): Long? {
        return withContext(backgroundDispatcher) {
            try {
                accountManager.findAll().sumOf { account ->
                    messageStoreManager.getMessageStore(account.id).getSize()
                }
            } catch (e: MessagingException) {
                logger.error(TAG, e) { "Couldn't determine the local storage size" }
                null
            }
        }
    }

    private fun createUiState(
        settings: NetworkSettings,
        status: OfflineDownloadStatus,
        usedBytes: Long?,
    ): OfflineSettingsUiState {
        return OfflineSettingsUiState(
            isEnabled = settings.offlineDownload,
            isOnlyWhileCharging = settings.offlineDownloadOnlyWhileCharging,
            storageLimitMegabytes = settings.offlineStorageLimitMegabytes,
            keepFreeMegabytes = settings.offlineKeepFreeMegabytes,
            status = status,
            usedBytes = usedBytes,
        )
    }
}
