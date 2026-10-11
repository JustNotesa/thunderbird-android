package net.thunderbird.feature.account.settings.impl.ui.signature

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableSet
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilled
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.atom.button.ButtonSegmentedSingleChoice
import net.thunderbird.components.ui.bolt.atom.text.TextLabelSmall
import net.thunderbird.components.ui.bolt.atom.textfield.TextFieldOutlined
import net.thunderbird.components.ui.bolt.molecule.input.TextInput
import net.thunderbird.components.ui.bolt.organism.AlertDialog
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.feature.account.settings.R
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.Editor
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.EditorMode
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.Event
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

private val SIGNATURE_TEXT_MIN_HEIGHT = 160.dp
private val EDITOR_MODES = persistentListOf(EditorMode.Formatted, EditorMode.Code)

@Composable
internal fun SignatureEditor(
    editor: Editor,
    onEvent: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(vertical = BoltTheme.spacings.double),
    ) {
        TextInput(
            text = editor.name,
            onTextChange = { onEvent(Event.OnNameChange(it)) },
            label = stringResource(R.string.account_settings_signatures_name_label),
            isRequired = true,
            modifier = Modifier.testTag(SignatureSettingsTestTags.NAME_INPUT),
        )
        val modeTitles = mapOf(
            EditorMode.Formatted to stringResource(R.string.account_settings_signatures_mode_formatted),
            EditorMode.Code to stringResource(R.string.account_settings_signatures_mode_code),
        )
        ButtonSegmentedSingleChoice(
            onClick = { onEvent(Event.OnModeChange(it)) },
            options = EDITOR_MODES,
            optionTitle = { mode -> modeTitles.getValue(mode) },
            selectedOption = editor.mode,
            modifier = Modifier.padding(horizontal = BoltTheme.spacings.double),
        )
        // The fields keep the selection themselves. A new mode means new text, so they have to start over.
        key(editor.mode) {
            when (editor.mode) {
                EditorMode.Formatted -> FormattedSignatureField(editor = editor, onEvent = onEvent)
                EditorMode.Code -> HtmlCodeSignatureField(editor = editor, onEvent = onEvent)
            }
        }
    }

    if (editor.isConfirmingFormattedMode) {
        AlertDialog(
            title = stringResource(R.string.account_settings_signatures_formatted_mode_dialog_title),
            text = stringResource(R.string.account_settings_signatures_formatted_mode_dialog_text),
            confirmText = stringResource(R.string.account_settings_signatures_formatted_mode_dialog_confirm),
            dismissText = stringResource(R.string.account_settings_cancel_action),
            onConfirmClick = { onEvent(Event.OnFormattedModeConfirm) },
            onDismissClick = { onEvent(Event.OnFormattedModeDismiss) },
            onDismissRequest = { onEvent(Event.OnFormattedModeDismiss) },
        )
    }
}

@Composable
private fun FormattedSignatureField(
    editor: Editor,
    onEvent: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    var richText by remember {
        mutableStateOf(
            RichTextEditorState(
                text = editor.text,
                ranges = editor.ranges,
                selectionStart = editor.text.length,
                selectionEnd = editor.text.length,
            ),
        )
    }
    // The word the keyboard is still composing. The text field needs it back, or the keyboard starts the word over.
    var composition by remember { mutableStateOf<TextRange?>(null) }

    fun update(newRichText: RichTextEditorState) {
        val contentChanged = newRichText.text != richText.text || newRichText.ranges != richText.ranges
        richText = newRichText
        if (contentChanged) onEvent(Event.OnFormattedTextChange(newRichText.text, newRichText.ranges))
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
        modifier = modifier.padding(horizontal = BoltTheme.spacings.double),
    ) {
        TextFormatBar(
            activeFormats = richText.activeFormats.toImmutableSet(),
            onFormatClick = { format -> update(richText.toggle(format)) },
        )
        TextFieldOutlined(
            value = TextFieldValue(
                annotatedString = richText.toAnnotatedString(),
                selection = TextRange(richText.selectionStart, richText.selectionEnd),
                composition = composition,
            ),
            onValueChange = { newValue ->
                composition = newValue.composition
                update(richText.onTextFieldChange(newValue.text, newValue.selection.start, newValue.selection.end))
            },
            label = stringResource(R.string.account_settings_signatures_text_label),
            isRequired = true,
            isSingleLine = false,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SIGNATURE_TEXT_MIN_HEIGHT)
                .testTag(SignatureSettingsTestTags.TEXT_INPUT),
        )
    }
}

@Composable
private fun TextFormatBar(
    activeFormats: ImmutableSet<TextFormat>,
    onFormatClick: (TextFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        for (format in TextFormat.entries) {
            val title = stringResource(
                when (format) {
                    TextFormat.Bold -> R.string.account_settings_signatures_format_bold
                    TextFormat.Italic -> R.string.account_settings_signatures_format_italic
                    TextFormat.Underline -> R.string.account_settings_signatures_format_underline
                },
            )
            // A format that is switched on looks like a pressed button.
            if (format in activeFormats) {
                ButtonFilled(text = title, onClick = { onFormatClick(format) })
            } else {
                ButtonOutlined(text = title, onClick = { onFormatClick(format) })
            }
        }
    }
}

private fun RichTextEditorState.toAnnotatedString(): AnnotatedString {
    return buildAnnotatedString {
        append(text)
        for (range in ranges) {
            addStyle(range.format.toSpanStyle(), range.start, range.end)
        }
    }
}

private fun TextFormat.toSpanStyle(): SpanStyle {
    return when (this) {
        TextFormat.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
        TextFormat.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
        TextFormat.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
    }
}

@Composable
private fun HtmlCodeSignatureField(
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
        modifier = modifier.padding(horizontal = BoltTheme.spacings.double),
    ) {
        TextLabelSmall(text = stringResource(R.string.account_settings_signatures_is_html_summary))
        HtmlFormatBar(onFormatClick = { format -> onFieldValueChange(fieldValue.applyHtmlFormat(format)) })
        TextFieldOutlined(
            value = fieldValue,
            onValueChange = onFieldValueChange,
            label = stringResource(R.string.account_settings_signatures_html_text_label),
            isRequired = true,
            isSingleLine = false,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SIGNATURE_TEXT_MIN_HEIGHT)
                .testTag(SignatureSettingsTestTags.TEXT_INPUT),
        )
        editor.previewHtml?.takeIf { editor.text.isNotBlank() }?.let { previewHtml ->
            TextLabelSmall(
                text = stringResource(R.string.account_settings_signatures_preview_label),
                color = BoltTheme.colors.onSurfaceVariant,
            )
            SignatureHtmlPreview(html = previewHtml, modifier = Modifier.fillMaxWidth())
        }
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
