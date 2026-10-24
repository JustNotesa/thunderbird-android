package app.k9mail.feature.settings.import.ui

import app.k9mail.feature.settings.import.ConnectionCheckResult
import app.k9mail.feature.settings.import.ui.SettingsListItem.GeneralSettings

internal class SettingsImportUiModel {
    private var isShowMigrationActions = false

    var settingsList: List<SettingsListItem> = emptyList()
    var isSettingsListVisible = false
    var isSettingsListEnabled = true
    var importButton: ButtonState = ButtonState.DISABLED
    var closeButton: ButtonState = ButtonState.GONE
    var closeButtonLabel: CloseButtonLabel = CloseButtonLabel.OK
    var isPickDocumentButtonVisible = true
    var isPickDocumentButtonEnabled = true
    var isScanQrCodeButtonVisible = isShowMigrationActions
    var isScanQrCodeButtonEnabled = true
    var isPasteAccountsButtonVisible = isShowMigrationActions
    var isPasteAccountsButtonEnabled = true
    var isPickAppButtonVisible = isShowMigrationActions
    var isPickAppButtonEnabled = false
    var isPickAppButtonPermanentlyDisabled = true
    var isLoadingProgressVisible = false
    var isImportProgressVisible = false
    var statusText = StatusText.HIDDEN
    var isPasswordManagerPasteFlow = false
    var isPasswordManagerReminderVisible = false

    val hasImportStarted
        get() = importButton == ButtonState.GONE

    val hasDocumentBeenRead
        get() = isSettingsListVisible

    val wasAccountImportSuccessful
        get() = hasImportStarted && settingsList.any { it !is GeneralSettings && it.importStatus.isSuccess }

    /**
     * The number of accounts that were imported successfully.
     */
    val importedAccountCount: Int
        get() = settingsList.count { it is SettingsListItem.Account && it.importStatus == ImportStatus.IMPORT_SUCCESS }

    fun enablePickButtons() {
        isPickDocumentButtonEnabled = true
        isScanQrCodeButtonEnabled = true
        isPasteAccountsButtonEnabled = true
        maybeEnablePickAppButton()
    }

    private fun maybeEnablePickAppButton() {
        if (!isPickAppButtonPermanentlyDisabled) {
            isPickAppButtonEnabled = true
        }
    }

    fun disablePickButtons() {
        statusText = StatusText.HIDDEN
        isPickDocumentButtonEnabled = false
        isScanQrCodeButtonEnabled = false
        isPasteAccountsButtonEnabled = false
        isPickAppButtonEnabled = false
    }

    private fun enableImportButton() {
        importButton = ButtonState.ENABLED
        isImportProgressVisible = false
        isSettingsListEnabled = true
    }

    private fun disableImportButton() {
        importButton = ButtonState.DISABLED
        isImportProgressVisible = false
    }

    fun showLoadingProgress() {
        isLoadingProgressVisible = true
        isPickDocumentButtonVisible = false
        isScanQrCodeButtonVisible = false
        isPasteAccountsButtonVisible = false
        isPickAppButtonVisible = false
        isSettingsListEnabled = false
        statusText = StatusText.HIDDEN
    }

    fun showImportingProgress() {
        isImportProgressVisible = true
        isSettingsListEnabled = false
        importButton = ButtonState.INVISIBLE
        statusText = StatusText.IMPORTING_PROGRESS
    }

    private fun showSuccessText() {
        importButton = ButtonState.GONE
        closeButton = ButtonState.ENABLED
        closeButtonLabel = CloseButtonLabel.OK
        isImportProgressVisible = false
        isSettingsListEnabled = true
        statusText = StatusText.IMPORT_SUCCESS
        isPasswordManagerReminderVisible = isPasswordManagerPasteFlow
    }

    private fun showActionRequiredText(actionText: StatusText) {
        importButton = ButtonState.GONE
        closeButton = ButtonState.ENABLED
        closeButtonLabel = CloseButtonLabel.LATER
        isImportProgressVisible = false
        isSettingsListEnabled = true
        statusText = actionText
    }

    fun showReadFailureText() {
        isLoadingProgressVisible = false
        isPickDocumentButtonVisible = true
        isPickDocumentButtonEnabled = true
        isScanQrCodeButtonVisible = isShowMigrationActions
        isScanQrCodeButtonEnabled = true
        isPasteAccountsButtonVisible = isShowMigrationActions
        isPasteAccountsButtonEnabled = true
        isPickAppButtonVisible = isShowMigrationActions
        maybeEnablePickAppButton()
        statusText = StatusText.IMPORT_READ_FAILURE
        importButton = ButtonState.DISABLED
    }

    fun showImportErrorText() {
        isLoadingProgressVisible = false
        isImportProgressVisible = false
        isSettingsListVisible = false
        isPickDocumentButtonVisible = true
        isPickDocumentButtonEnabled = true
        isScanQrCodeButtonVisible = isShowMigrationActions
        isScanQrCodeButtonEnabled = true
        isPasteAccountsButtonVisible = isShowMigrationActions
        isPasteAccountsButtonEnabled = true
        isPickAppButtonVisible = isShowMigrationActions
        maybeEnablePickAppButton()
        statusText = StatusText.IMPORT_FAILURE
        importButton = ButtonState.DISABLED
    }

    private fun showPartialImportErrorText() {
        importButton = ButtonState.GONE
        closeButton = ButtonState.ENABLED
        closeButtonLabel = CloseButtonLabel.OK
        isImportProgressVisible = false
        isSettingsListEnabled = true
        statusText = StatusText.IMPORT_PARTIAL_FAILURE
    }

