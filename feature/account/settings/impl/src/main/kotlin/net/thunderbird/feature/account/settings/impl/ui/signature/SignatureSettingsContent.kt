package net.thunderbird.feature.account.settings.impl.ui.signature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import net.thunderbird.components.ui.bolt.atom.DividerHorizontal
import net.thunderbird.components.ui.bolt.atom.Surface
import net.thunderbird.components.ui.bolt.atom.button.ButtonIcon
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.atom.button.ButtonText
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.atom.text.TextBodyLarge
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.atom.text.TextLabelSmall
import net.thunderbird.components.ui.bolt.atom.textfield.TextFieldOutlined
import net.thunderbird.components.ui.bolt.molecule.input.CheckboxInput
import net.thunderbird.components.ui.bolt.molecule.input.TextInput
import net.thunderbird.components.ui.bolt.organism.AlertDialog
import net.thunderbird.components.ui.bolt.organism.SubtitleTopAppBarWithBackButton
import net.thunderbird.components.ui.bolt.template.Scaffold
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.core.android.account.AccountSignature
import net.thunderbird.feature.account.settings.R
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.Editor
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.Error
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.Event
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.State

private const val SIGNATURE_PREVIEW_MAX_LINES = 2
private val SIGNATURE_TEXT_MIN_HEIGHT = 160.dp

@Composable
internal fun SignatureSettingsContent(
    state: State,
    onEvent: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editor = state.editor

    Scaffold(
        modifier = modifier,
        topBar = {
            SignatureSettingsTopBar(state = state, onEvent = onEvent)
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            if (editor == null) {
                SignatureList(
                    signatures = state.signatures,
                    defaultSignatureId = state.defaultSignatureId,
                    onEvent = onEvent,
                )
            } else {
                SignatureEditor(editor = editor, onEvent = onEvent)
            }
        }
    }

    state.signaturePendingDeletion?.let { signature ->
        AlertDialog(
            title = stringResource(R.string.account_settings_signatures_delete_dialog_title),
            text = stringResource(R.string.account_settings_signatures_delete_dialog_text, signature.name),
            confirmText = stringResource(R.string.account_settings_signatures_delete_action),
            dismissText = stringResource(R.string.account_settings_cancel_action),
            onConfirmClick = { onEvent(Event.OnDeleteConfirm) },
            onDismissClick = { onEvent(Event.OnDeleteDismiss) },
            onDismissRequest = { onEvent(Event.OnDeleteDismiss) },
        )
    }

    state.error?.let { error ->
        AlertDialog(
            title = stringResource(R.string.account_settings_signatures_error_title),
            text = stringResource(
                when (error) {
                    Error.LoadFailed -> R.string.account_settings_signatures_error_load
                    Error.SaveFailed -> R.string.account_settings_signatures_error_save
                    Error.DeleteFailed -> R.string.account_settings_signatures_error_delete
                    Error.SetDefaultFailed -> R.string.account_settings_signatures_error_set_default
                },
            ),
            confirmText = stringResource(R.string.account_settings_okay_action),
            onConfirmClick = { onEvent(Event.OnErrorDismiss) },
            onDismissRequest = { onEvent(Event.OnErrorDismiss) },
        )
    }
}

