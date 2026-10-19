package app.k9mail.feature.migration.qrcode.ui.text

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.k9mail.feature.migration.qrcode.R
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.Event
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilled
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.theme.BoltTheme

/**
 * Buttons below the text of the accounts: copying it to the clipboard and closing the screen.
 */
@Composable
internal fun ActionButtons(onEvent: (Event) -> Unit) {
    Column {
        Spacer(modifier = Modifier.height(BoltTheme.spacings.triple))

        ButtonFilled(
            text = stringResource(R.string.migration_qrcode_transfer_text_copy_button_text),
            onClick = { onEvent(Event.CopyClicked) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("CopyButton"),
            icon = Icons.Outlined.ContentCopy,
        )
        Spacer(modifier = Modifier.height(BoltTheme.spacings.default))

        ButtonOutlined(
            text = stringResource(R.string.migration_qrcode_transfer_text_done_button_text),
            onClick = { onEvent(Event.DoneClicked) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("DoneButton"),
        )
    }
}
