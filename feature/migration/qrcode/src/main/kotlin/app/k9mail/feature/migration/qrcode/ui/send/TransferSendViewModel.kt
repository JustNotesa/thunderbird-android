package app.k9mail.feature.migration.qrcode.ui.send

import androidx.lifecycle.viewModelScope
import app.k9mail.feature.migration.qrcode.transfer.LanTransferServerResult
import app.k9mail.feature.migration.qrcode.transfer.TransferSendResult
import app.k9mail.feature.migration.qrcode.transfer.TransferSender
import app.k9mail.feature.migration.qrcode.transfer.TransferSession
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendContract.Effect
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendContract.Event
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendContract.State
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendContract.Step
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.thunderbird.core.ui.contract.mvi.BaseViewModel

/**
 * View model of the screen that sends the accounts of this device to another device.
 *
 * The transfer is only served while the screen shows the QR code, so a running session is stopped when the user
 * retries, closes the screen, or leaves the screen for good.
 */
internal class TransferSendViewModel(
    private val transferSender: TransferSender,
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState), TransferSendContract.ViewModel {
    private var transferJob: Job? = null
    private var session: TransferSession? = null

    override fun event(event: Event) {
        when (event) {
            Event.StartScreen -> handleOneTimeEvent(event, ::startTransfer)
            Event.RetryClicked -> startTransfer()
            Event.CloseClicked -> handleCloseClicked()
        }
    }

    override fun onCleared() {
        stopTransfer()

        super.onCleared()
    }

    /**
     * Waits until the transfer has stopped, so a test can tear down its dispatchers only after the view model stopped
     * using them. Has no effect when no transfer was started.
     *
     * Only used by tests, in the app the transfer is stopped by [onCleared].
     */
    internal suspend fun awaitIdle() {
        transferJob?.join()
    }

    private fun startTransfer() {
        stopTransfer()
        updateStep(Step.Preparing)

        transferJob = viewModelScope.launch {
            when (val result = transferSender.prepare()) {
                is TransferSendResult.Ready -> awaitTransfer(result.session)

                TransferSendResult.NoWifi -> updateStep(Step.NoWifi)

                TransferSendResult.NoAccounts -> updateStep(Step.NoAccounts)

                // The hotspot isn't used on this path, it can only be prepared with prepareHotspot().
                TransferSendResult.HotspotUnsupported -> updateStep(Step.Failed)

                TransferSendResult.Failed -> updateStep(Step.Failed)
            }
        }
    }

    private suspend fun awaitTransfer(session: TransferSession) {
        this.session = session
        updateStep(Step.ShowingQrCode(qrText = session.qrText))

        updateStep(toStep(session.awaitTransfer()))
    }

    private fun toStep(result: LanTransferServerResult): Step {
        return when (result) {
            LanTransferServerResult.Transferred -> Step.Transferred
            LanTransferServerResult.Expired -> Step.Expired
            LanTransferServerResult.Failed -> Step.Failed
        }
    }

    private fun handleCloseClicked() {
        stopTransfer()

        emitEffect(Effect.Close)
    }

    private fun stopTransfer() {
        // The stopped job is kept, so awaitIdle() can wait until the transfer really ended.
        transferJob?.cancel()
        session?.stop()
        session = null
    }

    private fun updateStep(step: Step) {
        updateState {
            it.copy(step = step)
        }
    }
}