    fun initializeSettingsList(list: List<SettingsListItem>) {
        settingsList = list
        isSettingsListVisible = true
        isLoadingProgressVisible = false
        isPickDocumentButtonVisible = false
        isScanQrCodeButtonVisible = false
        isPasteAccountsButtonVisible = false
        isPickAppButtonVisible = false
        updateImportButtonFromSelection()
    }

    fun toggleSettingsListItemSelection(position: Int) {
        val settingsListItem = settingsList[position]
        settingsListItem.selected = !settingsListItem.selected
        statusText = StatusText.HIDDEN
        updateImportButtonFromSelection()
    }

    fun setSettingsListState(position: Int, status: ImportStatus) {
        settingsList[position].importStatus = status
        settingsList[position].enabled = status.isActionRequired
    }

    fun setConnectionStatus(position: Int, connectionStatus: ConnectionStatus) {
        val settingsListItem = settingsList[position]
        if (settingsListItem is SettingsListItem.Account) {
            settingsListItem.connectionStatus = connectionStatus
        }
    }

    private fun updateImportButtonFromSelection() {
        if (isImportProgressVisible) return

        val atLeastOnceSelected = settingsList.any { it.selected }
        if (atLeastOnceSelected) {
            enableImportButton()
        } else {
            disableImportButton()
        }
    }

    fun updateCloseButtonAndImportStatusText() {
        val errorsOnly = settingsList.none { it.importStatus.isSuccess }
        if (errorsOnly) {
            showImportErrorText()
            return
        }

        val passwordsMissing = settingsList.any {
            it.importStatus == ImportStatus.IMPORT_SUCCESS_PASSWORD_REQUIRED
        }
        val authorizationRequired = settingsList.any {
            it.importStatus == ImportStatus.IMPORT_SUCCESS_AUTHORIZATION_REQUIRED
        }

        if (passwordsMissing && authorizationRequired) {
            showActionRequiredText(StatusText.IMPORT_SUCCESS_PASSWORD_AND_AUTHORIZATION_REQUIRED)
        } else if (passwordsMissing) {
            showActionRequiredText(StatusText.IMPORT_SUCCESS_PASSWORD_REQUIRED)
        } else if (authorizationRequired) {
            showActionRequiredText(StatusText.IMPORT_SUCCESS_AUTHORIZATION_REQUIRED)
        } else {
            val partialImportError = settingsList.any { it.importStatus == ImportStatus.IMPORT_FAILURE }
            if (partialImportError) {
                showPartialImportErrorText()
            } else {
                showSuccessText()
            }
        }
    }

    fun showMigrationActions() {
        isShowMigrationActions = true
        isScanQrCodeButtonVisible = true
        isPasteAccountsButtonVisible = true
        isPickAppButtonVisible = true
    }
}

sealed class SettingsListItem {
    var selected: Boolean = true
    var enabled: Boolean = true
    var importStatus: ImportStatus = ImportStatus.NOT_AVAILABLE

    class GeneralSettings : SettingsListItem()
    class Account(
        val accountIndex: Int,
        var displayName: String,
        val isAlreadySetUp: Boolean = false,
        var connectionStatus: ConnectionStatus = ConnectionStatus.NOT_CHECKED,
    ) : SettingsListItem() {
        init {
            selected = !isAlreadySetUp
        }

        val needsConnectionCheck: Boolean
            get() = importStatus == ImportStatus.IMPORT_SUCCESS && connectionStatus == ConnectionStatus.NOT_CHECKED
    }
}

enum class ImportStatus(val isSuccess: Boolean, val isActionRequired: Boolean) {
    NOT_AVAILABLE(isSuccess = false, isActionRequired = false),
    NOT_SELECTED(isSuccess = false, isActionRequired = false),
    IMPORT_SUCCESS(isSuccess = true, isActionRequired = false),
    IMPORT_SUCCESS_PASSWORD_REQUIRED(isSuccess = true, isActionRequired = true),
    IMPORT_SUCCESS_AUTHORIZATION_REQUIRED(isSuccess = true, isActionRequired = true),
    IMPORT_FAILURE(isSuccess = false, isActionRequired = false),
}

enum class ConnectionStatus {
    NOT_CHECKED,
    CHECKING,
    CONNECTED,
    FAILED,
}

enum class ButtonState {
    DISABLED,
    ENABLED,
    INVISIBLE,
    GONE,
}

enum class StatusText {
    HIDDEN,
    IMPORTING_PROGRESS,
    IMPORT_SUCCESS,
    IMPORT_SUCCESS_PASSWORD_REQUIRED,
    IMPORT_SUCCESS_AUTHORIZATION_REQUIRED,
    IMPORT_SUCCESS_PASSWORD_AND_AUTHORIZATION_REQUIRED,
    IMPORT_READ_FAILURE,
    IMPORT_PARTIAL_FAILURE,
    IMPORT_FAILURE,
}

enum class CloseButtonLabel {
    OK,
    LATER,
}

internal fun ConnectionCheckResult.toConnectionStatus(): ConnectionStatus {
    return when (this) {
        ConnectionCheckResult.Connected -> ConnectionStatus.CONNECTED
        ConnectionCheckResult.Failed -> ConnectionStatus.FAILED
        ConnectionCheckResult.NotChecked -> ConnectionStatus.NOT_CHECKED
    }
}
