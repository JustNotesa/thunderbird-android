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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.components.ui.bolt.atom.DropdownMenuBox
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

// The sizes relative to the normal text, close to what mail clients show for small, large and x-large.
private val SIZE_SMALL = 0.8.em
private val SIZE_LARGE = 1.25.em
private val SIZE_HUGE = 1.6.em

private val SIZE_OPTIONS = persistentListOf(TextFormat.SizeSmall, null, TextFormat.SizeLarge, TextFormat.SizeHuge)
private val FONT_OPTIONS = persistentListOf(null, TextFormat.FontSerif, TextFormat.FontMonospace)

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
        TextFormatBar(richText = richText, onRichTextChange = { update(it) })
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
    richText: RichTextEditorState,
    onRichTextChange: (RichTextEditorState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeFormats = richText.activeFormats

    Row(
        horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        for (format in TextFormat.toggles) {
            val title = stringResource(format.titleRes())
            // A format that is switched on looks like a pressed button.
            if (format in activeFormats) {
                ButtonFilled(text = title, onClick = { onRichTextChange(richText.toggle(format)) })
            } else {
                ButtonOutlined(text = title, onClick = { onRichTextChange(richText.toggle(format)) })
            }
        }
        FormatGroupSelector(
            labelRes = R.string.account_settings_signatures_format_size,
            defaultTitleRes = R.string.account_settings_signatures_format_size_normal,
            options = SIZE_OPTIONS,
            selected = richText.activeFormat(TextFormat.Group.Size),
            onSelect = { format -> onRichTextChange(richText.select(TextFormat.Group.Size, format)) },
        )
        FormatGroupSelector(
            labelRes = R.string.account_settings_signatures_format_font,
            defaultTitleRes = R.string.account_settings_signatures_format_font_default,
            options = FONT_OPTIONS,
            selected = richText.activeFormat(TextFormat.Group.Font),
            onSelect = { format -> onRichTextChange(richText.select(TextFormat.Group.Font, format)) },
        )
    }
}

/**
 * A button that shows the size or font at the cursor and opens the list to choose another one from. The `null`
 * option is the normal size or the default font.
 */
@Composable
private fun FormatGroupSelector(
    labelRes: Int,
    defaultTitleRes: Int,
    options: ImmutableList<TextFormat?>,
    selected: TextFormat?,
    onSelect: (TextFormat?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val titles = options.associateWith { option -> stringResource(option?.titleRes() ?: defaultTitleRes) }

    DropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        options = options,
        onItemSelected = { option ->
            expanded = false
            onSelect(option)
        },
        optionToString = { option -> titles.getValue(option) },
        modifier = modifier,
    ) {
        ButtonOutlined(
            text = stringResource(labelRes, titles.getValue(selected)),
            onClick = { expanded = true },
        )
    }
}

private fun TextFormat.titleRes(): Int {
    return when (this) {
        TextFormat.Bold -> R.string.account_settings_signatures_format_bold
        TextFormat.Italic -> R.string.account_settings_signatures_format_italic
        TextFormat.Underline -> R.string.account_settings_signatures_format_underline
        TextFormat.SizeSmall -> R.string.account_settings_signatures_format_size_small
        TextFormat.SizeLarge -> R.string.account_settings_signatures_format_size_large
        TextFormat.SizeHuge -> R.string.account_settings_signatures_format_size_huge
        TextFormat.FontSerif -> R.string.account_settings_signatures_format_font_serif
        TextFormat.FontMonospace -> R.string.account_settings_signatures_format_font_monospace
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
        TextFormat.SizeSmall -> SpanStyle(fontSize = SIZE_SMALL)
        TextFormat.SizeLarge -> SpanStyle(fontSize = SIZE_LARGE)
        TextFormat.SizeHuge -> SpanStyle(fontSize = SIZE_HUGE)
        TextFormat.FontSerif -> SpanStyle(fontFamily = FontFamily.Serif)
        TextFormat.FontMonospace -> SpanStyle(fontFamily = FontFamily.Monospace)
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
