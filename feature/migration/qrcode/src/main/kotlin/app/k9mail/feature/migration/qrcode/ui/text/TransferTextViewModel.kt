package app.k9mail.feature.migration.qrcode.ui.text

import androidx.lifecycle.viewModelScope
import app.k9mail.feature.migration.qrcode.transfer.TransferPayload
import app.k9mail.feature.migration.qrcode.transfer.TransferTextExporter
import app.k9mail.feature.migration.qrcode.transfer.TransferTextResult
import app.k9mail.feature.migration.qrcode.transfer.countTransferAccounts
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
            Event.TextVisibilityToggled -> toggleTextVisibility()
            Event.ScreenBackgrounded -> hideText()
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
                is TransferTextResult.Success -> Step.Ready(
                    text = result.text,
                    accountCount = accountCountOf(text = result.text),
                )

                TransferTextResult.NoAccounts -> Step.NoAccounts
            }

            updateState {
                it.copy(step = step, copied = false)
            }
        }
    }

    /** The amount of accounts in the exported text, so the screen can name it without showing the text. */
    private fun accountCountOf(text: String): Int {
        val content = TransferPayload.read(text)

        return content?.let { countTransferAccounts(it.accountsJson) } ?: 0
    }

    private fun copyText() {
        val step = state.value.step
        if (step !is Step.Ready) return

        emitEffect(Effect.CopyToClipboard(text = step.text))

        // The accounts are in the clipboard now, so they aren't shown on the screen any more.
        updateState {
            it.copy(copied = true, revealed = false)
        }
    }

    private fun toggleTextVisibility() {
        updateState {
            it.copy(revealed = !it.revealed)
        }
    }

    /** Hides the accounts, so they don't stay on the screen while another one is in front of it. */
    private fun hideText() {
        updateState {
            it.copy(revealed = false)
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
