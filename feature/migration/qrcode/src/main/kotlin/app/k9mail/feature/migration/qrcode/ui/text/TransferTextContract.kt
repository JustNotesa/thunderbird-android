package app.k9mail.feature.migration.qrcode.ui.text

import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel

internal interface TransferTextContract {
    interface ViewModel : UnidirectionalViewModel<State, Event, Effect>

    data class State(
        val step: Step = Step.Preparing,
        val copied: Boolean = false,
    )

    sealed interface Step {
        data object Preparing : Step

        data class Ready(val text: String) : Step

        data object NoAccounts : Step
    }

    sealed interface Event {
        data object StartScreen : Event
        data object CopyClicked : Event
        data object DoneClicked : Event
    }

    sealed interface Effect {
        data class CopyToClipboard(val text: String) : Effect
        data object Close : Effect
    }
}
