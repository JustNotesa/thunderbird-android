package app.k9mail.feature.migration.qrcode.ui.auth

import app.k9mail.core.ui.compose.testing.mvi.MviContext
import app.k9mail.core.ui.compose.testing.mvi.MviTurbines
import app.k9mail.core.ui.compose.testing.mvi.turbinesWithInitialStateCheck
import app.k9mail.feature.migration.qrcode.transfer.auth.AccountPasswordVerifier
import app.k9mail.feature.migration.qrcode.transfer.auth.DeviceSecurity
import app.k9mail.feature.migration.qrcode.transfer.auth.FakePasswordAccountSource
import app.k9mail.feature.migration.qrcode.transfer.auth.FakeTransferAuthAttemptStore
import app.k9mail.feature.migration.qrcode.transfer.auth.PasswordAccount
import app.k9mail.feature.migration.qrcode.transfer.auth.TransferAuthorization
import app.k9mail.feature.migration.qrcode.transfer.auth.passwordAccount
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationContract.Effect
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationContract.Error
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationContract.Event
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationContract.State
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationContract.Step
import assertk.assertThat
import assertk.assertions.isEqualTo

/**
 * Drives the [TransferAuthorizationViewModel] of a device that behaves the way the test needs.
 */
internal class TransferAuthorizationRobot(
    private val mviContext: MviContext,
    isDeviceSecure: Boolean = false,
    accounts: List<Pair<PasswordAccount, String?>> = listOf(passwordAccount() to PASSWORD),
) {
    private val clock = { NOW }
    private val passwordVerifier = AccountPasswordVerifier(
        accountSource = FakePasswordAccountSource(accounts),
        attemptStore = FakeTransferAuthAttemptStore(),
        clock = clock,
    )
    private val viewModel = TransferAuthorizationViewModel(
        transferAuthorization = TransferAuthorization(
            deviceSecurity = DeviceSecurity { isDeviceSecure },
            passwordVerifier = passwordVerifier,
        ),
        passwordVerifier = passwordVerifier,
        clock = clock,
    )
    private lateinit var turbines: MviTurbines<State, Effect>

    suspend fun startScreen() {
        turbines = mviContext.turbinesWithInitialStateCheck(viewModel, State())

        viewModel.event(Event.StartScreen)
    }

    suspend fun assertAuthenticationIsRequested() {
        assertThat(turbines.awaitStateItem()).isEqualTo(State(step = Step.Authenticating))
        assertThat(turbines.awaitEffectItem()).isEqualTo(Effect.Authenticate)
    }

    fun userAuthenticatesSuccessfully() {
        viewModel.event(Event.AuthenticationSucceeded)
    }

    fun userFailsAuthentication() {
        viewModel.event(Event.AuthenticationFailed)
    }

    suspend fun assertPasswordFormIsShown() {
        assertThat(turbines.awaitStateItem()).isEqualTo(passwordFormState())
    }

    suspend fun userEntersPassword(password: String) {
        viewModel.event(Event.PasswordChanged(password))

        assertThat(turbines.awaitStateItem()).isEqualTo(passwordFormState(password = password))
    }

    fun userClicksContinue() {
        viewModel.event(Event.ContinueClicked)
    }

    suspend fun assertWrongPassword(remainingAttempts: Int) {
        assertThat(turbines.awaitStateItem()).isEqualTo(
            passwordFormState(error = Error.WrongPassword(remainingAttempts = remainingAttempts)),
        )
    }

    suspend fun assertAccountIsLockedOut() {
        assertThat(turbines.awaitStateItem()).isEqualTo(
            passwordFormState(error = Error.LockedOut(remainingMillis = LOCK_OUT_MILLIS)),
        )
    }

    suspend fun assertTransferIsAuthorized() {
        assertThat(turbines.awaitStateItem()).isEqualTo(State(step = Step.Authorized))
    }

    suspend fun assertTransferIsDenied() {
        assertThat(turbines.awaitStateItem()).isEqualTo(State(step = Step.Denied))
    }

    fun userCancels() {
        viewModel.event(Event.CancelClicked)
    }

    suspend fun assertScreenWasClosed() {
        assertThat(turbines.awaitEffectItem()).isEqualTo(Effect.Close)
    }

    fun ensureThatAllEventsAreConsumed() {
        turbines.effectTurbine.ensureAllEventsConsumed()
        turbines.stateTurbine.ensureAllEventsConsumed()
    }

    private fun passwordFormState(password: String = "", error: Error? = null): State {
        return State(
            step = Step.EnteringPassword,
            accounts = listOf(passwordAccount()),
            selectedAccountId = ACCOUNT_ID,
            password = password,
            error = error,
        )
    }
}
