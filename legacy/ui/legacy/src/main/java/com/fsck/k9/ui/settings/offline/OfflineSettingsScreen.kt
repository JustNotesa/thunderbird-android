package com.fsck.k9.ui.settings.offline

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fsck.k9.job.OfflineDownloadState
import com.fsck.k9.job.OfflineDownloadStatus
import com.fsck.k9.ui.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.components.ui.bolt.atom.CircularProgressIndicator
import net.thunderbird.components.ui.bolt.atom.RadioGroup
import net.thunderbird.components.ui.bolt.atom.Surface
import net.thunderbird.components.ui.bolt.atom.Switch
import net.thunderbird.components.ui.bolt.atom.card.CardOutlined
import net.thunderbird.components.ui.bolt.atom.icon.Icon
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.atom.text.TextBodyLarge
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.atom.text.TextBodySmall
import net.thunderbird.components.ui.bolt.atom.text.TextTitleMedium
import net.thunderbird.components.ui.bolt.theme.BoltTheme

private const val NO_LIMIT_MEGABYTES = 0
private const val FIVE_HUNDRED_MEGABYTES = 500
private const val ONE_GIGABYTE_MEGABYTES = 1024
private const val TWO_GIGABYTES_MEGABYTES = 2048
private const val FIVE_GIGABYTES_MEGABYTES = 5120
private const val TEN_GIGABYTES_MEGABYTES = 10240

private const val INDICATOR_SIZE = 24

/**
 * A storage limit the user can choose from.
 */
private data class StorageOption(val megabytes: Int, val label: String)

@Composable
internal fun OfflineSettingsScreen(
    uiState: OfflineSettingsUiState,
    onEnabledChange: (Boolean) -> Unit,
    onOnlyWhileChargingChange: (Boolean) -> Unit,
    onStorageLimitChange: (Int) -> Unit,
    onKeepFreeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(BoltTheme.spacings.double),
            verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.double),
        ) {
            TextBodyMedium(text = stringResource(R.string.offline_settings_description))

            OfflineSwitchSetting(
                text = stringResource(R.string.offline_settings_download_all),
                checked = uiState.isEnabled,
                onCheckedChange = onEnabledChange,
            )

            OfflineSwitchSetting(
                text = stringResource(R.string.offline_settings_only_while_charging),
                checked = uiState.isOnlyWhileCharging,
                onCheckedChange = onOnlyWhileChargingChange,
                enabled = uiState.isEnabled,
            )

            MegabytesSetting(
                title = stringResource(R.string.offline_settings_storage_limit_title),
                options = storageLimitOptions(),
                selectedMegabytes = uiState.storageLimitMegabytes,
                onSelect = onStorageLimitChange,
            )

            MegabytesSetting(
                title = stringResource(R.string.offline_settings_keep_free_title),
                options = keepFreeOptions(),
                selectedMegabytes = uiState.keepFreeMegabytes,
                onSelect = onKeepFreeChange,
            )

            TextBodySmall(text = stringResource(R.string.offline_settings_note))

            OfflineStatusCard(
                status = uiState.status,
                usedBytes = uiState.usedBytes,
            )
        }
    }
}

@Composable
private fun OfflineSwitchSetting(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextBodyLarge(
            text = text,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(BoltTheme.spacings.default))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun MegabytesSetting(
    title: String,
    options: ImmutableList<StorageOption>,
    selectedMegabytes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TextTitleMedium(text = title)
        RadioGroup(
            onClick = { option -> onSelect(option.megabytes) },
            options = options,
            optionTitle = { option -> option.label },
            selectedOption = options.firstOrNull { option -> option.megabytes == selectedMegabytes },
        )
    }
}

@Composable
private fun storageLimitOptions(): ImmutableList<StorageOption> {
    return persistentListOf(
        StorageOption(NO_LIMIT_MEGABYTES, stringResource(R.string.offline_settings_storage_limit_none)),
        StorageOption(ONE_GIGABYTE_MEGABYTES, stringResource(R.string.offline_settings_storage_limit_1gb)),
        StorageOption(TWO_GIGABYTES_MEGABYTES, stringResource(R.string.offline_settings_storage_limit_2gb)),
        StorageOption(FIVE_GIGABYTES_MEGABYTES, stringResource(R.string.offline_settings_storage_limit_5gb)),
        StorageOption(TEN_GIGABYTES_MEGABYTES, stringResource(R.string.offline_settings_storage_limit_10gb)),
    )
}

@Composable
private fun keepFreeOptions(): ImmutableList<StorageOption> {
    return persistentListOf(
        StorageOption(FIVE_HUNDRED_MEGABYTES, stringResource(R.string.offline_settings_keep_free_500mb)),
        StorageOption(ONE_GIGABYTE_MEGABYTES, stringResource(R.string.offline_settings_keep_free_1gb)),
        StorageOption(TWO_GIGABYTES_MEGABYTES, stringResource(R.string.offline_settings_keep_free_2gb)),
        StorageOption(FIVE_GIGABYTES_MEGABYTES, stringResource(R.string.offline_settings_keep_free_5gb)),
    )
}

