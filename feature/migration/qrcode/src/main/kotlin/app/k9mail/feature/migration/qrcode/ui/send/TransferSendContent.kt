package app.k9mail.feature.migration.qrcode.ui.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.k9mail.feature.migration.qrcode.R
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendContract.Event
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendContract.Step
import net.thunderbird.components.ui.bolt.atom.Surface
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.atom.text.TextBodyLarge
import net.thunderbird.components.ui.bolt.atom.text.TextTitleLarge
import net.thunderbird.components.ui.bolt.template.ResponsiveContent
import net.thunderbird.components.ui.bolt.theme.BoltTheme

/**
 * Stateless content of the screen that sends the accounts of this device to another device.
 */
@Composable
internal fun TransferSendContent(
    step: Step,
    onEvent: (Event) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        ResponsiveContent(
            modifier = Modifier.testTag("TransferSendContent"),
        ) { contentPadding ->
            StepContent(
                step = step,
                contentPadding = contentPadding,
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun StepContent(
    step: Step,
    contentPadding: PaddingValues,
    onEvent: (Event) -> Unit,
) {
    when (step) {
        is Step.ShowingQrCode -> ShowingQrCodeContent(
            isHotspot = step.isHotspot,
            qrText = step.qrText,
            contentPadding = contentPadding,
            onCancel = { onEvent(Event.CloseClicked) },
        )

        Step.NoWifi,
        Step.StartingHotspot,
        Step.HotspotUnsupported,
        Step.HotspotPermissionDenied,
        Step.HotspotFailed,
        -> HotspotStepContent(
            step = step,
            contentPadding = contentPadding,
            onEvent = onEvent,
        )

        else -> MessageStepContent(
            step = step,
            contentPadding = contentPadding,
            onEvent = onEvent,
        )
    }
}

@Composable
private fun ShowingQrCodeContent(
    isHotspot: Boolean,
    qrText: String,
    contentPadding: PaddingValues,
    onCancel: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxHeight()
            .padding(BoltTheme.spacings.double)
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        TextTitleLarge(text = stringResource(R.string.migration_qrcode_transfer_send_title))
        Spacer(modifier = Modifier.height(BoltTheme.spacings.double))

        TextBodyLarge(text = stringResource(instructionText(isHotspot)))
        Spacer(modifier = Modifier.height(BoltTheme.spacings.triple))

        QrCodeImage(
            text = qrText,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .testTag("QrCodeImage"),
        )
        Spacer(modifier = Modifier.height(BoltTheme.spacings.triple))

        ButtonOutlined(
            text = stringResource(R.string.migration_qrcode_transfer_send_cancel_button_text),
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .testTag("CancelButton"),
        )
    }
}

/**
 * When the accounts are sent over the hotspot of this device, the new device joins it on its own.
 */
private fun instructionText(isHotspot: Boolean): Int {
    return if (isHotspot) {
        R.string.migration_qrcode_transfer_send_hotspot_instructions
    } else {
        R.string.migration_qrcode_transfer_send_instructions
    }
}
