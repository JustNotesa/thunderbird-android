package net.thunderbird.feature.account.settings.impl.ui.signature

import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.thunderbird.components.core.logging.Logger
import net.thunderbird.components.core.outcome.handle
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.AccountSettingError
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UpdateSignaturesCommand
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UseCase
import net.thunderbird.feature.account.settings.impl.domain.AccountSignatures
import net.thunderbird.feature.account.settings.impl.domain.toAccountSignatures
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.Editor
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.EditorEvent
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.Effect
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.Error
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.Event
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.State
import net.thunderbird.feature.mail.message.composer.richtext.RichTextHtmlConverter
import net.thunderbird.feature.mail.message.composer.signature.HtmlSignatureSanitizer

private const val TAG = "SignatureSettingsViewModel"

internal class SignatureSettingsViewModel(
    private val accountId: AccountId,
    private val getAccountName: UseCase.GetAccountName,
    private val getLegacyAccount: UseCase.GetLegacyAccount,
    private val updateSignatures: UseCase.UpdateSignatures,
    private val logger: Logger,
    htmlSignatureSanitizer: HtmlSignatureSanitizer,
    richTextHtmlConverter: RichTextHtmlConverter,
    private val initialSignatureId: String? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val signatureIdGenerator: () -> String = { UUID.randomUUID().toString() },
    initialState: State = State(),
) : BaseViewModel<State, Event, Effect>(initialState), SignatureSettingsContract.ViewModel {

    private val editorMapper = SignatureEditorMapper(htmlSignatureSanitizer, richTextHtmlConverter)

    /**
     * `true` while editing the signature the screen was opened for. Leaving that editor leaves the screen.
     */
    private var isEditingInitialSignature = false

    init {
        observeAccountName()
        loadSignatures()
    }

    override fun event(event: Event) {
        when (event) {
            Event.OnBackPressed -> onBackPressed()

            Event.OnAddClick -> updateState { it.copy(editor = Editor()) }

            is Event.OnSignatureClick -> editSignature(event.signatureId)

            is EditorEvent -> updateState { state ->
                state.copy(editor = state.editor?.let { editorMapper.onEvent(it, event) })
            }

            Event.OnSaveClick -> saveSignature()

            is Event.OnDeleteClick -> updateState { state ->
                state.copy(signaturePendingDeletion = state.signatures.find { it.id == event.signatureId })
            }

            Event.OnDeleteConfirm -> deleteSignature()

            Event.OnDeleteDismiss -> updateState { it.copy(signaturePendingDeletion = null) }

            is Event.OnDefaultClick -> toggleDefaultSignature(event.signatureId)

            Event.OnErrorDismiss -> updateState { it.copy(error = null) }
        }
    }

    private fun onBackPressed() {
        if (state.value.editor != null && !isEditingInitialSignature) {
            updateState { it.copy(editor = null) }
        } else {
            emitEffect(Effect.NavigateBack)
        }
    }

    private fun editSignature(signatureId: String) {
        val signature = state.value.signatures.find { it.id == signatureId } ?: return
        updateState { it.copy(editor = editorMapper.toEditor(signature)) }
    }

    private fun saveSignature() {
        val editor = state.value.editor?.takeIf { it.canSave } ?: return
        val signature = editorMapper.toSignature(editor, editor.signatureId ?: signatureIdGenerator())

        viewModelScope.launch(dispatcher) {
            updateSignatures(accountId, UpdateSignaturesCommand.Save(signature)).handle(
                onSuccess = { signatures ->
                    if (isEditingInitialSignature) {
                        emitEffect(Effect.NavigateBack)
                    } else {
                        updateState { it.withSignatures(signatures).copy(editor = null) }
                    }
                },
                onFailure = { handleError(it, Error.SaveFailed) },
            )
        }
    }

    private fun deleteSignature() {
        val signature = state.value.signaturePendingDeletion ?: return
        updateState { it.copy(signaturePendingDeletion = null) }

        viewModelScope.launch(dispatcher) {
            updateSignatures(accountId, UpdateSignaturesCommand.Delete(signature.id)).handle(
                onSuccess = { signatures -> updateState { it.withSignatures(signatures) } },
                onFailure = { handleError(it, Error.DeleteFailed) },
            )
        }
    }

    private fun toggleDefaultSignature(signatureId: String) {
        val newDefaultSignatureId = signatureId.takeUnless { it == state.value.defaultSignatureId }

        viewModelScope.launch(dispatcher) {
            updateSignatures(accountId, UpdateSignaturesCommand.SetDefault(newDefaultSignatureId)).handle(
                onSuccess = { signatures -> updateState { it.withSignatures(signatures) } },
                onFailure = { handleError(it, Error.SetDefaultFailed) },
            )
        }
    }

    private fun observeAccountName() {
        getAccountName(accountId)
            .onEach { outcome ->
                outcome.handle(
                    onSuccess = { name -> updateState { it.copy(subtitle = name) } },
                    onFailure = { handleError(it, Error.LoadFailed) },
                )
            }.launchIn(viewModelScope)
    }

    private fun loadSignatures() {
        viewModelScope.launch(dispatcher) {
            getLegacyAccount(accountId).handle(
                onSuccess = { account ->
                    updateState { it.withSignatures(account.toAccountSignatures()) }
                    if (initialSignatureId != null && account.signatures.any { it.id == initialSignatureId }) {
                        isEditingInitialSignature = true
                        editSignature(initialSignatureId)
                    }
                },
                onFailure = { handleError(it, Error.LoadFailed) },
            )
        }
    }

    private fun handleError(accountSettingError: AccountSettingError, error: Error) {
        val message = when (accountSettingError) {
            is AccountSettingError.NotFound -> accountSettingError.message
            is AccountSettingError.StorageError -> accountSettingError.message
            is AccountSettingError.UnsupportedFormat -> accountSettingError.message
            is AccountSettingError.UnsupportedMessageFormat -> accountSettingError.message
            is AccountSettingError.UnsupportedQuoteStyle -> accountSettingError.message
        }
        logger.error(tag = TAG, message = { message })
        updateState { it.copy(error = error) }
    }
}

private fun State.withSignatures(accountSignatures: AccountSignatures) = copy(
    signatures = accountSignatures.signatures.toImmutableList(),
    defaultSignatureId = accountSignatures.defaultSignatureId,
)
