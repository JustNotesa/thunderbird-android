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

/**
 * The sheets of the signature in a message that is being written.
 */
sealed interface SignatureSheet {
    /** The list to choose the signature from, or the explanation and the offer to create one if there is none. */
    data class Choose(val model: SignatureChooserModel) : SignatureSheet

    /** What can be done with an HTML signature. [name] is what the chip says. */
    data class HtmlActions(val name: String) : SignatureSheet

    /** The HTML of the signature to change for this message only. */
    data class HtmlEditor(val initialHtml: String) : SignatureSheet
}

interface SignatureSheetListener {
    fun onDefaultChosen()
    fun onSavedChosen(id: String)
    fun onNoneChosen()
    fun onCreateRequested()
    fun onEditSavedRequested(id: String)
    fun onEditHtmlForMessageRequested()
    fun onChooseOtherRequested()
    fun onEditAppliedSavedRequested()

    /** The user changed the HTML signature of this message only. */
    fun onHtmlEdited(html: String)
    fun onSheetDismissed()
}

@Composable
internal fun SignatureSheetHost(
    sheet: SignatureSheet?,
    listener: SignatureSheetListener,
    pasteProcessor: SignaturePasteProcessor,
) {
    when (sheet) {
        null -> Unit
        is SignatureSheet.Choose -> ModalBottomSheet(onDismissRequest = listener::onSheetDismissed) {
            ChooseSignatureContent(sheet.model, listener)
        }

        is SignatureSheet.HtmlActions -> ModalBottomSheet(onDismissRequest = listener::onSheetDismissed) {
            HtmlSignatureActionsContent(sheet.name, listener)
        }

        is SignatureSheet.HtmlEditor -> ModalBottomSheet(
            onDismissRequest = listener::onSheetDismissed,
            skipPartiallyExpanded = true,
        ) {
            HtmlSignatureEditorContent(
                initialHtml = sheet.initialHtml,
                pasteProcessor = pasteProcessor,
                onCancel = listener::onSheetDismissed,
                onApply = listener::onHtmlEdited,
            )
        }
    }
}

