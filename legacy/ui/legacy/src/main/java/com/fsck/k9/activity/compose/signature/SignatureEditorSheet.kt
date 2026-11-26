package com.fsck.k9.activity.compose.signature

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fsck.k9.ui.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import net.thunderbird.components.ui.bolt.atom.DividerHorizontal
import net.thunderbird.components.ui.bolt.atom.DropdownMenuBox
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilled
import net.thunderbird.components.ui.bolt.atom.button.ButtonIcon
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.atom.button.ButtonText
import net.thunderbird.components.ui.bolt.atom.icon.Icon
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.atom.text.TextBodySmall
import net.thunderbird.components.ui.bolt.atom.text.TextLabelSmall
import net.thunderbird.components.ui.bolt.atom.text.TextTitleMedium
import net.thunderbird.components.ui.bolt.atom.textfield.TextFieldOutlined
import net.thunderbird.components.ui.bolt.atom.textfield.TextFieldPasteHandler
import net.thunderbird.components.ui.bolt.atom.textfield.pasteShortcut
import net.thunderbird.components.ui.bolt.organism.AlertDialog
import net.thunderbird.components.ui.bolt.organism.ModalBottomSheet
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.feature.mail.message.composer.signature.ClipboardContent
import net.thunderbird.feature.mail.message.composer.signature.HtmlSourceFormat
import net.thunderbird.feature.mail.message.composer.signature.HtmlSourceText
import net.thunderbird.feature.mail.message.composer.signature.PasteNotice
import net.thunderbird.feature.mail.message.composer.signature.PasteResult
import net.thunderbird.feature.mail.message.composer.signature.SignatureImageLimits
import net.thunderbird.feature.mail.message.composer.signature.SignaturePasteProcessor
import net.thunderbird.feature.mail.message.composer.signature.message
import net.thunderbird.feature.mail.message.composer.signature.readClipboardForSignature
import net.thunderbird.feature.mail.message.composer.R as ComposerR

private val EDITOR_MIN_HEIGHT = 160.dp
private val SIZE_OPTIONS = persistentListOf<HtmlSourceFormat?>(
    HtmlSourceFormat.SizeSmall,
    null,
    HtmlSourceFormat.SizeLarge,
    HtmlSourceFormat.SizeHuge,
)
private val FONT_OPTIONS =
    persistentListOf<HtmlSourceFormat?>(null, HtmlSourceFormat.FontSerif, HtmlSourceFormat.FontMonospace)

@Composable
internal fun HtmlSignatureEditorContent(
    initialHtml: String,
    pasteProcessor: SignaturePasteProcessor,
    onCancel: () -> Unit,
    onApply: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf(TextFieldValue(initialHtml, TextRange(initialHtml.length))) }
    var isPasting by remember { mutableStateOf(false) }
    var notices by remember { mutableStateOf<ImmutableList<PasteNotice>>(persistentListOf()) }

    Column(
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = BoltTheme.spacings.double),
    ) {
        TextTitleMedium(text = stringResource(R.string.message_compose_signature_edit_for_message_action))
        TextBodySmall(
            text = stringResource(R.string.message_compose_signature_edit_for_message_summary),
            color = BoltTheme.colors.onSurfaceVariant,
        )

        HtmlSourceFormatBar(
            onFormat = { format ->
                val result = value.toHtmlSourceText().withFormat(format)
                value = result.toTextFieldValue()
            },
        )

        // Pasting formatted text or images keeps them. The clipboard is read, and images are loaded, only now.
        val paste = {
            val content = context.readClipboardForSignature()
            val isRich = content.hasRichContent && !isPasting
            if (isRich) {
                isPasting = true
                scope.launch {
                    val pasted = pasteInto(value, pasteProcessor, content)
                    value = pasted.value
                    notices = pasted.notices
                    isPasting = false
                }
            }
            isRich
        }
        TextFieldPasteHandler(onPaste = paste) {
            TextFieldOutlined(
                value = value,
                onValueChange = { value = it },
                label = stringResource(R.string.message_compose_signature_html_source_label),
                isSingleLine = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = EDITOR_MIN_HEIGHT)
                    .pasteShortcut(paste)
                    .testTag("SignatureHtmlSourceInput"),
            )
        }
        if (isPasting) {
            TextLabelSmall(text = stringResource(ComposerR.string.message_composer_signature_paste_working))
        }

        EditorButtons(onCancel = onCancel, onApply = { onApply(value.text) })
    }

    PasteNoticesDialog(notices = notices, onDismiss = { notices = persistentListOf() })
}

@Composable
private fun EditorButtons(onCancel: () -> Unit, onApply: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default, Alignment.End),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = BoltTheme.spacings.double),
    ) {
        ButtonText(text = stringResource(R.string.message_compose_signature_cancel_action), onClick = onCancel)
        ButtonFilled(text = stringResource(R.string.message_compose_signature_apply_action), onClick = onApply)
    }
}

