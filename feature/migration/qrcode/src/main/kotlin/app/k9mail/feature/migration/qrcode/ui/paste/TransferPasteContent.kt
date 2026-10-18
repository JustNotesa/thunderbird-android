package app.k9mail.feature.migration.qrcode.ui.paste

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.k9mail.feature.migration.qrcode.R
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.Event
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.PasteError
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.State
import net.thunderbird.components.ui.bolt.atom.Surface
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilled
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.atom.icon.Icon
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.atom.text.TextTitleLarge
import net.thunderbird.components.ui.bolt.atom.textfield.TextFieldOutlined
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
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
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

                StepGuide()
                Spacer(modifier = Modifier.height(BoltTheme.spacings.triple))

                TextFieldOutlined(
                    value = state.text,
                    onValueChange = { text -> onEvent(Event.TextChanged(text)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = MAX_TEXT_FIELD_HEIGHT)
                        .testTag("AccountsTextField"),
                    label = stringResource(R.string.migration_qrcode_transfer_paste_text_field_label),
                    hasError = state.error != null,
                    isSingleLine = false,
                )

                state.error?.let { error ->
                    Spacer(modifier = Modifier.height(BoltTheme.spacings.default))
                    ErrorText(error = error)
                }

                ActionButtons(onEvent = onEvent)
            }
        }
    }
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

/** The steps of the transfer through the password manager, with the same wording on both devices. */
@Composable
private fun StepGuide() {
    val steps = listOf(
        Icons.Outlined.Lock to stringResource(R.string.migration_qrcode_transfer_paste_step_one),
        Icons.Outlined.Description to stringResource(R.string.migration_qrcode_transfer_paste_step_two),
        Icons.Outlined.Smartphone to stringResource(R.string.migration_qrcode_transfer_paste_step_three),
    )

    Column(modifier = Modifier.testTag("StepGuide")) {
        steps.forEach { (icon, text) -> StepRow(icon = icon, text = text) }
    }
}

@Composable
private fun StepRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(vertical = BoltTheme.spacings.default),
    ) {
        Icon(imageVector = icon, modifier = Modifier.size(STEP_ICON_SIZE))
        Spacer(modifier = Modifier.width(BoltTheme.spacings.default))

        TextBodyMedium(text = text)
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

private val MAX_TEXT_FIELD_HEIGHT = 160.dp
private val STEP_ICON_SIZE = 24.dp
