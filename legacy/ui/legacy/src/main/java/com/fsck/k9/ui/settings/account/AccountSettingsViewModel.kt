package com.fsck.k9.ui.settings.account

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import app.k9mail.legacy.mailstore.MessageStoreManager
import com.fsck.k9.backend.BackendManager
import com.fsck.k9.mailstore.SpecialFolderSelectionStrategy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.thunderbird.components.core.logging.Logger
import net.thunderbird.components.core.outcome.fold
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.core.common.exception.MessagingException
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.mail.folder.api.RemoteFolder
import net.thunderbird.feature.mail.folder.api.data.repository.RemoteFolderQueryRepository

private const val TAG = "AccountSettingsViewModel"

class AccountSettingsViewModel(
    private val accountManager: LegacyAccountManager,
    private val remoteFolderQueryRepository: RemoteFolderQueryRepository,
    private val specialFolderSelectionStrategy: SpecialFolderSelectionStrategy,
    private val messageStoreManager: MessageStoreManager,
    private val backendManager: BackendManager,
    private val logger: Logger,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val accounts = accountManager.observeAll().asLiveData()
    private var accountId: AccountId? = null
    private val accountLiveData = MutableLiveData<LegacyAccount?>()
    private val foldersLiveData = MutableLiveData<RemoteFolderInfo>()
    private val localStorageSizeLiveData = MutableLiveData<LocalStorageSize>()

    /**
     * The storage the messages and attachments of the account occupy on the device.
     */
    val localStorageSize: LiveData<LocalStorageSize> = localStorageSizeLiveData

    private val serverStorageLiveData = MutableLiveData<ServerStorage>()

    /**
     * The storage the account uses on the server.
     */
    val serverStorage: LiveData<ServerStorage> = serverStorageLiveData

    fun getAccount(accountId: AccountId): LiveData<LegacyAccount?> {
        if (this.accountId != accountId) {
            this.accountId = accountId
            viewModelScope.launch {
                val account = withContext(backgroundDispatcher) {
                    loadAccount(accountId)
                }
                accountLiveData.value = account
            }
        }

        return accountLiveData
    }

    /**
     * Returns the cached [LegacyAccount] if possible. Otherwise does a blocking load because
     * `PreferenceFragmentCompat` doesn't support asynchronous preference loading.
     */
    fun getAccountBlocking(accountId: AccountId): LegacyAccount {
        return accountLiveData.value
            ?: loadAccount(accountId).also { account ->
                this.accountId = accountId
                accountLiveData.value = account
            }
            ?: error("Account $accountId not found")
    }

    private fun loadAccount(accountId: AccountId): LegacyAccount? {
        return accountManager.findById(accountId)
    }

    fun loadLocalStorageSize(accountId: AccountId) {
        viewModelScope.launch {
            localStorageSizeLiveData.value = withContext(backgroundDispatcher) {
                try {
                    LocalStorageSize.Known(messageStoreManager.getMessageStore(accountId).getSize())
                } catch (e: MessagingException) {
                    logger.error(TAG, e) { "Couldn't determine the local storage size" }
                    LocalStorageSize.Unknown
                }
            }
        }
    }

    fun loadServerStorage(accountId: AccountId) {
        serverStorageLiveData.value = ServerStorage.Loading

        viewModelScope.launch {
            serverStorageLiveData.value = withContext(backgroundDispatcher) {
                try {
                    val storageQuota = backendManager.getBackend(accountId).getStorageQuota()
                    if (storageQuota != null) {
                        ServerStorage.Known(usedBytes = storageQuota.usedBytes, limitBytes = storageQuota.limitBytes)
                    } else {
                        ServerStorage.NotReported
                    }
                } catch (e: MessagingException) {
                    logger.error(TAG, e) { "Couldn't determine the storage used on the server" }
                    ServerStorage.Unknown
                }
            }
        }
    }

    fun getFolders(accountId: AccountId): LiveData<RemoteFolderInfo> {
        if (foldersLiveData.value == null) {
            loadFolders(accountId)
        }

        return foldersLiveData
    }

    private fun loadFolders(accountId: AccountId) {
        viewModelScope.launch {
            val remoteFolderInfo = withContext(backgroundDispatcher) {
                val folders = remoteFolderQueryRepository.getAllByAccountId(accountId)
                    .fold(
                        onSuccess = { it },
                        onFailure = { error ->
                            when (val throwable = error.throwable) {
                                null -> error("Unknown error while loading folders. Error: $error")
                                else -> throw throwable
                            }
                        },
                    )
                    .sortedWith(
                        compareByDescending<RemoteFolder> { it.type == FolderType.INBOX }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                    )

                val automaticSpecialFolders = getAutomaticSpecialFolders(folders)
                RemoteFolderInfo(folders, automaticSpecialFolders)
            }
            foldersLiveData.value = remoteFolderInfo
        }
    }

    private fun getAutomaticSpecialFolders(folders: List<RemoteFolder>): Map<FolderType, RemoteFolder?> {
        return mapOf(
            FolderType.ARCHIVE to specialFolderSelectionStrategy.selectSpecialFolder(folders, FolderType.ARCHIVE),
            FolderType.DRAFTS to specialFolderSelectionStrategy.selectSpecialFolder(folders, FolderType.DRAFTS),
            FolderType.SENT to specialFolderSelectionStrategy.selectSpecialFolder(folders, FolderType.SENT),
            FolderType.SPAM to specialFolderSelectionStrategy.selectSpecialFolder(folders, FolderType.SPAM),
            FolderType.TRASH to specialFolderSelectionStrategy.selectSpecialFolder(folders, FolderType.TRASH),
        )
    }
}

sealed interface LocalStorageSize {
    data class Known(val bytes: Long) : LocalStorageSize
    data object Unknown : LocalStorageSize
}

sealed interface ServerStorage {
    data object Loading : ServerStorage
    data class Known(val usedBytes: Long, val limitBytes: Long) : ServerStorage {
        val freeBytes: Long
            get() = (limitBytes - usedBytes).coerceAtLeast(0L)
    }

    /**
     * The server doesn't tell how much storage is used.
     */
    data object NotReported : ServerStorage
    data object Unknown : ServerStorage
}

data class RemoteFolderInfo(
    val folders: List<RemoteFolder>,
    val automaticSpecialFolders: Map<FolderType, RemoteFolder?>,
)
