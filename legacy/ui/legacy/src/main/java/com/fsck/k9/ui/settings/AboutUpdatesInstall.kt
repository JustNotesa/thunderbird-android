package com.fsck.k9.ui.settings

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fsck.k9.ui.R
import net.thunderbird.components.ui.bolt.atom.CircularProgressIndicator
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilled
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.feature.updater.api.DownloadWaiting
import net.thunderbird.feature.updater.api.UpdateInstallFailure
import net.thunderbird.feature.updater.api.UpdateInstallState

private const val PERCENT = 100

/**
 * The information about a running download that is shown in the UI.
 */
internal data class DownloadStatus(
    /** The text describing how much of the download is done. */
    @StringRes val progressTextRes: Int,
    /** The arguments of [progressTextRes]. */
    val progressTextArgs: List<Any>,
    /** The percentage of the download that is done, `null` when the total size isn't known. */
    val percent: Int?,
    /** The text explaining why the download isn't running, `null` while the download is running. */
    @StringRes val waitingTextRes: Int?,
)

/**
 * The state of the installation of an update: the download, the verification and the installation itself.
 */
@Composable
internal fun AboutUpdateInstallStatus(
    install: UpdateInstallState,
    onInstallAgainClick: () -> Unit,
    onInstallDownloadedClick: () -> Unit,
    onCancelInstallClick: () -> Unit,
    onRefreshDownloadClick: () -> Unit,
    onInstallFailureDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BoltTheme.spacings.double),
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
    ) {
        when (install) {
            UpdateInstallState.Idle -> Unit

            is UpdateInstallState.Downloading -> {
                AboutUpdateDownloadProgress(
                    install = install,
                    onRefreshClick = onRefreshDownloadClick,
                    onCancelClick = onCancelInstallClick,
                )
            }

            is UpdateInstallState.ReadyToInstall -> {
                AboutUpdateReadyToInstall(
                    install = install,
                    onInstallDownloadedClick = onInstallDownloadedClick,
                )
            }

            UpdateInstallState.Verifying -> {
                AboutUpdateProgress(text = stringResource(R.string.about_updates_verifying))
            }

            UpdateInstallState.WaitingForConfirmation -> {
                TextBodyMedium(text = stringResource(R.string.about_updates_waiting_for_confirmation))
                Row(horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half)) {
                    DisableSelection {
                        ButtonFilled(
                            text = stringResource(R.string.about_updates_install_again_button),
                            onClick = onInstallAgainClick,
                        )
                    }
                    DisableSelection {
                        ButtonOutlined(
                            text = stringResource(R.string.about_updates_cancel_button),
                            onClick = onCancelInstallClick,
                        )
                    }
                }
            }

            UpdateInstallState.Installing -> {
                AboutUpdateProgress(text = stringResource(R.string.about_updates_installing_progress))
                TextBodyMedium(text = stringResource(R.string.about_updates_installing_hint))
            }

            is UpdateInstallState.Failed -> {
                AboutUpdateInstallFailure(
                    reason = install.reason,
                    onDismissClick = onInstallFailureDismiss,
                )
            }
        }
    }
}

/**
 * The message that the update was downloaded and the button that starts its installation.
 */
@Composable
private fun AboutUpdateReadyToInstall(
    install: UpdateInstallState.ReadyToInstall,
    onInstallDownloadedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
    ) {
        TextBodyMedium(text = stringResource(R.string.about_updates_ready_to_install, install.tag))
        DisableSelection {
            ButtonFilled(
                text = stringResource(R.string.about_updates_install_now_button),
                onClick = onInstallDownloadedClick,
            )
        }
    }
}

/**
 * The message that the installation failed and the button that hides it.
 */
@Composable
private fun AboutUpdateInstallFailure(
    reason: UpdateInstallFailure,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
    ) {
        TextBodyMedium(text = stringResource(installFailureMessageRes(reason)))
        DisableSelection {
            ButtonOutlined(
                text = stringResource(R.string.about_updates_dismiss_button),
                onClick = onDismissClick,
            )
        }
    }
}

/**
 * Shows the progress of a running download: the size downloaded so far, the total size and the percentage.
 */
