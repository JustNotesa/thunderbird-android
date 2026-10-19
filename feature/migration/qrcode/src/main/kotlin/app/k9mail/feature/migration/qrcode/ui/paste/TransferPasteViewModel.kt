package app.k9mail.feature.migration.qrcode.ui.paste

import androidx.lifecycle.viewModelScope
import app.k9mail.feature.migration.qrcode.domain.QrCodeDomainContract.UseCase
import app.k9mail.feature.migration.qrcode.domain.entity.AccountData.Account
import app.k9mail.feature.migration.qrcode.transfer.TransferPayload
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.Effect
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.Event
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.PasteError
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.State
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.thunderbird.core.ui.contract.mvi.BaseViewModel

/**
 * View model of the screen that imports the accounts a user copied from a password manager.
 *
 * The password manager keeps the note encrypted and synchronizes it, so this screen gets the very same plaintext the
 * sending device put into the note. The text is never written to a file or logged, and the clipboard is cleared once
 * the accounts have been handed over to the import screen.
 */
internal class TransferPasteViewModel(
    private val qrCodePayloadReader: UseCase.QrCodePayloadReader,
    private val qrCodeSettingsWriter: UseCase.QrCodeSettingsWriter,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState), TransferPasteContract.ViewModel {

    override fun event(event: Event) {
        when (event) {
            is Event.TextChanged -> updateText(event.text)
            is Event.PasteFromClipboardClicked -> pasteFromClipboard(event.clipboardText)
            Event.TextVisibilityToggled -> toggleTextVisibility()
            Event.ImportClicked -> importAccounts()
            Event.CloseClicked -> emitEffect(Effect.Close)
        }
    }

    private fun updateText(text: String) {
        updateState {
            it.copy(text = text, error = null, recognizedAccountCount = recognizeAccounts(text))
        }
    }

    /**
     * Reads the text to find out whether it holds accounts of a transfer, so the field can name the amount of accounts
     * instead of showing the text itself.
     *
     * @return The amount of accounts, or `null` when the text isn't the plaintext of a transfer this app can import.
     */
    private fun recognizeAccounts(text: String): Int? {
        val content = TransferPayload.read(text.trim())
        val accountData = content?.let { qrCodePayloadReader.read(it.accountsJson) }

        return accountData?.accounts?.size
    }

    private fun toggleTextVisibility() {
        updateState {
            it.copy(revealed = !it.revealed)
        }
    }

    /**
     * Fills the field with the text of the clipboard, so the user doesn't have to paste it themselves. Nothing to
     * paste leaves the field as it is.
     */
    private fun pasteFromClipboard(clipboardText: String?) {
        if (clipboardText == null) return

        updateText(clipboardText)
    }

    private fun importAccounts() {
        val content = TransferPayload.read(state.value.text.trim())
        if (content == null) {
            showError(PasteError.NotRecognized)
            return
        }

        val accountData = qrCodePayloadReader.read(content.accountsJson)
        if (accountData == null) {
            showError(PasteError.Unsupported)
            return
        }

        writeAccounts(accountData.accounts)
    }

    private fun writeAccounts(accounts: List<Account>) {
        viewModelScope.launch {
            val contentUri = withContext(backgroundDispatcher) {
                qrCodeSettingsWriter.write(accounts)
            }

            // The accounts are in the app now, so they don't have to stay on the clipboard.
            emitEffect(Effect.ClearClipboard)

            updateState {
                it.copy(text = "", error = null, recognizedAccountCount = null, revealed = false)
            }

            emitEffect(Effect.ReturnResult(contentUri))
        }
    }

    private fun showError(error: PasteError) {
        updateState {
            it.copy(error = error)
        }
    }
}
