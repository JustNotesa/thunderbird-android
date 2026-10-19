package app.k9mail.feature.migration.qrcode.ui.text

import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel

internal interface TransferTextContract {
    interface ViewModel : UnidirectionalViewModel<State, Event, Effect>

    data class State(
        val step: Step = Step.Preparing,
        val copied: Boolean = false,
        val revealed: Boolean = false,
    )

    sealed interface Step {
        data object Preparing : Step

        /** @param accountCount Amount of accounts in [text], so the screen can name it without showing the text. */
        data class Ready(val text: String, val accountCount: Int) : Step

        data object NoAccounts : Step
    }

    sealed interface Event {
        data object StartScreen : Event
        data object CopyClicked : Event
        data object TextVisibilityToggled : Event
        data object ScreenBackgrounded : Event
        data object DoneClicked : Event
    }

    sealed interface Effect {
        data class CopyToClipboard(val text: String) : Effect
        data object Close : Effect
    }
}
