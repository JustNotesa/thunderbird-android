package app.k9mail.feature.migration.qrcode.ui.paste

import androidx.core.net.toUri
import app.k9mail.core.ui.compose.testing.mvi.MviContext
import app.k9mail.core.ui.compose.testing.mvi.MviTurbines
import app.k9mail.core.ui.compose.testing.mvi.turbinesWithInitialStateCheck
import app.k9mail.feature.migration.qrcode.domain.usecase.QrCodePayloadReader
import app.k9mail.feature.migration.qrcode.payload.FakeDeletePolicyProvider
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadAdapter
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadMapper
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadParser
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadValidator
import app.k9mail.feature.migration.qrcode.transfer.TransferPayload
import app.k9mail.feature.migration.qrcode.ui.FakeQrCodeSettingsWriter
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.Effect
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.Event
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.PasteError
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.State
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.Dispatchers

private const val CREATED_AT = 1792300000L
private const val ACCOUNT_COUNT = 1

private const val ACCOUNTS_JSON = """[1,[1,1],""" +
    """[0,"imap.domain.example",993,3,2,"username","My Account"],""" +
    """[[[0,"smtp.domain.example",587,2,1,"username"],""" +
    """["user@domain.example","Firstname Lastname"]]]]"""

private const val NEWER_ACCOUNTS_JSON = """[2,[1,1],""" +
    """[0,"imap.domain.example",993,3,2,"username","My Account"],""" +
    """[[[0,"smtp.domain.example",587,2,1,"username"],""" +
    """["user@domain.example","Firstname Lastname"]]]]"""

internal val TRANSFER_TEXT = TransferPayload.write(accountsJson = ACCOUNTS_JSON, createdAt = CREATED_AT)
internal val TRANSFER_TEXT_OF_NEWER_ACCOUNTS =
    TransferPayload.write(accountsJson = NEWER_ACCOUNTS_JSON, createdAt = CREATED_AT)

private val WRITTEN_SETTINGS_URI = "content://provider".toUri()

/**
 * Drives the [TransferPasteViewModel] of a device that imports the accounts of a note of a password manager.
 */
internal class TransferPasteScreenRobot(private val mviContext: MviContext) {
    private val qrCodeSettingsWriter = FakeQrCodeSettingsWriter()
    private val viewModel = TransferPasteViewModel(
        qrCodePayloadReader = QrCodePayloadReader(
            parser = QrCodePayloadParser(QrCodePayloadAdapter()),
            mapper = QrCodePayloadMapper(
                qrCodePayloadValidator = QrCodePayloadValidator(),
                deletePolicyProvider = FakeDeletePolicyProvider(),
            ),
        ),
        qrCodeSettingsWriter = qrCodeSettingsWriter,
        backgroundDispatcher = Dispatchers.Unconfined,
    )
    private lateinit var turbines: MviTurbines<State, Effect>
    private var pastedText = ""

    suspend fun startScreen() {
        turbines = mviContext.turbinesWithInitialStateCheck(viewModel, State())
    }

    suspend fun userPastesText(text: String) {
        pastedText = text

        viewModel.event(Event.TextChanged(text))

        assertThat(turbines.awaitStateItem()).isEqualTo(pastedState(text = text))
    }

    suspend fun userClicksPasteFromClipboard(clipboardText: String?) {
        viewModel.event(Event.PasteFromClipboardClicked(clipboardText))

        if (clipboardText != null) {
            assertThat(turbines.awaitStateItem()).isEqualTo(pastedState(text = clipboardText))
        }
    }

    suspend fun userTogglesTextVisibility() {
        viewModel.event(Event.TextVisibilityToggled)
    }

    suspend fun assertTextIsRevealed() {
        assertThat(turbines.awaitStateItem()).isEqualTo(pastedState(text = TRANSFER_TEXT, revealed = true))
    }

    suspend fun userClicksImport() {
        viewModel.event(Event.ImportClicked)
    }

    suspend fun assertClipboardWasCleared() {
        assertThat(turbines.awaitEffectItem()).isEqualTo(Effect.ClearClipboard)
    }

    suspend fun assertImportResult(expectedNumberOfAccounts: Int) {
        assertThat(turbines.awaitEffectItem()).isEqualTo(Effect.ReturnResult(contentUri = WRITTEN_SETTINGS_URI))
        assertThat(turbines.awaitStateItem()).isEqualTo(State())
        assertThat(qrCodeSettingsWriter.arguments).isNotNull().hasSize(expectedNumberOfAccounts)
    }

    suspend fun assertError(error: PasteError) {
        val state = pastedState(text = pastedText).copy(error = error)

        assertThat(turbines.awaitStateItem()).isEqualTo(state)
    }

    fun assertNothingWasImported() {
        assertThat(qrCodeSettingsWriter.arguments).isNull()
    }

    fun ensureThatAllEventsAreConsumed() {
        turbines.effectTurbine.ensureAllEventsConsumed()
        turbines.stateTurbine.ensureAllEventsConsumed()
    }
}

/** The pasted text is hidden, but the amount of accounts in it is read, so the screen can name it. */
private fun pastedState(text: String, revealed: Boolean = false): State {
    val accountCount = if (text.contains(ACCOUNTS_JSON)) ACCOUNT_COUNT else null

    return State(text = text, recognizedAccountCount = accountCount, revealed = revealed)
}
