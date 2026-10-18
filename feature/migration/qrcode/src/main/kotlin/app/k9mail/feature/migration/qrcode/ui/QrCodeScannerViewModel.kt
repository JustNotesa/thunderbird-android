package app.k9mail.feature.migration.qrcode.ui

import androidx.lifecycle.viewModelScope
import app.k9mail.feature.migration.qrcode.domain.QrCodeDomainContract.UseCase
import app.k9mail.feature.migration.qrcode.domain.entity.AccountData
import app.k9mail.feature.migration.qrcode.domain.entity.AccountData.Account
import app.k9mail.feature.migration.qrcode.domain.usecase.QrCodeImageAnalysisProvider
import app.k9mail.feature.migration.qrcode.transfer.TransferQrCode
import app.k9mail.feature.migration.qrcode.transfer.TransferReceiveResult
import app.k9mail.feature.migration.qrcode.transfer.TransferReceiver
import app.k9mail.feature.migration.qrcode.ui.QrCodeScannerContract.DisplayText
import app.k9mail.feature.migration.qrcode.ui.QrCodeScannerContract.Effect
import app.k9mail.feature.migration.qrcode.ui.QrCodeScannerContract.Event
import app.k9mail.feature.migration.qrcode.ui.QrCodeScannerContract.State
import app.k9mail.feature.migration.qrcode.ui.QrCodeScannerContract.TransferFailureReason
import app.k9mail.feature.migration.qrcode.ui.QrCodeScannerContract.UiPermissionState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

private typealias PayloadCallback = (String) -> Unit
private typealias CameraUseCaseProviderFactory = (PayloadCallback) -> UseCase.CameraUseCasesProvider