@Composable
private fun SignatureSettingsTopBar(
    state: State,
    onEvent: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editor = state.editor

    SubtitleTopAppBarWithBackButton(
        title = stringResource(
            when {
                editor == null -> R.string.account_settings_signatures_title
                editor.signatureId == null -> R.string.account_settings_signatures_new_title
                else -> R.string.account_settings_signatures_edit_title
            },
        ),
        subtitle = state.subtitle.orEmpty(),
        onBackClick = { onEvent(Event.OnBackPressed) },
        actions = {
            if (editor == null) {
                ButtonIcon(
                    onClick = { onEvent(Event.OnAddClick) },
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.account_settings_signatures_add_action),
                    modifier = Modifier.testTag(SignatureSettingsTestTags.ADD_BUTTON),
                )
            } else {
                ButtonText(
                    text = stringResource(R.string.account_settings_signatures_save_action),
                    onClick = { onEvent(Event.OnSaveClick) },
                    enabled = editor.canSave,
                    modifier = Modifier.testTag(SignatureSettingsTestTags.SAVE_BUTTON),
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun SignatureList(
    signatures: ImmutableList<AccountSignature>,
    defaultSignatureId: String?,
    onEvent: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (signatures.isEmpty()) {
        TextBodyMedium(
            text = stringResource(R.string.account_settings_signatures_empty),
            color = BoltTheme.colors.onSurfaceVariant,
            modifier = modifier
                .fillMaxWidth()
                .padding(BoltTheme.spacings.triple),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = BoltTheme.spacings.default),
    ) {
        items(items = signatures, key = { it.id }) { signature ->
            SignatureListItem(
                signature = signature,
                isDefault = signature.id == defaultSignatureId,
                onEvent = onEvent,
            )
            DividerHorizontal()
        }
    }
}

@Composable
private fun SignatureListItem(
    signature: AccountSignature,
    isDefault: Boolean,
    onEvent: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onEvent(Event.OnSignatureClick(signature.id)) }
            .padding(
                start = BoltTheme.spacings.double,
                end = BoltTheme.spacings.default,
                top = BoltTheme.spacings.oneHalf,
                bottom = BoltTheme.spacings.oneHalf,
            ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
            modifier = Modifier.weight(1f),
        ) {
            TextBodyLarge(text = signature.name)
            TextBodyMedium(
                text = signature.text,
                color = BoltTheme.colors.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = SIGNATURE_PREVIEW_MAX_LINES,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default)) {
                if (isDefault) {
                    TextLabelSmall(
                        text = stringResource(R.string.account_settings_signatures_default_badge),
                        color = BoltTheme.colors.primary,
                    )
                }
                if (signature.isHtml) {
                    TextLabelSmall(
                        text = stringResource(R.string.account_settings_signatures_html_badge),
                        color = BoltTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
        ButtonIcon(
            onClick = { onEvent(Event.OnDefaultClick(signature.id)) },
            imageVector = if (isDefault) Icons.Filled.Star else Icons.Outlined.Star,
            contentDescription = stringResource(
                if (isDefault) {
                    R.string.account_settings_signatures_unset_default_action
                } else {
                    R.string.account_settings_signatures_set_default_action
                },
            ),
        )
        ButtonIcon(
            onClick = { onEvent(Event.OnDeleteClick(signature.id)) },
            imageVector = Icons.Outlined.Delete,
            contentDescription = stringResource(R.string.account_settings_signatures_delete_action),
        )
    }
}

@Composable
private fun SignatureEditor(
    editor: Editor,
    onEvent: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The text field keeps the selection, which the formatting buttons need. The text itself lives in the state.
    var fieldValue by remember { mutableStateOf(TextFieldValue(editor.text)) }
    if (fieldValue.text != editor.text) {
        fieldValue = fieldValue.copy(text = editor.text)
    }
    val onFieldValueChange: (TextFieldValue) -> Unit = { newValue ->
        val textChanged = newValue.text != fieldValue.text
        fieldValue = newValue
        if (textChanged) onEvent(Event.OnTextChange(newValue.text))
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(vertical = BoltTheme.spacings.double),
    ) {
        SignatureNameAndFormat(editor = editor, onEvent = onEvent)
        if (editor.isHtml) {
            HtmlFormatBar(
                onFormatClick = { format -> onFieldValueChange(fieldValue.applyHtmlFormat(format)) },
                modifier = Modifier.padding(horizontal = BoltTheme.spacings.double),
            )
        }
        TextFieldOutlined(
            value = fieldValue,
            onValueChange = onFieldValueChange,
            label = stringResource(
                if (editor.isHtml) {
                    R.string.account_settings_signatures_html_text_label
                } else {
                    R.string.account_settings_signatures_text_label
                },
            ),
            isRequired = true,
            isSingleLine = false,
            modifier = Modifier
                .padding(horizontal = BoltTheme.spacings.double)
                .fillMaxWidth()
                .heightIn(min = SIGNATURE_TEXT_MIN_HEIGHT)
                .testTag(SignatureSettingsTestTags.TEXT_INPUT),
        )
        editor.previewHtml?.takeIf { editor.text.isNotBlank() }?.let { previewHtml ->
            SignaturePreview(
                previewHtml = previewHtml,
                modifier = Modifier.padding(horizontal = BoltTheme.spacings.double),
            )
        }
    }
}

@Composable
private fun SignatureNameAndFormat(
    editor: Editor,
    onEvent: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
        modifier = modifier,
    ) {
        TextInput(
            text = editor.name,
            onTextChange = { onEvent(Event.OnNameChange(it)) },
            label = stringResource(R.string.account_settings_signatures_name_label),
            isRequired = true,
            modifier = Modifier.testTag(SignatureSettingsTestTags.NAME_INPUT),
        )
        CheckboxInput(
            text = stringResource(R.string.account_settings_signatures_is_html_label),
            checked = editor.isHtml,
            onCheckedChange = { onEvent(Event.OnIsHtmlChange(it)) },
        )
        TextLabelSmall(
            text = stringResource(R.string.account_settings_signatures_is_html_summary),
            modifier = Modifier.padding(horizontal = BoltTheme.spacings.double),
        )
    }
}

@Composable
private fun SignaturePreview(
    previewHtml: String,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
        modifier = modifier,
    ) {
        TextLabelSmall(
            text = stringResource(R.string.account_settings_signatures_preview_label),
            color = BoltTheme.colors.onSurfaceVariant,
        )
        SignatureHtmlPreview(html = previewHtml, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun HtmlFormatBar(
    onFormatClick: (HtmlFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        ButtonOutlined(
            text = stringResource(R.string.account_settings_signatures_format_bold),
            onClick = { onFormatClick(HtmlFormat.Bold) },
        )
        ButtonOutlined(
            text = stringResource(R.string.account_settings_signatures_format_italic),
            onClick = { onFormatClick(HtmlFormat.Italic) },
        )
        ButtonOutlined(
            text = stringResource(R.string.account_settings_signatures_format_link),
            onClick = { onFormatClick(HtmlFormat.Link) },
        )
        ButtonOutlined(
            text = stringResource(R.string.account_settings_signatures_format_line_break),
            onClick = { onFormatClick(HtmlFormat.LineBreak) },
        )
    }
}

internal object SignatureSettingsTestTags {
    const val ADD_BUTTON = "SignatureSettingsAddButton"
    const val SAVE_BUTTON = "SignatureSettingsSaveButton"
    const val NAME_INPUT = "SignatureSettingsNameInput"
    const val TEXT_INPUT = "SignatureSettingsTextInput"
}
