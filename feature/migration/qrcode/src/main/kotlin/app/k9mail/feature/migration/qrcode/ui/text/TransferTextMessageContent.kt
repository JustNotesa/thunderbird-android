package app.k9mail.feature.migration.qrcode.ui.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.k9mail.feature.migration.qrcode.R
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.Event
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.atom.text.TextBodyLarge
import net.thunderbird.components.ui.bolt.theme.BoltTheme

/**
 * Content of the steps that tell the user that there is nothing to copy.
 */
@Composable
internal fun MessageStepContent(
    text: String,
    contentPadding: PaddingValues,
    onEvent: (Event) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxHeight()
            .padding(BoltTheme.spacings.double)
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        TextBodyLarge(text = text)
        Spacer(modifier = Modifier.height(BoltTheme.spacings.triple))

        ButtonOutlined(
            text = stringResource(R.string.migration_qrcode_transfer_send_close_button_text),
            onClick = { onEvent(Event.DoneClicked) },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .testTag("CloseButton"),
        )
    }
}