@Suppress("TooManyFunctions")
internal class QrCodeScannerViewModel(
    private val qrCodePayloadReader: UseCase.QrCodePayloadReader,
    private val qrCodeSettingsWriter: UseCase.QrCodeSettingsWriter,
    private val transferReceiver: TransferReceiver,
    createCameraUseCaseProvider: CameraUseCaseProviderFactory = { callback -> QrCodeImageAnalysisProvider(callback) },
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState), QrCodeScannerContract.ViewModel {
    private val supportedPayloadHashes = mutableSetOf<ByteString>()
    private val unsupportedPayloadHashes = ArrayDeque<ByteString>()
    private val accountDataList = mutableListOf<AccountData>()

    private var scannedCount = 0
    private var totalCount = 0

    @Volatile
    private var isReceivingTransfer = false

    override val cameraUseCasesProvider: UseCase.CameraUseCasesProvider =
        createCameraUseCaseProvider(::handleQrCodeScanned)

    override fun event(event: Event) {
        when (event) {
            Event.StartScreen -> handleOneTimeEvent(event, ::handleStartScreen)
            is Event.CameraPermissionResult -> handleCameraPermissionResult(event.success)
            Event.GoToSettingsClicked -> handleGoToSettingsClicked()
            Event.ReturnedFromAppInfoScreen -> handleReturnedFromAndroidSettings()
            Event.DoneClicked -> handleDoneClicked()
        }
    }

    private fun handleStartScreen() {
        requestCameraPermission()
    }

    private fun handleCameraPermissionResult(success: Boolean) {
        updateState {
            it.copy(cameraPermissionState = if (success) UiPermissionState.Granted else UiPermissionState.Denied)
        }
    }

    private fun handleGoToSettingsClicked() {
        emitEffect(Effect.GoToAppInfoScreen)

        viewModelScope.launch {
            // Delay updating the UI to make sure Android's app settings screen is active and our activity is paused.
            // We want to prevent QrCodeScannerContent triggering the permission dialog again before the user had a
            // chance to enter Android's app settings screen.
            delay(APP_SETTINGS_DELAY)

            updateState {
                it.copy(cameraPermissionState = UiPermissionState.Waiting)
            }
        }
    }

    private fun handleReturnedFromAndroidSettings() {
        updateState {
            it.copy(cameraPermissionState = UiPermissionState.Unknown)
        }

        requestCameraPermission()
    }

    private fun requestCameraPermission() {
        emitEffect(Effect.RequestCameraPermission)
    }

    private fun handleQrCodeScanned(payload: String) {
        val payloadHash = payload.sha1
        if (payloadHash in supportedPayloadHashes || payloadHash in unsupportedPayloadHashes) {
            // This QR code has already been scanned. Skip further processing.
            return
        }

        if (payload.startsWith(TRANSFER_QR_CODE_PREFIX)) {
            handleTransferQrCode(payload = payload, payloadHash = payloadHash)
            return
        }

        val accountData = qrCodePayloadReader.read(payload)
        if (accountData != null) {
            handleSupportedPayload(accountData)
            supportedPayloadHashes.add(payloadHash)
        } else {
            handleUnsupportedPayload(payloadHash)
        }
    }

    private fun handleUnsupportedPayload(payloadHash: ByteString) {
        if (unsupportedPayloadHashes.size > MAX_NUMBER_OF_UNSUPPORTED_PAYLOADS) {
            unsupportedPayloadHashes.removeFirst()
        }
        unsupportedPayloadHashes.add(payloadHash)

        updateState {
            it.copy(displayText = DisplayText.UnsupportedQrCode)
        }
    }

    private fun handleTransferQrCode(payload: String, payloadHash: ByteString) {
        if (isReceivingTransfer) {
            // A transfer is already being received. Its key material can only be used for a single transfer.
            return
        }

        val qrCode = TransferQrCode.parse(payload)
        if (qrCode == null) {
            handleUnsupportedPayload(payloadHash)
            return
        }

        receiveTransfer(qrCode = qrCode, payloadHash = payloadHash)
    }

    private fun receiveTransfer(qrCode: TransferQrCode, payloadHash: ByteString) {
        isReceivingTransfer = true

        updateState {
            it.copy(displayText = DisplayText.ReceivingTransfer)
        }

        viewModelScope.launch {
            val result = withContext(backgroundDispatcher) {
                transferReceiver.receive(qrCode)
            }

            isReceivingTransfer = false

            if (result is TransferReceiveResult.Success) {
                handleReceivedAccounts(accountsJson = result.accountsJson, payloadHash = payloadHash)
            } else {
                showTransferFailure(result)
            }
        }
    }

    private fun handleReceivedAccounts(accountsJson: String, payloadHash: ByteString) {
        val accountData = qrCodePayloadReader.read(accountsJson)
        if (accountData != null) {
            supportedPayloadHashes.add(payloadHash)
            handleSupportedPayload(accountData)
        } else {
            // The transfer held data this version of the app doesn't understand.
            updateState {
                it.copy(displayText = DisplayText.TransferFailed(TransferFailureReason.Unsupported))
            }
        }
    }

    private fun showTransferFailure(result: TransferReceiveResult) {
        val reason = when (result) {
            TransferReceiveResult.Unreachable, TransferReceiveResult.TransferFailed -> TransferFailureReason.Unreachable
            TransferReceiveResult.Corrupted -> TransferFailureReason.Corrupted
            else -> TransferFailureReason.Unsupported
        }

        // Don't remember the hash of the QR code so the user can scan it again.
        updateState {
            it.copy(displayText = DisplayText.TransferFailed(reason))
        }
    }

    private fun handleSupportedPayload(accountData: AccountData) {
        if (accountData.sequenceEnd == totalCount) {
            accountDataList.add(accountData)
            scannedCount = accountDataList.size
        } else {
            // Total QR code count doesn't match previous value. The user has probably started over.

            supportedPayloadHashes.clear()
            accountDataList.clear()
            accountDataList.add(accountData)

            scannedCount = 1
            totalCount = accountData.sequenceEnd
        }

        updateState {
            it.copy(displayText = DisplayText.ProgressText(scannedCount, totalCount))
        }

        if (accountDataList.size == accountData.sequenceEnd) {
            startAccountImport()
        }
    }

    private fun handleDoneClicked() {
        startAccountImport()
    }

    private fun startAccountImport() {
        val accounts = mergeAccounts(accountDataList)
        if (accounts.isEmpty()) {
            emitEffect(Effect.Cancel)
            return
        }

        viewModelScope.launch {
            val contentUri = withContext(backgroundDispatcher) {
                qrCodeSettingsWriter.write(accounts)
            }

            emitEffect(Effect.ReturnResult(contentUri))
        }
    }

    private fun mergeAccounts(accountDataList: List<AccountData>): List<Account> {
        return accountDataList.flatMap { it.accounts }
    }

    private val String.sha1: ByteString
        get() = encodeUtf8().sha1()
}

private const val APP_SETTINGS_DELAY = 100L
private const val MAX_NUMBER_OF_UNSUPPORTED_PAYLOADS = 5
private const val TRANSFER_QR_CODE_PREFIX = "TBX1:"
