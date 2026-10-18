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
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilled
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
            when (step) {
                Step.Preparing -> MessageContent(
                    text = stringResource(R.string.migration_qrcode_transfer_send_preparing),
                    contentPadding = contentPadding,
                )

                is Step.ShowingQrCode -> ShowingQrCodeContent(
                    qrText = step.qrText,
                    contentPadding = contentPadding,
                    onCancel = { onEvent(Event.CloseClicked) },
                )

                Step.Transferred -> MessageContent(
                    title = stringResource(R.string.migration_qrcode_transfer_send_transferred),
                    contentPadding = contentPadding,
                    action = MessageAction(
                        text = stringResource(R.string.migration_qrcode_transfer_send_done_button_text),
                        tag = "DoneButton",
                        onClick = { onEvent(Event.CloseClicked) },
                    ),
                )

                Step.Expired -> MessageContent(
                    text = stringResource(R.string.migration_qrcode_transfer_send_expired),
                    contentPadding = contentPadding,
                    action = MessageAction(
                        text = stringResource(R.string.migration_qrcode_transfer_send_new_qr_code_button_text),
                        tag = "NewQrCodeButton",
                        onClick = { onEvent(Event.RetryClicked) },
                    ),
                    onClose = { onEvent(Event.CloseClicked) },
                )

                Step.NoWifi -> MessageContent(
                    text = stringResource(R.string.migration_qrcode_transfer_send_no_wifi),
                    contentPadding = contentPadding,
                    action = retryAction(onEvent),
                    onClose = { onEvent(Event.CloseClicked) },
                )

                Step.NoAccounts -> MessageContent(
                    text = stringResource(R.string.migration_qrcode_transfer_send_no_accounts),
                    contentPadding = contentPadding,
                    onClose = { onEvent(Event.CloseClicked) },
                )

                Step.Failed -> MessageContent(
                    text = stringResource(R.string.migration_qrcode_transfer_send_failed),
                    contentPadding = contentPadding,
                    action = retryAction(onEvent),
                    onClose = { onEvent(Event.CloseClicked) },
                )
            }
        }
    }
}

@Composable
private fun ShowingQrCodeContent(
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

        TextBodyLarge(text = stringResource(R.string.migration_qrcode_transfer_send_instructions))
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

@Composable
private fun MessageContent(
    contentPadding: PaddingValues,
    text: String = "",
    title: String? = null,
    action: MessageAction? = null,
    onClose: (() -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxHeight()
            .padding(BoltTheme.spacings.double)
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        if (title != null) {
            TextTitleLarge(text = title)
            Spacer(modifier = Modifier.height(BoltTheme.spacings.double))
        }

        if (text.isNotEmpty()) {
            TextBodyLarge(text = text)
            Spacer(modifier = Modifier.height(BoltTheme.spacings.triple))
        }

        if (action != null) {
            ButtonFilled(
                text = action.text,
                onClick = action.onClick,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag(action.tag),
            )
            Spacer(modifier = Modifier.height(BoltTheme.spacings.default))
        }

        if (onClose != null) {
            ButtonOutlined(
                text = stringResource(R.string.migration_qrcode_transfer_send_close_button_text),
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("CloseButton"),
            )
        }
    }
}

private data class MessageAction(
    val text: String,
    val tag: String,
    val onClick: () -> Unit,
)

@Composable
private fun retryAction(onEvent: (Event) -> Unit): MessageAction {
    return MessageAction(
        text = stringResource(R.string.migration_qrcode_transfer_send_retry_button_text),
        tag = "RetryButton",
        onClick = { onEvent(Event.RetryClicked) },
    )
}
