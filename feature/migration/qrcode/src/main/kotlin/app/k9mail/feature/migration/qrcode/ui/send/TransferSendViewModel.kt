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
 * retries, closes the screen, or leaves the screen for good. It runs either on the network both devices are connected
 * to, or on the local-only hotspot of this device, which needs a permission the screen asks for.
 */
internal class TransferSendViewModel(
    private val transferSender: TransferSender,
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState), TransferSendContract.ViewModel {
    private var transferJob: Job? = null
    private var session: TransferSession? = null

    override fun event(event: Event) {
        when (event) {
            Event.StartScreen -> handleOneTimeEvent(event) { startTransfer(isHotspot = false) }
            Event.RetryClicked -> startTransfer(isHotspot = false)
            Event.UseHotspotClicked -> emitEffect(Effect.RequestHotspotPermission)
            is Event.HotspotPermissionResult -> startHotspotTransfer(granted = event.granted)
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

    private fun startTransfer(isHotspot: Boolean) {
        stopTransfer()
        updateStep(if (isHotspot) Step.StartingHotspot else Step.Preparing)

        transferJob = viewModelScope.launch {
            val result = if (isHotspot) transferSender.prepareHotspot() else transferSender.prepare()

            when (result) {
                is TransferSendResult.Ready -> awaitTransfer(result.session, isHotspot)

                TransferSendResult.NoWifi -> updateStep(Step.NoWifi)

                TransferSendResult.NoAccounts -> updateStep(Step.NoAccounts)

                // The hotspot is never prepared on the Wi-Fi path, it can only be prepared with prepareHotspot().
                TransferSendResult.HotspotUnsupported ->
                    updateStep(if (isHotspot) Step.HotspotUnsupported else Step.Failed)

                TransferSendResult.Failed -> updateStep(if (isHotspot) Step.HotspotFailed else Step.Failed)
            }
        }
    }

    /**
     * Starts the transfer over the local-only hotspot of this device, as soon as the user allowed the permission the
     * hotspot needs.
     */
    private fun startHotspotTransfer(granted: Boolean) {
        if (granted) {
            startTransfer(isHotspot = true)
        } else {
            updateStep(Step.HotspotPermissionDenied)
        }
    }

    private suspend fun awaitTransfer(session: TransferSession, isHotspot: Boolean) {
        this.session = session
        updateStep(Step.ShowingQrCode(qrText = session.qrText, isHotspot = isHotspot))

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
