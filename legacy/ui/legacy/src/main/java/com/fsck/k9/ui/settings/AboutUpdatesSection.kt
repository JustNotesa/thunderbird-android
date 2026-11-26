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
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fsck.k9.ui.R
import net.thunderbird.components.ui.bolt.atom.CircularProgressIndicator
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilled
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.atom.button.ButtonText
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.atom.text.TextTitleMedium
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.UpdateCheckFailure
import net.thunderbird.feature.updater.api.UpdateInstallState

internal const val INDICATOR_SIZE = 24

@Composable
internal fun AboutUpdatesSection(
    state: UpdatesState,
    onBackgroundDownloadChange: (Boolean) -> Unit,
    onDownloadOnlyOnWifiChange: (Boolean) -> Unit,
    onAutoInstallWhenIdleChange: (Boolean) -> Unit,
    onInstalledVersionClick: () -> Unit,
    onRetryInstalledNotes: () -> Unit,
    onInstalledNotesDismiss: () -> Unit,
    onCheckClick: () -> Unit,
    onInstallClick: () -> Unit,
    onInstallDownloadedClick: () -> Unit,
    onCancelInstallClick: () -> Unit,
    onOpenInstallPermissionClick: () -> Unit,
    onInstallFailureDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val availableUpdate = (state.check as? CheckStatus.Available)?.update
    val installInProgress = state.install !is UpdateInstallState.Idle && state.install !is UpdateInstallState.Failed
    val updateDownloaded = state.install is UpdateInstallState.ReadyToInstall

    SelectionContainer(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
        ) {
            SectionTitle(title = stringResource(R.string.about_updates_title))

            AboutUpdateInstalledVersionRow(
                versionTag = state.currentVersionTag,
                onClick = onInstalledVersionClick,
            )

            DisableSelection {
                AboutUpdateSettings(
                    state = state,
                    onBackgroundDownloadChange = onBackgroundDownloadChange,
                    onDownloadOnlyOnWifiChange = onDownloadOnlyOnWifiChange,
                    onAutoInstallWhenIdleChange = onAutoInstallWhenIdleChange,
                )
            }

            AboutUpdateCheckStatus(
                check = state.check,
                onCheckClick = onCheckClick,
            )

            AboutUpdateAvailable(
                update = availableUpdate,
                showInstallButton = state.install !is UpdateInstallState.Installing && !updateDownloaded,
                installEnabled = !installInProgress && state.canInstallPackages,
                onInstallClick = onInstallClick,
            )

            AboutUpdateInstallStatus(
                install = state.install,
                onInstallAgainClick = onInstallClick,
                onInstallDownloadedClick = onInstallDownloadedClick,
                onCancelInstallClick = onCancelInstallClick,
                onInstallFailureDismiss = onInstallFailureDismiss,
            )

            if (!state.canInstallPackages) {
                AboutUpdateInstallPermission(onOpenInstallPermissionClick = onOpenInstallPermissionClick)
            }
        }
    }

    AboutUpdateInstalledNotesDialog(
        notes = state.installedNotes,
        onRetryClick = onRetryInstalledNotes,
        onOpenUrl = onOpenUrl,
        onDismiss = onInstalledNotesDismiss,
    )
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
                TextBodyMedium(text = stringResource(checkFailureMessageRes(check.reason)))
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
    var showNotes by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BoltTheme.spacings.double),
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
    ) {
        TextTitleMedium(text = stringResource(R.string.about_updates_available_headline))
        TextTitleMedium(text = update.title)
        TextBodyMedium(text = stringResource(R.string.about_updates_size, size))
        if (formatReleaseNotes(update.releaseNotes, shorten = false).isNotBlank()) {
            DisableSelection {
                ButtonText(
                    text = stringResource(R.string.about_updates_show_release_notes),
                    onClick = { showNotes = true },
                )
            }
        }
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

    if (showNotes) {
        AboutUpdateReleaseNotesDialog(
            title = update.title,
            releaseNotes = update.releaseNotes,
            onDismiss = { showNotes = false },
        )
    }
}

/**
 * The explanation that the app needs the permission to install apps and the button that opens the system settings.
 */
@Composable
private fun AboutUpdateInstallPermission(
    onOpenInstallPermissionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = BoltTheme.spacings.double),
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

/**
 * The message that explains why the check for a newer release failed.
 */
internal fun checkFailureMessageRes(reason: UpdateCheckFailure): Int {
    return when (reason) {
        UpdateCheckFailure.NETWORK -> R.string.about_updates_check_failed_network
        UpdateCheckFailure.SERVER -> R.string.about_updates_check_failed_server
        UpdateCheckFailure.INVALID_RESPONSE -> R.string.about_updates_check_failed_invalid_response
        UpdateCheckFailure.NO_APK -> R.string.about_updates_check_failed_no_apk
    }
}
