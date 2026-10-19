package app.k9mail.feature.migration.qrcode.ui.paste

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.k9mail.feature.migration.qrcode.R
import app.k9mail.feature.migration.qrcode.ui.guide.TransferGuideIllustration
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.Event
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.PasteError
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.State
import net.thunderbird.components.ui.bolt.atom.Surface
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilled
import net.thunderbird.components.ui.bolt.atom.button.ButtonIcon
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.atom.text.TextTitleLarge
import net.thunderbird.components.ui.bolt.organism.AlertDialog
import net.thunderbird.components.ui.bolt.template.ResponsiveContent
import net.thunderbird.components.ui.bolt.theme.BoltTheme

/**
 * Stateless content of the screen that imports the accounts a user copied from a password manager.
 */
@Composable
internal fun TransferPasteContent(
    state: State,
    onEvent: (Event) -> Unit,
) {
    var showHelp by rememberSaveable { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        ResponsiveContent(
            modifier = Modifier.testTag("TransferPasteContent"),
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(BoltTheme.spacings.double)
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState()),
            ) {
                TextTitleLarge(text = stringResource(R.string.migration_qrcode_transfer_paste_title))
                Spacer(modifier = Modifier.height(BoltTheme.spacings.double))

                TransferGuideIllustration()
                Spacer(modifier = Modifier.height(BoltTheme.spacings.triple))

                InstructionText(onHelpClick = { showHelp = true })
                Spacer(modifier = Modifier.height(BoltTheme.spacings.triple))

                AccountsTextField(state = state, onEvent = onEvent)

                state.error?.let { error ->
                    Spacer(modifier = Modifier.height(BoltTheme.spacings.default))
                    ErrorText(error = error)
                }

                ActionButtons(onEvent = onEvent)
            }
        }
    }

    if (showHelp) {
        HelpDialog(onDismiss = { showHelp = false })
    }
}

/** What to do with the note, and the way to get it as long as there is none. */
@Composable
private fun InstructionText(onHelpClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextBodyMedium(
            text = stringResource(R.string.migration_qrcode_transfer_paste_instructions),
            modifier = Modifier.weight(weight = 1f),
        )
        ButtonIcon(
            onClick = onHelpClick,
            imageVector = Icons.Outlined.HelpOutline,
            contentDescription = stringResource(R.string.migration_qrcode_transfer_paste_help_button),
            modifier = Modifier.testTag("HelpButton"),
        )
    }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        title = stringResource(R.string.migration_qrcode_transfer_paste_help_title),
        text = stringResource(R.string.migration_qrcode_transfer_paste_help_text),
        confirmText = stringResource(R.string.migration_qrcode_done_button_text),
        onConfirmClick = onDismiss,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("HelpDialog"),
    )
}

@Composable
private fun ActionButtons(onEvent: (Event) -> Unit) {
    Column {
        Spacer(modifier = Modifier.height(BoltTheme.spacings.triple))

        ButtonOutlined(
            text = stringResource(R.string.migration_qrcode_transfer_paste_clipboard_button_text),
            onClick = { onEvent(Event.PasteFromClipboardClicked(clipboardText = null)) },
            modifier = Modifier.testTag("PasteFromClipboardButton"),
            icon = Icons.Outlined.ContentPaste,
        )
        Spacer(modifier = Modifier.height(BoltTheme.spacings.default))

        ButtonFilled(
            text = stringResource(R.string.migration_qrcode_transfer_paste_import_button_text),
            onClick = { onEvent(Event.ImportClicked) },
            modifier = Modifier.testTag("ImportButton"),
        )
    }
}

@Composable
private fun ErrorText(error: PasteError) {
    TextBodyMedium(
        text = stringResource(errorText(error)),
        color = BoltTheme.colors.error,
        modifier = Modifier.testTag("ErrorText"),
    )
}

private fun errorText(error: PasteError): Int {
    return when (error) {
        PasteError.NotRecognized -> R.string.migration_qrcode_transfer_paste_not_recognized
        PasteError.Unsupported -> R.string.migration_qrcode_transfer_paste_unsupported
    }
}
