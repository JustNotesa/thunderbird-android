package app.k9mail.feature.migration.qrcode.ui.auth

import androidx.lifecycle.viewModelScope
import app.k9mail.feature.migration.qrcode.transfer.auth.AccountPasswordVerifier
import app.k9mail.feature.migration.qrcode.transfer.auth.PasswordAccount
import app.k9mail.feature.migration.qrcode.transfer.auth.TransferAuthorization
import app.k9mail.feature.migration.qrcode.transfer.auth.TransferAuthorizationMode
import app.k9mail.feature.migration.qrcode.transfer.auth.VerifyResult
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationContract.Effect
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationContract.Error
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationContract.Event
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationContract.State
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationContract.Step
import kotlinx.coroutines.launch
import net.thunderbird.core.ui.contract.mvi.BaseViewModel

/**
 * View model of the content that asks the user for permission before the accounts of this device may leave it.
 *
 * A device with a screen lock is authorized by device authentication. Without a screen lock the password of one of the
 * accounts of this device is needed, which only lives in the state while it is typed and is handed to the verifier as
 * a [CharArray], that is wiped before it returns.
 */
internal class TransferAuthorizationViewModel(
    private val transferAuthorization: TransferAuthorization,
    private val passwordVerifier: AccountPasswordVerifier,
    private val clock: () -> Long = System::currentTimeMillis,
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState), TransferAuthorizationContract.ViewModel {

    override fun event(event: Event) {
        when (event) {
            Event.StartScreen -> handleOneTimeEvent(event) { determineMode() }
            Event.AuthenticationSucceeded -> authorize()
            Event.AuthenticationFailed -> deny()
            is Event.AccountSelected -> updateState { it.copy(selectedAccountId = event.accountId, error = null) }
            is Event.PasswordChanged -> onPasswordChanged(password = event.password)
            Event.ContinueClicked -> verifyPassword()
            Event.LockOutEnded -> updateState { it.copy(error = null) }
            Event.CancelClicked, Event.CloseClicked -> emitEffect(Effect.Close)
        }
    }

    override fun onCleared() {
        updateState { it.copy(password = "") }

        super.onCleared()
    }

    private fun determineMode() {
        viewModelScope.launch {
            when (val mode = transferAuthorization.mode()) {
                TransferAuthorizationMode.DeviceAuthentication -> {
                    updateState { it.copy(step = Step.Authenticating) }

                    emitEffect(Effect.Authenticate)
                }

                is TransferAuthorizationMode.AccountPassword -> showPasswordForm(accounts = mode.accounts)

                TransferAuthorizationMode.NotRequired -> authorize()
            }
        }
    }

    private fun showPasswordForm(accounts: List<PasswordAccount>) {
        updateState {
            it.copy(
                step = Step.EnteringPassword,
                accounts = accounts,
                selectedAccountId = accounts.firstOrNull()?.id.orEmpty(),
            )
        }
    }

    private fun authorize() {
        updateState { State(step = Step.Authorized) }
    }

    private fun deny() {
        updateState { State(step = Step.Denied) }
    }

    private fun onPasswordChanged(password: String) {
        updateState {
            val error = it.error
            // A wrong password is gone as soon as the next one is typed, a lock out holds until it ended.
            it.copy(password = password, error = error as? Error.LockedOut)
        }
    }

    private fun verifyPassword() {
        val password = state.value.password
        if (password.isEmpty()) return

        val accountId = state.value.selectedAccountId
        viewModelScope.launch {
            val result = passwordVerifier.verify(accountId = accountId, password = password.toCharArray())

            updateState { it.withResult(result) }
        }
    }

    private fun State.withResult(result: VerifyResult): State {
        return when (result) {
            VerifyResult.Correct -> State(step = Step.Authorized)

            is VerifyResult.Wrong ->
                copy(password = "", error = Error.WrongPassword(remainingAttempts = result.remainingAttempts))

            is VerifyResult.LockedOut ->
                copy(password = "", error = Error.LockedOut(remainingMillis = result.untilMillis - clock()))
        }
    }
}