@Composable
private fun OfflineStatusCard(
    status: OfflineDownloadStatus,
    usedBytes: Long?,
    modifier: Modifier = Modifier,
) {
    CardOutlined(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(BoltTheme.spacings.double),
            verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
        ) {
            TextTitleMedium(text = stringResource(R.string.offline_settings_status_title))
            OfflineStatusState(state = status.state)

            val showFolderProgress = status.state == OfflineDownloadState.RUNNING ||
                status.state == OfflineDownloadState.WAITING
            if (showFolderProgress && status.folderCount > 0) {
                FolderProgress(
                    completeFolderCount = status.completeFolderCount,
                    folderCount = status.folderCount,
                )
            }

            OfflineStatusDetails(status = status, usedBytes = usedBytes)
        }
    }
}

@Composable
private fun OfflineStatusState(
    state: OfflineDownloadState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
    ) {
        when (state) {
            OfflineDownloadState.WAITING, OfflineDownloadState.RUNNING -> {
                CircularProgressIndicator(modifier = Modifier.size(INDICATOR_SIZE.dp))
            }

            OfflineDownloadState.PAUSED_STORAGE_LIMIT, OfflineDownloadState.PAUSED_LOW_STORAGE -> {
                Icon(imageVector = Icons.Outlined.Warning)
            }

            OfflineDownloadState.DONE -> {
                Icon(imageVector = Icons.Outlined.CheckCircle)
            }

            OfflineDownloadState.FAILED -> {
                Icon(imageVector = Icons.Outlined.ErrorOutline, tint = BoltTheme.colors.error)
            }

            OfflineDownloadState.OFF -> Unit
        }

        TextBodyMedium(text = stringResource(statusStringRes(state)))
    }
}

private fun statusStringRes(state: OfflineDownloadState): Int {
    return when (state) {
        OfflineDownloadState.OFF -> R.string.offline_status_off
        OfflineDownloadState.WAITING -> R.string.offline_status_waiting
        OfflineDownloadState.RUNNING -> R.string.offline_status_downloading
        OfflineDownloadState.PAUSED_STORAGE_LIMIT -> R.string.offline_status_paused_storage_limit
        OfflineDownloadState.PAUSED_LOW_STORAGE -> R.string.offline_status_paused_low_storage
        OfflineDownloadState.DONE -> R.string.offline_status_up_to_date
        OfflineDownloadState.FAILED -> R.string.offline_status_problem
    }
}

@Composable
private fun FolderProgress(
    completeFolderCount: Int,
    folderCount: Int,
    modifier: Modifier = Modifier,
) {
    val trackColor = BoltTheme.colors.surfaceContainerHighest
    val indicatorColor = BoltTheme.colors.primary
    val progress = (completeFolderCount.toFloat() / folderCount).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(BoltTheme.spacings.half),
        ) {
            val cornerRadius = CornerRadius(size.height / 2f)
            drawRoundRect(color = trackColor, cornerRadius = cornerRadius)
            drawRoundRect(
                color = indicatorColor,
                size = Size(width = size.width * progress, height = size.height),
                cornerRadius = cornerRadius,
            )
        }
        Spacer(modifier = Modifier.height(BoltTheme.spacings.quarter))
        TextBodySmall(
            text = pluralStringResource(
                R.plurals.offline_status_folder_progress,
                folderCount,
                completeFolderCount,
                folderCount,
            ),
        )
    }
}

@Composable
private fun OfflineStatusDetails(
    status: OfflineDownloadStatus,
    usedBytes: Long?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(modifier = modifier) {
        status.lastRunStart?.let { lastRunStart ->
            val duration = status.lastRunDurationMillis?.let { DateUtils.formatElapsedTime(it) }
            val lastRunText = if (duration != null) {
                stringResource(R.string.offline_status_last_run, relativeTime(lastRunStart), duration)
            } else {
                stringResource(R.string.offline_status_last_run_without_duration, relativeTime(lastRunStart))
            }
            TextBodySmall(text = lastRunText)
        }

        val lastCompleteTime = status.lastCompleteTime
        val completeText = if (lastCompleteTime != null) {
            stringResource(R.string.offline_status_complete_time, relativeTime(lastCompleteTime))
        } else {
            stringResource(R.string.offline_status_not_complete)
        }
        TextBodySmall(text = completeText)

        if (usedBytes != null) {
            TextBodySmall(
                text = stringResource(
                    R.string.offline_status_used_storage,
                    Formatter.formatShortFileSize(context, usedBytes),
                ),
            )
        }

        if (status.state == OfflineDownloadState.FAILED) {
            status.lastError?.let { lastError ->
                TextBodySmall(text = lastError, color = BoltTheme.colors.error)
            }
        }
    }
}

private fun relativeTime(time: Long): CharSequence {
    return DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
}
