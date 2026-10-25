package com.fsck.k9.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import net.thunderbird.feature.updater.api.UpdateCheckFailure
import net.thunderbird.feature.updater.api.UpdateInstallFailure
import net.thunderbird.feature.updater.api.UpdateInstallState

private const val MAX_RELEASE_NOTES_LENGTH = 300
private const val PERCENT = 100
private const val INDICATOR_SIZE = 24

@Composable
internal fun AboutUpdatesSection(
    state: UpdatesState,
    onAutoCheckChange: (Boolean) -> Unit,
    onCheckClick: () -> Unit,
    onInstallClick: () -> Unit,
    onCancelInstallClick: () -> Unit,
    onOpenInstallPermissionClick: () -> Unit,
    onInstallFailureDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val availableUpdate = (state.check as? CheckStatus.Available)?.update
    val installInProgress = state.install !is UpdateInstallState.Idle && state.install !is UpdateInstallState.Failed

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
    ) {
        SectionTitle(title = stringResource(R.string.about_updates_title))

        AboutUpdateInfoRow(
            label = stringResource(R.string.about_updates_installed_version),
            value = state.currentVersionTag,
        )

        AboutUpdateAutoCheckRow(
            enabled = state.autoCheckEnabled,
            onAutoCheckChange = onAutoCheckChange,
        )

        AboutUpdateCheckStatus(
            check = state.check,
            onCheckClick = onCheckClick,
        )

        AboutUpdateAvailable(
            update = availableUpdate,
            installEnabled = !installInProgress && state.canInstallPackages,
            onInstallClick = onInstallClick,
        )

        AboutUpdateInstallStatus(
            install = state.install,
            canInstallPackages = state.canInstallPackages,
            onInstallAgainClick = onInstallClick,
            onCancelInstallClick = onCancelInstallClick,
            onOpenInstallPermissionClick = onOpenInstallPermissionClick,
            onInstallFailureDismiss = onInstallFailureDismiss,
        )
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
                ButtonOutlined(
                    text = stringResource(R.string.about_updates_check_button),
                    onClick = onCheckClick,
                )
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
                ButtonOutlined(
                    text = stringResource(R.string.about_updates_check_again_button),
                    onClick = onCheckClick,
                )
            }

            is CheckStatus.Available -> Unit

            is CheckStatus.Failed -> {
                TextBodyMedium(text = checkFailureMessage(check.reason))
                ButtonOutlined(
                    text = stringResource(R.string.about_updates_retry_button),
                    onClick = onCheckClick,
                )
            }
        }
    }
}

@Composable
private fun AboutUpdateAvailable(
    update: AppUpdate?,
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
        ButtonFilled(
            text = stringResource(R.string.about_updates_install_button),
            onClick = onInstallClick,
            enabled = installEnabled,
        )
    }
}

@Composable
private fun AboutUpdateInstallStatus(
    install: UpdateInstallState,
    canInstallPackages: Boolean,
    onInstallAgainClick: () -> Unit,
    onCancelInstallClick: () -> Unit,
    onOpenInstallPermissionClick: () -> Unit,
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
                AboutUpdateDownloading(downloading = install)
            }

            UpdateInstallState.Verifying -> {
                TextBodyMedium(text = stringResource(R.string.about_updates_verifying))
            }

            UpdateInstallState.WaitingForConfirmation -> {
                TextBodyMedium(text = stringResource(R.string.about_updates_waiting_for_confirmation))
                Row(horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half)) {
                    ButtonFilled(
                        text = stringResource(R.string.about_updates_install_again_button),
                        onClick = onInstallAgainClick,
                    )
                    ButtonOutlined(
                        text = stringResource(R.string.about_updates_cancel_button),
                        onClick = onCancelInstallClick,
                    )
                }
            }

            is UpdateInstallState.Failed -> {
                TextBodyMedium(text = installFailureMessage(install.reason))
                ButtonOutlined(
                    text = stringResource(R.string.about_updates_dismiss_button),
                    onClick = onInstallFailureDismiss,
                )
            }
        }

        if (!canInstallPackages) {
            TextBodyMedium(text = stringResource(R.string.about_updates_install_permission_explanation))
            ButtonOutlined(
                text = stringResource(R.string.about_updates_open_settings_button),
                onClick = onOpenInstallPermissionClick,
            )
        }
    }
}

@Composable
private fun AboutUpdateDownloading(
    downloading: UpdateInstallState.Downloading,
    modifier: Modifier = Modifier,
) {
    val percent = calculateDownloadPercent(
        downloadedBytes = downloading.downloadedBytes,
        totalBytes = downloading.totalBytes,
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (percent == null) {
            CircularProgressIndicator(modifier = Modifier.size(INDICATOR_SIZE.dp))
        } else {
            CircularProgressIndicator(
                progress = { percent / PERCENT.toFloat() },
                modifier = Modifier.size(INDICATOR_SIZE.dp),
            )
        }
        Spacer(modifier = Modifier.width(BoltTheme.spacings.half))
        val progressText = if (percent == null) {
            stringResource(R.string.about_updates_downloading_indeterminate)
        } else {
            stringResource(R.string.about_updates_downloading, percent)
        }
        TextBodyMedium(text = progressText)
    }
}

@Composable
private fun checkFailureMessage(reason: UpdateCheckFailure): String {
    val stringRes = when (reason) {
        UpdateCheckFailure.NETWORK -> R.string.about_updates_check_failed_network
        UpdateCheckFailure.SERVER -> R.string.about_updates_check_failed_server
        UpdateCheckFailure.INVALID_RESPONSE -> R.string.about_updates_check_failed_invalid_response
        UpdateCheckFailure.NO_APK -> R.string.about_updates_check_failed_no_apk
    }

    return stringResource(stringRes)
}

@Composable
private fun installFailureMessage(reason: UpdateInstallFailure): String {
    val stringRes = when (reason) {
        UpdateInstallFailure.DOWNLOAD -> R.string.about_updates_install_failed_download
        UpdateInstallFailure.CHECKSUM -> R.string.about_updates_install_failed_checksum
        UpdateInstallFailure.PERMISSION_MISSING -> R.string.about_updates_install_failed_permission_missing
        UpdateInstallFailure.CANCELLED -> R.string.about_updates_install_failed_cancelled
        UpdateInstallFailure.REJECTED -> R.string.about_updates_install_failed_rejected
    }

    return stringResource(stringRes)
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
