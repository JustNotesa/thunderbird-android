package app.k9mail.feature.migration.qrcode.ui.auth

import app.k9mail.feature.migration.qrcode.transfer.auth.PasswordAccount
import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel

/**
 * Contract of the content that asks the user for permission before the accounts of this device may leave it.
 */
internal interface TransferAuthorizationContract {
    interface ViewModel : UnidirectionalViewModel<State, Event, Effect>

    data class State(
        val step: Step = Step.Checking,
        val accounts: List<PasswordAccount> = emptyList(),
        val selectedAccountId: String = "",
        val password: String = "",
        val error: Error? = null,
    )

    /**
     * What the screen is waiting for.
     */
    sealed interface Step {
        /** How this transfer has to be authorized is still being determined. */
        data object Checking : Step

        /** The device is protected by a screen lock and the device authentication is running. */
        data object Authenticating : Step

        /** The device has no screen lock, the password of one of [State.accounts] is needed. */
        data object EnteringPassword : Step

        /** The user confirmed it's them, so the accounts may leave this device. */
        data object Authorized : Step

        /** The user didn't confirm, so the accounts stay on this device. */
        data object Denied : Step
    }

    /**
     * Why the entered password wasn't accepted.
     */
    sealed interface Error {
        /**
         * The password was wrong, there are [remainingAttempts] attempts left.
         */
        data class WrongPassword(val remainingAttempts: Int) : Error

        /**
         * There were too many wrong passwords, the next attempt is possible in [remainingMillis].
         */
        data class LockedOut(val remainingMillis: Long) : Error
    }

    sealed interface Event {
        data object StartScreen : Event
        data object AuthenticationSucceeded : Event
        data object AuthenticationFailed : Event
        data class AccountSelected(val accountId: String) : Event
        data class PasswordChanged(val password: String) : Event
        data object ContinueClicked : Event
        data object LockOutEnded : Event
        data object CancelClicked : Event
        data object CloseClicked : Event
    }

    sealed interface Effect {
        /** The screen is asked to run the device authentication and to report how it ended. */
        data object Authenticate : Effect

        /** There is nothing left to show, so the screen is asked to close. */
        data object Close : Effect
    }
}