private class PasteOutcome(val value: TextFieldValue, val notices: ImmutableList<PasteNotice>)

/**
 * Puts what the user copied into [value] at the cursor. What could not be pasted ends up in the notices.
 */
private suspend fun pasteInto(
    value: TextFieldValue,
    pasteProcessor: SignaturePasteProcessor,
    content: ClipboardContent,
): PasteOutcome {
    val usedBytes = SignatureImageLimits.embeddedImageBytes(value.text)
    return when (val result = pasteProcessor.process(content, usedBytes)) {
        is PasteResult.Content -> PasteOutcome(
            value = value.toHtmlSourceText().withInserted(result.html).toTextFieldValue(),
            notices = result.notices.toImmutableList(),
        )

        is PasteResult.NothingToPaste -> PasteOutcome(
            value = value,
            notices = result.notices.ifEmpty { listOf(PasteNotice.NothingUsable) }.toImmutableList(),
        )
    }
}

private fun TextFieldValue.toHtmlSourceText() = HtmlSourceText(text, selection.start, selection.end)

private fun HtmlSourceText.toTextFieldValue() = TextFieldValue(text, TextRange(selectionStart, selectionEnd))

@Composable
private fun PasteNoticesDialog(notices: ImmutableList<PasteNotice>, onDismiss: () -> Unit) {
    if (notices.isEmpty()) return

    val context = LocalContext.current
    AlertDialog(
        title = stringResource(ComposerR.string.message_composer_signature_paste_notices_title),
        text = notices.joinToString(separator = "\n\n") { notice -> notice.message(context) },
        confirmText = stringResource(com.fsck.k9.ui.base.R.string.okay_action),
        onConfirmClick = onDismiss,
        onDismissRequest = onDismiss,
    )
}

@Composable
private fun HtmlSourceFormatBar(
    onFormat: (HtmlSourceFormat) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        ButtonOutlined(
            text = stringResource(R.string.message_compose_signature_format_bold),
            onClick = { onFormat(HtmlSourceFormat.Bold) },
        )
        ButtonOutlined(
            text = stringResource(R.string.message_compose_signature_format_italic),
            onClick = { onFormat(HtmlSourceFormat.Italic) },
        )
        ButtonOutlined(
            text = stringResource(R.string.message_compose_signature_format_underline),
            onClick = { onFormat(HtmlSourceFormat.Underline) },
        )
        ButtonOutlined(
            text = stringResource(R.string.message_compose_signature_format_strikethrough),
            onClick = { onFormat(HtmlSourceFormat.Strikethrough) },
        )
        FormatSelector(
            labelRes = R.string.message_compose_signature_format_size,
            options = SIZE_OPTIONS,
            optionTitle = { option -> stringResource(option.sizeTitleRes()) },
            onSelect = { option -> option?.let(onFormat) },
        )
        FormatSelector(
            labelRes = R.string.message_compose_signature_format_font,
            options = FONT_OPTIONS,
            optionTitle = { option -> stringResource(option.fontTitleRes()) },
            onSelect = { option -> option?.let(onFormat) },
        )
    }
}

private fun HtmlSourceFormat?.sizeTitleRes(): Int {
    return when (this) {
        HtmlSourceFormat.SizeSmall -> R.string.message_compose_signature_size_small
        HtmlSourceFormat.SizeLarge -> R.string.message_compose_signature_size_large
        HtmlSourceFormat.SizeHuge -> R.string.message_compose_signature_size_huge
        else -> R.string.message_compose_signature_size_normal
    }
}

private fun HtmlSourceFormat?.fontTitleRes(): Int {
    return when (this) {
        HtmlSourceFormat.FontSerif -> R.string.message_compose_signature_font_serif
        HtmlSourceFormat.FontMonospace -> R.string.message_compose_signature_font_monospace
        else -> R.string.message_compose_signature_font_default
    }
}

/**
 * A button that shows the size or the font to choose from; choosing one wraps the selection in it. The `null` option is
 * the normal size or the default font, which has nothing to wrap.
 */
@Composable
private fun FormatSelector(
    labelRes: Int,
    options: ImmutableList<HtmlSourceFormat?>,
    optionTitle: @Composable (HtmlSourceFormat?) -> String,
    onSelect: (HtmlSourceFormat?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val titles = options.associateWith { option -> optionTitle(option) }

    DropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        options = options,
        onItemSelected = { option ->
            expanded = false
            onSelect(option)
        },
        optionToString = { option -> titles.getValue(option) },
    ) {
        ButtonOutlined(
            text = stringResource(labelRes, titles.getValue(null)),
            onClick = { expanded = true },
        )
    }
}