@Composable
private fun AboutUpdateDownloadProgress(
    install: UpdateInstallState.Downloading,
    onRefreshClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val status = downloadStatus(
        downloadedBytes = install.downloadedBytes,
        totalBytes = install.totalBytes,
        waiting = install.waiting,
        formatSize = { bytes -> Formatter.formatShortFileSize(context, bytes) },
    )

    AboutUpdateProgress(
        text = stringResource(status.progressTextRes, *status.progressTextArgs.toTypedArray()),
        modifier = modifier,
        progress = status.percent?.let { it / PERCENT.toFloat() },
        waitingText = status.waitingTextRes?.let { stringResource(it) },
        onRefreshClick = onRefreshClick,
        onCancelClick = onCancelClick,
    )
}

/**
 * Shows the progress of a running update step. The progress indicator is indeterminate when [progress] is `null`.
 * [waitingText] is shown below the progress. The buttons are only shown when their action isn't `null`.
 */
@Composable
private fun AboutUpdateProgress(
    text: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    waitingText: String? = null,
    onRefreshClick: (() -> Unit)? = null,
    onCancelClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (progress == null) {
                CircularProgressIndicator(modifier = Modifier.size(INDICATOR_SIZE.dp))
            } else {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(INDICATOR_SIZE.dp),
                )
            }
            Spacer(modifier = Modifier.width(BoltTheme.spacings.half))
            TextBodyMedium(text = text)
        }

        waitingText?.let {
            TextBodyMedium(text = it)
        }

        if (onRefreshClick != null || onCancelClick != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half)) {
                onRefreshClick?.let { click ->
                    DisableSelection {
                        ButtonOutlined(
                            text = stringResource(R.string.about_updates_check_status_button),
                            onClick = click,
                        )
                    }
                }
                onCancelClick?.let { click ->
                    DisableSelection {
                        ButtonOutlined(
                            text = stringResource(R.string.about_updates_cancel_button),
                            onClick = click,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The message that explains why the installation of the update failed.
 */
internal fun installFailureMessageRes(reason: UpdateInstallFailure): Int {
    return when (reason) {
        UpdateInstallFailure.DOWNLOAD -> R.string.about_updates_install_failed_download
        UpdateInstallFailure.CHECKSUM -> R.string.about_updates_install_failed_checksum
        UpdateInstallFailure.PERMISSION_MISSING -> R.string.about_updates_install_failed_permission_missing
        UpdateInstallFailure.CANCELLED -> R.string.about_updates_install_failed_cancelled
        UpdateInstallFailure.REJECTED -> R.string.about_updates_install_failed_rejected
    }
}

/**
 * Collects the information about a running download that the UI shows.
 */
internal fun downloadStatus(
    downloadedBytes: Long,
    totalBytes: Long,
    waiting: DownloadWaiting?,
    formatSize: (Long) -> String,
): DownloadStatus {
    val downloadedSize = formatSize(downloadedBytes)
    val percent = calculateDownloadPercent(downloadedBytes, totalBytes)
    val waitingTextRes = when (waiting) {
        null -> null
        DownloadWaiting.NETWORK -> R.string.about_updates_downloading_waiting_network
        DownloadWaiting.WIFI -> R.string.about_updates_downloading_waiting_wifi
        DownloadWaiting.RETRY -> R.string.about_updates_downloading_waiting_retry
        DownloadWaiting.PAUSED -> R.string.about_updates_downloading_waiting_paused
    }

    return if (percent == null) {
        DownloadStatus(
            progressTextRes = R.string.about_updates_downloading_progress_unknown,
            progressTextArgs = listOf(downloadedSize),
            percent = null,
            waitingTextRes = waitingTextRes,
        )
    } else {
        DownloadStatus(
            progressTextRes = R.string.about_updates_downloading_progress,
            progressTextArgs = listOf(downloadedSize, formatSize(totalBytes), percent),
            percent = percent,
            waitingTextRes = waitingTextRes,
        )
    }
}

/**
 * Returns the download progress in percent or `null` when the total size of the download isn't known.
 */
internal fun calculateDownloadPercent(downloadedBytes: Long, totalBytes: Long): Int? {
    if (totalBytes <= 0) {
        return null
    }

    val percent = (downloadedBytes * PERCENT) / totalBytes
    return percent.toInt().coerceIn(0, PERCENT)
}
