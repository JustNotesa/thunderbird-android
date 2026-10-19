package app.k9mail.feature.migration.qrcode.ui.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.k9mail.feature.migration.qrcode.R
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendContract.Event
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendContract.Step
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilled
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.atom.text.TextBodyLarge
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.atom.text.TextTitleLarge
import net.thunderbird.components.ui.bolt.theme.BoltTheme

/**
 * Content of the steps that tell the user about the state of the transfer over Wi-Fi.
 */
@Composable
internal fun MessageStepContent(
    step: Step,
    contentPadding: PaddingValues,
    onEvent: (Event) -> Unit,
) {
    when (step) {
        Step.Preparing -> MessageContent(
            text = stringResource(R.string.migration_qrcode_transfer_send_preparing),
            contentPadding = contentPadding,
        )

        Step.Transferred -> MessageContent(
            title = stringResource(R.string.migration_qrcode_transfer_send_transferred),
            contentPadding = contentPadding,
            primaryAction = doneAction(onEvent),
        )

        Step.Expired -> MessageContent(
            text = stringResource(R.string.migration_qrcode_transfer_send_expired),
            contentPadding = contentPadding,
            primaryAction = retryAction(onEvent),
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
            primaryAction = retryAction(onEvent),
            onClose = { onEvent(Event.CloseClicked) },
        )

        else -> Unit
    }
}

@Composable
internal fun MessageContent(
    contentPadding: PaddingValues,
    text: String = "",
    hint: String? = null,
    title: String? = null,
    primaryAction: MessageAction? = null,
    secondaryAction: MessageAction? = null,
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

        if (hint != null) {
            TextBodyMedium(text = hint)
            Spacer(modifier = Modifier.height(BoltTheme.spacings.triple))
        }

        MessageActionButton(action = primaryAction)
        MessageActionButton(action = secondaryAction)

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

@Composable
private fun ColumnScope.MessageActionButton(action: MessageAction?) {
    if (action == null) return

    ButtonFilled(
        text = action.text,
        onClick = action.onClick,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .testTag(action.tag),
    )
    Spacer(modifier = Modifier.height(BoltTheme.spacings.default))
}

@Immutable
internal data class MessageAction(
    val text: String,
    val tag: String,
    val onClick: () -> Unit,
)

@Composable
internal fun retryAction(onEvent: (Event) -> Unit): MessageAction {
    return MessageAction(
        text = stringResource(R.string.migration_qrcode_transfer_send_retry_button_text),
        tag = "RetryButton",
        onClick = { onEvent(Event.RetryClicked) },
    )
}

@Composable
private fun doneAction(onEvent: (Event) -> Unit): MessageAction {
    return MessageAction(
        text = stringResource(R.string.migration_qrcode_transfer_send_done_button_text),
        tag = "DoneButton",
        onClick = { onEvent(Event.CloseClicked) },
    )
}
