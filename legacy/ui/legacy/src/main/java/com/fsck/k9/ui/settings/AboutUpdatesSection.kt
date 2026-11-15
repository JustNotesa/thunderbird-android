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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fsck.k9.ui.R
import net.thunderbird.components.ui.bolt.atom.CircularProgressIndicator
import net.thunderbird.components.ui.bolt.atom.Switch
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilled
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.atom.text.TextTitleMedium
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.DownloadWaiting
import net.thunderbird.feature.updater.api.UpdateCheckFailure
import net.thunderbird.feature.updater.api.UpdateInstallFailure
import net.thunderbird.feature.updater.api.UpdateInstallState

private const val MAX_RELEASE_NOTES_LENGTH = 300
private const val PERCENT = 100
private const val INDICATOR_SIZE = 24

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

@Composable
internal fun AboutUpdatesSection(
    state: UpdatesState,
    onAutoCheckChange: (Boolean) -> Unit,
    onCheckClick: () -> Unit,
    onInstallClick: () -> Unit,
    onCancelInstallClick: () -> Unit,
    onRefreshDownloadClick: () -> Unit,
    onOpenInstallPermissionClick: () -> Unit,
    onInstallFailureDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val availableUpdate = (state.check as? CheckStatus.Available)?.update
    val installInProgress = state.install !is UpdateInstallState.Idle && state.install !is UpdateInstallState.Failed

    SelectionContainer(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
        ) {
            SectionTitle(title = stringResource(R.string.about_updates_title))

            AboutUpdateInfoRow(
                label = stringResource(R.string.about_updates_installed_version),
                value = state.currentVersionTag,
            )

            DisableSelection {
                AboutUpdateAutoCheckRow(
                    enabled = state.autoCheckEnabled,
                    onAutoCheckChange = onAutoCheckChange,
                )
            }

            AboutUpdateCheckStatus(
                check = state.check,
                onCheckClick = onCheckClick,
            )

            AboutUpdateAvailable(
                update = availableUpdate,
                showInstallButton = state.install !is UpdateInstallState.Installing,
                installEnabled = !installInProgress && state.canInstallPackages,
                onInstallClick = onInstallClick,
            )

            AboutUpdateInstallStatus(
                install = state.install,
                onInstallAgainClick = onInstallClick,
                onCancelInstallClick = onCancelInstallClick,
                onRefreshDownloadClick = onRefreshDownloadClick,
                onInstallFailureDismiss = onInstallFailureDismiss,
            )

            if (!state.canInstallPackages) {
                Column(
                    modifier = Modifier.padding(horizontal = BoltTheme.spacings.double),
                    verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
                ) {
                    TextBodyMedium(text = stringResource(R.string.about_updates_install_permission_explanation))
                    DisableSelection {
                        ButtonOutlined(
                            text = stringResource(R.string.about_updates_open_settings_button),
                            onClick = onOpenInstallPermissionClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutUpdateInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = BoltTheme.spacings.double,
                vertical = BoltTheme.spacings.half,
            ),
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.quarter),
    ) {
        TextTitleMedium(text = label)
        TextBodyMedium(text = value)
    }
}

@Composable
private fun AboutUpdateAutoCheckRow(
    enabled: Boolean,
    onAutoCheckChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = BoltTheme.spacings.double,
                vertical = BoltTheme.spacings.half,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextBodyMedium(
            text = stringResource(R.string.about_updates_auto_check),
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(BoltTheme.spacings.default))
        Switch(
            checked = enabled,
            onCheckedChange = onAutoCheckChange,
        )
    }
}

@Composable
private fun AboutUpdateCheckStatus(
    check: CheckStatus,
    onCheckClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BoltTheme.spacings.double),
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
    ) {
        when (check) {
            CheckStatus.Idle -> {
                DisableSelection {
                    ButtonOutlined(
                        text = stringResource(R.string.about_updates_check_button),
                        onClick = onCheckClick,
                    )
                }
            }

            CheckStatus.Checking -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(INDICATOR_SIZE.dp))
                    Spacer(modifier = Modifier.width(BoltTheme.spacings.half))
                    TextBodyMedium(text = stringResource(R.string.about_updates_checking))
                }
            }

            CheckStatus.UpToDate -> {
                TextBodyMedium(text = stringResource(R.string.about_updates_up_to_date))
                DisableSelection {
                    ButtonOutlined(
                        text = stringResource(R.string.about_updates_check_again_button),
                        onClick = onCheckClick,
                    )
                }
            }

            is CheckStatus.Available -> Unit

            is CheckStatus.Failed -> {
                val messageRes = when (check.reason) {
                    UpdateCheckFailure.NETWORK -> R.string.about_updates_check_failed_network
                    UpdateCheckFailure.SERVER -> R.string.about_updates_check_failed_server
                    UpdateCheckFailure.INVALID_RESPONSE -> R.string.about_updates_check_failed_invalid_response
                    UpdateCheckFailure.NO_APK -> R.string.about_updates_check_failed_no_apk
                }

                TextBodyMedium(text = stringResource(messageRes))
                DisableSelection {
                    ButtonOutlined(
                        text = stringResource(R.string.about_updates_retry_button),
                        onClick = onCheckClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutUpdateAvailable(
    update: AppUpdate?,
    showInstallButton: Boolean,
    installEnabled: Boolean,
    onInstallClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (update == null) return

    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, update.apkSizeBytes)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BoltTheme.spacings.double),
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
    ) {
        TextTitleMedium(text = stringResource(R.string.about_updates_available_headline))
        TextTitleMedium(text = update.title)
        TextBodyMedium(text = stringResource(R.string.about_updates_release_notes_label))
        TextBodyMedium(text = formatReleaseNotes(update.releaseNotes))
        TextBodyMedium(text = stringResource(R.string.about_updates_size, size))
        if (showInstallButton) {
            DisableSelection {
                ButtonFilled(
                    text = stringResource(R.string.about_updates_install_button),
                    onClick = onInstallClick,
                    enabled = installEnabled,
                )
            }
        }
    }
}

@Composable
private fun AboutUpdateInstallStatus(
    install: UpdateInstallState,
    onInstallAgainClick: () -> Unit,
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
                val messageRes = when (install.reason) {
                    UpdateInstallFailure.DOWNLOAD -> R.string.about_updates_install_failed_download
                    UpdateInstallFailure.CHECKSUM -> R.string.about_updates_install_failed_checksum
                    UpdateInstallFailure.PERMISSION_MISSING ->
                        R.string.about_updates_install_failed_permission_missing
                    UpdateInstallFailure.CANCELLED -> R.string.about_updates_install_failed_cancelled
                    UpdateInstallFailure.REJECTED -> R.string.about_updates_install_failed_rejected
                }

                TextBodyMedium(text = stringResource(messageRes))
                DisableSelection {
                    ButtonOutlined(
                        text = stringResource(R.string.about_updates_dismiss_button),
                        onClick = onInstallFailureDismiss,
                    )
                }
            }
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

/**
 * Returns the release notes without Markdown headings, shortened to [MAX_RELEASE_NOTES_LENGTH] characters.
 */
internal fun formatReleaseNotes(releaseNotes: String): String {
    val withoutHeadings = releaseNotes.lineSequence()
        .filterNot { line -> line.trimStart().startsWith("#") }
        .joinToString(separator = "\n")
        .trim()

    return if (withoutHeadings.length > MAX_RELEASE_NOTES_LENGTH) {
        withoutHeadings.take(MAX_RELEASE_NOTES_LENGTH).trimEnd() + "…"
    } else {
        withoutHeadings
    }
}
