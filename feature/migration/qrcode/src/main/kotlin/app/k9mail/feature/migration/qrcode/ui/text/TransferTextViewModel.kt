package app.k9mail.feature.migration.qrcode.ui.text

import androidx.lifecycle.viewModelScope
import app.k9mail.feature.migration.qrcode.transfer.TransferTextExporter
import app.k9mail.feature.migration.qrcode.transfer.TransferTextResult
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.Effect
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.Event
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.State
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.Step
import kotlinx.coroutines.launch
import net.thunderbird.core.ui.contract.mvi.BaseViewModel

/**
 * View model of the screen that copies the accounts of this device into the clipboard, so the user can save them as a
 * note in a password manager.
 *
 * The text holds the accounts including their passwords, so it only lives in the state of the screen and is dropped
 * from it as soon as the user is done or leaves the screen.
 */
internal class TransferTextViewModel(
    private val transferTextExporter: TransferTextExporter,
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState), TransferTextContract.ViewModel {

    override fun event(event: Event) {
        when (event) {
            Event.StartScreen -> handleOneTimeEvent(event) { exportText() }
            Event.CopyClicked -> copyText()
            Event.DoneClicked -> handleDoneClicked()
        }
    }

    override fun onCleared() {
        clearText()

        super.onCleared()
    }

    private fun exportText() {
        viewModelScope.launch {
            val step = when (val result = transferTextExporter.exportText()) {
                is TransferTextResult.Success -> Step.Ready(text = result.text)
                TransferTextResult.NoAccounts -> Step.NoAccounts
            }

            updateState {
                it.copy(step = step, copied = false)
            }
        }
    }

    private fun copyText() {
        val step = state.value.step
        if (step !is Step.Ready) return

        emitEffect(Effect.CopyToClipboard(text = step.text))

        updateState {
            it.copy(copied = true)
        }
    }

    private fun handleDoneClicked() {
        clearText()

        emitEffect(Effect.Close)
    }

    private fun clearText() {
        updateState {
            State()
        }
    }
}
