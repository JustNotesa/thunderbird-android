package app.k9mail.feature.migration.qrcode.ui.send

import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel

internal interface TransferSendContract {
    interface ViewModel : UnidirectionalViewModel<State, Event, Effect>

    data class State(
        val step: Step = Step.Preparing,
    )

    sealed interface Step {
        data object Preparing : Step

        data object StartingHotspot : Step

        data class ShowingQrCode(val qrText: String, val isHotspot: Boolean = false) : Step

        data object Transferred : Step

        data object Expired : Step

        data object NoWifi : Step

        data object NoAccounts : Step

        data object HotspotUnsupported : Step

        data object HotspotPermissionDenied : Step

        data object HotspotFailed : Step

        data object Failed : Step
    }

    sealed interface Event {
        data object StartScreen : Event
        data object RetryClicked : Event
        data object UseHotspotClicked : Event
        data class HotspotPermissionResult(val granted: Boolean) : Event
        data object CloseClicked : Event
    }

    sealed interface Effect {
        data object Close : Effect
        data object RequestHotspotPermission : Effect
    }
}
