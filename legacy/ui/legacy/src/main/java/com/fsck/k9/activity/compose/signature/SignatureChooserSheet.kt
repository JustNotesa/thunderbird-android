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

@Composable
internal fun SheetTitle(text: String) {
    TextTitleMedium(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BoltTheme.spacings.double, vertical = BoltTheme.spacings.default),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SheetRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    isSelected: Boolean = false,
    badges: ImmutableList<String> = persistentListOf(),
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = BoltTheme.spacings.double, vertical = BoltTheme.spacings.default),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextBodyMedium(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                for (badge in badges) {
                    Spacer(modifier = Modifier.width(BoltTheme.spacings.default))
                    TextLabelSmall(text = badge, color = BoltTheme.colors.primary)
                }
            }
            if (summary != null) {
                TextBodySmall(
                    text = summary,
                    color = BoltTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = stringResource(R.string.message_compose_signature_current),
                tint = BoltTheme.colors.primary,
                modifier = Modifier.size(BoltTheme.sizes.icon),
            )
        }
        trailing?.invoke()
    }
}

@Composable
internal fun ChooseSignatureContent(model: SignatureChooserModel, listener: SignatureSheetListener) {
    val htmlBadge = stringResource(R.string.message_compose_signature_html_badge)
    val defaultBadge = stringResource(R.string.message_compose_signature_default_badge)

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SheetTitle(stringResource(R.string.message_compose_choose_signature_action))

        if (!model.hasSavedSignatures) {
            TextBodyMedium(
                text = stringResource(R.string.message_compose_signature_empty_explanation),
                color = BoltTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = BoltTheme.spacings.double,
                    vertical = BoltTheme.spacings.half,
                ),
            )
        }

        model.defaultEntry?.let { entry ->
            SheetRow(
                title = entry.name,
                summary = entry.preview,
                badges = listOfNotNull(defaultBadge, htmlBadge.takeIf { entry.isHtml }).toImmutableList(),
                isSelected = model.currentSelection == SignatureChooserModel.Selection.Default,
                onClick = listener::onDefaultChosen,
            )
        }

        SavedSignatureRows(model, listener)

        DividerHorizontal(modifier = Modifier.padding(vertical = BoltTheme.spacings.half))

        SheetRow(
            title = stringResource(R.string.message_compose_choose_signature_none),
            summary = stringResource(R.string.message_compose_signature_none_summary),
            isSelected = model.currentSelection == SignatureChooserModel.Selection.None,
            onClick = listener::onNoneChosen,
        )
        SheetRow(
            title = stringResource(
                if (model.hasSavedSignatures) {
                    R.string.message_compose_signature_new_action
                } else {
                    R.string.message_compose_signature_create_action
                },
            ),
            summary = stringResource(R.string.message_compose_signature_create_summary)
                .takeUnless { model.hasSavedSignatures },
            onClick = listener::onCreateRequested,
            modifier = Modifier.padding(bottom = BoltTheme.spacings.double),
        )
    }
}

@Composable
private fun SavedSignatureRows(model: SignatureChooserModel, listener: SignatureSheetListener) {
    val htmlBadge = stringResource(R.string.message_compose_signature_html_badge)

    for (entry in model.savedEntries) {
        val id = checkNotNull(entry.id)
        SheetRow(
            title = entry.name,
            summary = entry.preview,
            badges = listOfNotNull(htmlBadge.takeIf { entry.isHtml }).toImmutableList(),
            isSelected = model.currentSelection == SignatureChooserModel.Selection.Saved(id),
            onClick = { listener.onSavedChosen(id) },
            // A long press opens the editor, like it did in the list before.
            onLongClick = { listener.onEditSavedRequested(id) },
            trailing = {
                ButtonIcon(
                    onClick = { listener.onEditSavedRequested(id) },
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.message_compose_signature_edit_saved, entry.name),
                )
            },
        )
    }
}

@Composable
internal fun HtmlSignatureActionsContent(name: String, listener: SignatureSheetListener) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SheetTitle(stringResource(R.string.message_compose_signature_html_actions_title, name))
        SheetRow(
            title = stringResource(R.string.message_compose_signature_edit_for_message_action),
            summary = stringResource(R.string.message_compose_signature_edit_for_message_summary),
            onClick = listener::onEditHtmlForMessageRequested,
        )
        SheetRow(
            title = stringResource(R.string.message_compose_signature_choose_other_action),
            onClick = listener::onChooseOtherRequested,
        )
        SheetRow(
            title = stringResource(R.string.message_compose_signature_edit_saved_action),
            summary = stringResource(R.string.message_compose_signature_edit_saved_summary),
            onClick = listener::onEditAppliedSavedRequested,
            modifier = Modifier.padding(bottom = BoltTheme.spacings.double),
        )
    }
}

