package com.fsck.k9.ui.managefolders

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.helper.SingleLiveEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.thunderbird.components.core.outcome.fold
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.mail.folder.api.FOLDER_DEFAULT_PATH_DELIMITER
import net.thunderbird.feature.mail.folder.api.Folder
import net.thunderbird.feature.mail.folder.api.FolderDetails
import net.thunderbird.feature.mail.folder.api.data.repository.FolderDetailsRepository
import net.thunderbird.legacy.logging.Log

private const val TAG = "FolderSettingsViewModel"

private const val NO_FOLDER_ID = 0L

@Suppress("TooManyFunctions")
class FolderSettingsViewModel(
    private val accountManager: LegacyAccountManager,
    private val folderDetailsRepository: FolderDetailsRepository,
    private val messagingController: MessagingController,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val actionLiveData = SingleLiveEvent<Action>()
    private var folderSettingsLiveData: LiveData<FolderSettingsResult>? = null

    private lateinit var accountId: AccountId
    private var account: LegacyAccount? = null
    private var folderId: Long = NO_FOLDER_ID

    val showClearFolderInMenu: Boolean
        get() = this::accountId.isInitialized && folderId != NO_FOLDER_ID

    fun getFolderSettingsLiveData(accountId: AccountId, folderId: Long): LiveData<FolderSettingsResult> {
        return folderSettingsLiveData ?: createFolderSettingsLiveData(accountId, folderId).also {
            folderSettingsLiveData = it
        }
    }

    private fun createFolderSettingsLiveData(
        accountId: AccountId,
        folderId: Long,
    ): LiveData<FolderSettingsResult> {
        return liveData(context = viewModelScope.coroutineContext) {
            val folderDetails = folderDetailsRepository.loadFolderDetails(accountId, folderId)
            if (folderDetails == null) {
                Log.w(TAG, "Folder with ID $folderId not found")
                emit(FolderNotFound)
                return@liveData
            }

            this@FolderSettingsViewModel.accountId = accountId
            this@FolderSettingsViewModel.account = loadAccount(accountId)
            this@FolderSettingsViewModel.folderId = folderId

            val folderSettingsData = FolderSettingsData(
                folder = folderDetails.folder,
                dataStore = FolderSettingsDataStore(folderDetailsRepository, accountId, folderDetails),
            )
            emit(folderSettingsData)
        }
    }

    private suspend fun loadAccount(accountId: AccountId): LegacyAccount = withContext(ioDispatcher) {
        accountManager.findById(accountId) ?: error("Missing account: $accountId")
    }

    private suspend fun FolderDetailsRepository.loadFolderDetails(
        accountId: AccountId,
        folderId: Long,
    ): FolderDetails? = withContext(ioDispatcher) {
        findById(accountId, folderId).fold(onSuccess = { it }, onFailure = { null })
    }

    fun showClearFolderConfirmationDialog() {
        sendActionEvent(Action.ShowClearFolderConfirmationDialog)
    }

    fun onClearFolderConfirmation() {
        messagingController.clearFolder(accountId, folderId)
    }

    val folderPathDelimiter: String
        get() = account?.folderPathDelimiter ?: FOLDER_DEFAULT_PATH_DELIMITER

    fun createSubfolder(name: String) {
        messagingController.createFolder(accountId, folderId, name) { error ->
            actionLiveData.postValue(if (error == null) Action.FolderCreated else Action.CreateFolderFailed(error))
        }
    }

    fun renameFolder(name: String) {
        messagingController.renameFolder(accountId, folderId, name) { error ->
            actionLiveData.postValue(if (error == null) Action.FolderRenamed else Action.RenameFolderFailed(error))
        }
    }

    fun showDeleteFolderConfirmationDialog() {
        viewModelScope.launch {
            val subfolderCount = withContext(ioDispatcher) {
                messagingController.getSubfolderCount(accountId, folderId)
            }
            sendActionEvent(Action.ShowDeleteFolderConfirmationDialog(subfolderCount))
        }
    }

    fun onDeleteFolderConfirmation(includeSubfolders: Boolean) {
        messagingController.deleteFolder(accountId, folderId, includeSubfolders) { error ->
            actionLiveData.postValue(if (error == null) Action.FolderDeleted else Action.DeleteFolderFailed(error))
        }
    }

    fun getActionEvents(): LiveData<Action> = actionLiveData

    private fun sendActionEvent(action: Action) {
        actionLiveData.value = action
    }
}

sealed class FolderSettingsResult
object FolderNotFound : FolderSettingsResult()
data class FolderSettingsData(val folder: Folder, val dataStore: FolderSettingsDataStore) : FolderSettingsResult()

sealed class Action {
    object ShowClearFolderConfirmationDialog : Action()
    data class ShowDeleteFolderConfirmationDialog(val subfolderCount: Int) : Action()
    object FolderRenamed : Action()
    data class RenameFolderFailed(val reason: String) : Action()
    object FolderCreated : Action()
    data class CreateFolderFailed(val reason: String) : Action()
    object FolderDeleted : Action()
    data class DeleteFolderFailed(val reason: String) : Action()
}
