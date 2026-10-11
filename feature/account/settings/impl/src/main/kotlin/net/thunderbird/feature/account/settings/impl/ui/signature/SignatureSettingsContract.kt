package net.thunderbird.feature.account.settings.impl.ui.signature

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.core.android.account.AccountSignature
import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange

internal interface SignatureSettingsContract {
    interface ViewModel : UnidirectionalViewModel<State, Event, Effect>

    @Stable
    data class State(
        val subtitle: String? = null,
        val signatures: ImmutableList<AccountSignature> = persistentListOf(),
        val defaultSignatureId: String? = null,
        val editor: Editor? = null,
        val signaturePendingDeletion: AccountSignature? = null,
        val error: Error? = null,
    )

    /**
     * The signature being added or edited. [signatureId] is `null` while adding a new one.
     *
     * @param text The text as the user typed it in [EditorMode.Formatted], the HTML in [EditorMode.Code].
     * @param ranges The formatted parts of [text], only used in [EditorMode.Formatted].
     * @param previewHtml The sanitized HTML the signature is sent as, only set in [EditorMode.Code].
     * @param isConfirmingFormattedMode Whether the user is being asked to confirm a switch to
     * [EditorMode.Formatted] that removes HTML the formatted editor can't show.
     */
    data class Editor(
        val signatureId: String? = null,
        val name: String = "",
        val mode: EditorMode = EditorMode.Formatted,
        val text: String = "",
        val ranges: ImmutableList<FormattedRange> = persistentListOf(),
        val previewHtml: String? = null,
        val isConfirmingFormattedMode: Boolean = false,
    ) {
        val canSave: Boolean
            get() = name.isNotBlank() && text.isNotBlank()
    }

    enum class EditorMode {
        /**
         * The user writes the signature as it looks and formats it with buttons.
         */
        Formatted,

        /**
         * The user writes the HTML of the signature.
         */
        Code,
    }

    enum class Error {
        LoadFailed,
        SaveFailed,
        DeleteFailed,
        SetDefaultFailed,
    }

    sealed interface Event {
        data object OnBackPressed : Event
        data object OnAddClick : Event
        data class OnSignatureClick(val signatureId: String) : Event
        data class OnNameChange(val name: String) : EditorEvent
        data class OnTextChange(val text: String) : EditorEvent
        data class OnFormattedTextChange(val text: String, val ranges: List<FormattedRange>) : EditorEvent
        data class OnModeChange(val mode: EditorMode) : EditorEvent
        data object OnFormattedModeConfirm : EditorEvent
        data object OnFormattedModeDismiss : EditorEvent
        data object OnSaveClick : Event
        data class OnDeleteClick(val signatureId: String) : Event
        data object OnDeleteConfirm : Event
        data object OnDeleteDismiss : Event
        data class OnDefaultClick(val signatureId: String) : Event
        data object OnErrorDismiss : Event
    }

    /**
     * An event that changes the signature being edited.
     */
    sealed interface EditorEvent : Event

    sealed interface Effect {
        data object NavigateBack : Effect
    }
}
