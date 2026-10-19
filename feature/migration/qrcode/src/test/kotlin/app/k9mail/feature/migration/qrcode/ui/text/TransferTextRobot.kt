package app.k9mail.feature.migration.qrcode.ui.text

import app.k9mail.core.ui.compose.testing.mvi.MviContext
import app.k9mail.core.ui.compose.testing.mvi.MviTurbines
import app.k9mail.core.ui.compose.testing.mvi.turbinesWithInitialStateCheck
import app.k9mail.feature.migration.qrcode.transfer.DesktopPayloadWriter
import app.k9mail.feature.migration.qrcode.transfer.TransferAccount
import app.k9mail.feature.migration.qrcode.transfer.TransferAccountSource
import app.k9mail.feature.migration.qrcode.transfer.TransferIdentity
import app.k9mail.feature.migration.qrcode.transfer.TransferPayload
import app.k9mail.feature.migration.qrcode.transfer.TransferTextExporter
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.Effect
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.Event
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.State
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.Step
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.ServerSettings

private const val CREATED_AT = 1792300000L
private const val USERNAME = "username"
private const val ACCOUNT_COUNT = 1

private val ACCOUNTS = listOf(imapAccount())

/** The text the exporter hands to the screen: the payload the other device reads the accounts back from. */
internal val TRANSFER_TEXT = TransferPayload.write(
    accountsJson = DesktopPayloadWriter.write(ACCOUNTS),
    createdAt = CREATED_AT,
)

/**
 * Drives the [TransferTextViewModel] of a device that has the accounts of [accounts] set up.
 */
internal class TransferTextScreenRobot(
    private val mviContext: MviContext,
    accounts: List<TransferAccount> = ACCOUNTS,
) {
    private val viewModel = TransferTextViewModel(
        transferTextExporter = TransferTextExporter(
            accountSource = TransferAccountSource { accounts },
            clock = { CREATED_AT },
        ),
    )
    private lateinit var turbines: MviTurbines<State, Effect>

    suspend fun startScreen() {
        turbines = mviContext.turbinesWithInitialStateCheck(viewModel, State())

        // Preparing is the initial state, so no state is emitted until the accounts have been exported.
        viewModel.event(Event.StartScreen)
    }

    suspend fun assertText(text: String) {
        assertThat(turbines.awaitStateItem()).isEqualTo(readyState(text = text))
    }

    suspend fun assertStep(step: Step) {
        assertThat(turbines.awaitStateItem()).isEqualTo(State(step = step))
    }

    suspend fun userTogglesTextVisibility() {
        viewModel.event(Event.TextVisibilityToggled)
    }

    suspend fun assertTextIsRevealed() {
        assertThat(turbines.awaitStateItem()).isEqualTo(readyState(text = TRANSFER_TEXT, revealed = true))
    }

    suspend fun assertTextIsHidden() {
        assertThat(turbines.awaitStateItem()).isEqualTo(readyState(text = TRANSFER_TEXT))
    }

    suspend fun screenGoesToBackground() {
        viewModel.event(Event.ScreenBackgrounded)
    }

    suspend fun userClicksCopy() {
        viewModel.event(Event.CopyClicked)
    }

    suspend fun assertCopiedText(text: String) {
        assertThat(turbines.awaitEffectItem()).isEqualTo(Effect.CopyToClipboard(text = text))
        assertThat(turbines.awaitStateItem()).isEqualTo(readyState(text = text, copied = true))
    }

    suspend fun userClicksDone() {
        viewModel.event(Event.DoneClicked)
    }

    suspend fun assertScreenWasClosed() {
        assertThat(turbines.awaitStateItem()).isEqualTo(State())
        assertThat(turbines.awaitEffectItem()).isEqualTo(Effect.Close)
    }

    fun ensureThatAllEventsAreConsumed() {
        turbines.effectTurbine.ensureAllEventsConsumed()
        turbines.stateTurbine.ensureAllEventsConsumed()
    }
}

/** The text is hidden until the user asks for it, and the amount of accounts in it is named. */
private fun readyState(text: String, copied: Boolean = false, revealed: Boolean = false): State {
    return State(
        step = Step.Ready(text = text, accountCount = ACCOUNT_COUNT),
        copied = copied,
        revealed = revealed,
    )
}

private fun imapAccount(): TransferAccount {
    return TransferAccount(
        accountName = "My Account",
        incoming = ServerSettings(
            type = "imap",
            host = "imap.domain.example",
            port = 993,
            connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
            authenticationType = AuthType.CRAM_MD5,
            username = USERNAME,
            password = "imap-password",
            clientCertificateAlias = null,
        ),
        outgoing = ServerSettings(
            type = "smtp",
            host = "smtp.domain.example",
            port = 587,
            connectionSecurity = ConnectionSecurity.STARTTLS_REQUIRED,
            authenticationType = AuthType.PLAIN,
            username = USERNAME,
            password = "smtp-password",
            clientCertificateAlias = null,
        ),
        identities = listOf(
            TransferIdentity(emailAddress = "user@domain.example", displayName = "Firstname Lastname"),
        ),
    )
}
