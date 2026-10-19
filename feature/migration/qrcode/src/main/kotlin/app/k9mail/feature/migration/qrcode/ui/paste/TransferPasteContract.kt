package app.k9mail.feature.migration.qrcode.ui.paste

import android.net.Uri
import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel

internal interface TransferPasteContract {
    interface ViewModel : UnidirectionalViewModel<State, Event, Effect>

    data class State(
        val text: String = "",
        val error: PasteError? = null,
        val recognizedAccountCount: Int? = null,
        val revealed: Boolean = false,
    )

    enum class PasteError {
        NotRecognized,
        Unsupported,
    }

    sealed interface Event {
        data class TextChanged(val text: String) : Event
        data class PasteFromClipboardClicked(val clipboardText: String?) : Event
        data object TextVisibilityToggled : Event
        data object ImportClicked : Event
        data object CloseClicked : Event
    }

    sealed interface Effect {
        data class ReturnResult(val contentUri: Uri) : Effect
        data object Close : Effect
        data object ClearClipboard : Effect
    }
}
