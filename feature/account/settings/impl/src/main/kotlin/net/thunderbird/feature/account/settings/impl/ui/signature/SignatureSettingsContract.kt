package net.thunderbird.feature.account.settings.impl.ui.signature

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.core.android.account.AccountSignature
import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel

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
     */
    data class Editor(
        val signatureId: String? = null,
        val name: String = "",
        val text: String = "",
        val isHtml: Boolean = false,
    ) {
        val canSave: Boolean
            get() = name.isNotBlank() && text.isNotBlank()
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
        data class OnNameChange(val name: String) : Event
        data class OnTextChange(val text: String) : Event
        data class OnIsHtmlChange(val isHtml: Boolean) : Event
        data object OnSaveClick : Event
        data class OnDeleteClick(val signatureId: String) : Event
        data object OnDeleteConfirm : Event
        data object OnDeleteDismiss : Event
        data class OnDefaultClick(val signatureId: String) : Event
        data object OnErrorDismiss : Event
    }

    sealed interface Effect {
        data object NavigateBack : Effect
    }
}
