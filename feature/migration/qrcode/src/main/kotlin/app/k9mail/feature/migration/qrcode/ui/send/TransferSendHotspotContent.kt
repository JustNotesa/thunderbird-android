package app.k9mail.feature.migration.qrcode.ui.send

import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.k9mail.feature.migration.qrcode.R
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendContract.Event
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendContract.Step

/**
 * Content of the steps in which the accounts are sent over the local-only hotspot of this device.
 *
 * Wherever the hotspot itself is the problem, retrying opens the hotspot again, so the Wi-Fi transfer is not offered
 * a second time.
 */
@Composable
internal fun HotspotStepContent(
    step: Step,
    contentPadding: PaddingValues,
    onEvent: (Event) -> Unit,
) {
    when (step) {
        Step.NoWifi -> MessageContent(
            text = stringResource(R.string.migration_qrcode_transfer_send_no_wifi),
            hint = stringResource(R.string.migration_qrcode_transfer_send_hotspot_hint),
            contentPadding = contentPadding,
            primaryAction = useHotspotAction(onEvent),
            secondaryAction = retryAction(onEvent),
            onClose = { onEvent(Event.CloseClicked) },
        )

        Step.StartingHotspot -> MessageContent(
            text = stringResource(R.string.migration_qrcode_transfer_send_hotspot_starting),
            contentPadding = contentPadding,
        )

        Step.HotspotUnsupported -> MessageContent(
            text = stringResource(R.string.migration_qrcode_transfer_send_hotspot_unsupported),
            contentPadding = contentPadding,
            onClose = { onEvent(Event.CloseClicked) },
        )

        Step.HotspotPermissionDenied -> MessageContent(
            text = stringResource(hotspotPermissionDeniedText()),
            contentPadding = contentPadding,
            primaryAction = hotspotRetryAction(onEvent),
            onClose = { onEvent(Event.CloseClicked) },
        )

        Step.HotspotFailed -> MessageContent(
            text = stringResource(R.string.migration_qrcode_transfer_send_hotspot_failed),
            contentPadding = contentPadding,
            primaryAction = hotspotRetryAction(onEvent),
            onClose = { onEvent(Event.CloseClicked) },
        )

        else -> Unit
    }
}

/**
 * Up to Android 12 the hotspot asks for the location permission, from Android 13 on for the nearby devices
 * permission, so the permission the user is missing is named differently.
 */
private fun hotspotPermissionDeniedText(): Int {
    return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        R.string.migration_qrcode_transfer_send_hotspot_permission_denied_location
    } else {
        R.string.migration_qrcode_transfer_send_hotspot_permission_denied
    }
}

@Composable
private fun useHotspotAction(onEvent: (Event) -> Unit): MessageAction {
    return MessageAction(
        text = stringResource(R.string.migration_qrcode_transfer_send_hotspot_button_text),
        tag = "UseHotspotButton",
        onClick = { onEvent(Event.UseHotspotClicked) },
    )
}

@Composable
private fun hotspotRetryAction(onEvent: (Event) -> Unit): MessageAction {
    return MessageAction(
        text = stringResource(R.string.migration_qrcode_transfer_send_retry_button_text),
        tag = "RetryButton",
        onClick = { onEvent(Event.UseHotspotClicked) },
    )
}
