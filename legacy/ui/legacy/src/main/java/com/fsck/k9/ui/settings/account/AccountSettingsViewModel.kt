package com.fsck.k9.ui.settings.account

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import app.k9mail.legacy.mailstore.MessageStoreManager
import com.fsck.k9.mailstore.SpecialFolderSelectionStrategy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.thunderbird.components.core.outcome.fold
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.core.common.exception.MessagingException
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.mail.folder.api.RemoteFolder
import net.thunderbird.feature.mail.folder.api.data.repository.RemoteFolderQueryRepository

private const val TAG = "AccountSettingsViewModel"

class AccountSettingsViewModel(
    private val accountManager: LegacyAccountDtoManager,
    private val remoteFolderQueryRepository: RemoteFolderQueryRepository,
    private val specialFolderSelectionStrategy: SpecialFolderSelectionStrategy,
    private val messageStoreManager: MessageStoreManager,
    private val logger: Logger,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val accounts = accountManager.getAccountsFlow().asLiveData()
    private var accountId: AccountId? = null
    private val accountLiveData = MutableLiveData<LegacyAccountDto?>()
    private val foldersLiveData = MutableLiveData<RemoteFolderInfo>()
    private val localStorageSizeLiveData = MutableLiveData<LocalStorageSize>()

    /**
     * The storage the messages and attachments of the account occupy on the device.
     */
    val localStorageSize: LiveData<LocalStorageSize> = localStorageSizeLiveData

    fun getAccount(accountId: AccountId): LiveData<LegacyAccountDto?> {
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
     * Returns the cached [LegacyAccountDto] if possible. Otherwise does a blocking load because
     * `PreferenceFragmentCompat` doesn't support asynchronous preference loading.
     */
    fun getAccountBlocking(accountId: AccountId): LegacyAccountDto {
        return accountLiveData.value
            ?: loadAccount(accountId).also { account ->
                this.accountId = accountId
                accountLiveData.value = account
            }
            ?: error("Account $accountId not found")
    }

    private fun loadAccount(accountId: AccountId): LegacyAccountDto? {
        return accountManager.getById(accountId)
    }

    fun loadLocalStorageSize(account: LegacyAccountDto) {
        viewModelScope.launch {
            localStorageSizeLiveData.value = withContext(backgroundDispatcher) {
                try {
                    LocalStorageSize.Known(messageStoreManager.getMessageStore(account).getSize())
                } catch (e: MessagingException) {
                    logger.error(TAG, e) { "Couldn't determine the local storage size" }
                    LocalStorageSize.Unknown
                }
            }
        }
    }

    fun getFolders(account: LegacyAccountDto): LiveData<RemoteFolderInfo> {
        if (foldersLiveData.value == null) {
            loadFolders(account)
        }

        return foldersLiveData
    }

    private fun loadFolders(account: LegacyAccountDto) {
        viewModelScope.launch {
            val remoteFolderInfo = withContext(backgroundDispatcher) {
                val folders = remoteFolderQueryRepository.getAllByAccountId(account.id)
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

data class RemoteFolderInfo(
    val folders: List<RemoteFolder>,
    val automaticSpecialFolders: Map<FolderType, RemoteFolder?>,
)
