package com.fsck.k9.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fsck.k9.ui.R
import net.thunderbird.components.ui.bolt.atom.Switch
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.theme.BoltTheme

/**
 * The switches that control how the app looks for updates and what it does with them.
 *
 * The background download and the automatic installation are only useful when the app checks for updates, the
 * Wi-Fi setting only matters while the update is downloaded in the background.
 */
@Composable
internal fun AboutUpdateSettings(
    state: UpdatesState,
    onAutoCheckChange: (Boolean) -> Unit,
    onBackgroundDownloadChange: (Boolean) -> Unit,
    onDownloadOnlyOnWifiChange: (Boolean) -> Unit,
    onAutoInstallWhenIdleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.quarter),
    ) {
        AboutUpdateSwitchRow(
            label = stringResource(R.string.about_updates_auto_check),
            checked = state.autoCheckEnabled,
            onCheckedChange = onAutoCheckChange,
        )
        AboutUpdateSwitchRow(
            label = stringResource(R.string.about_updates_background_download),
            checked = state.backgroundDownloadEnabled,
            enabled = state.autoCheckEnabled,
            onCheckedChange = onBackgroundDownloadChange,
        )
        AboutUpdateSwitchRow(
            label = stringResource(R.string.about_updates_only_on_wifi),
            checked = state.downloadOnlyOnWifi,
            enabled = state.backgroundDownloadEnabled,
            onCheckedChange = onDownloadOnlyOnWifiChange,
        )
        AboutUpdateSwitchRow(
            label = stringResource(R.string.about_updates_auto_install_when_idle),
            checked = state.autoInstallWhenIdle,
            enabled = state.autoCheckEnabled && state.backgroundDownloadEnabled,
            onCheckedChange = onAutoInstallWhenIdleChange,
        )
        TextBodyMedium(
            text = stringResource(R.string.about_updates_auto_install_when_idle_hint),
            modifier = Modifier.padding(
                horizontal = BoltTheme.spacings.double,
                vertical = BoltTheme.spacings.quarter,
            ),
        )
    }
}

/**
 * A row with the label of a setting and the switch that turns it on and off.
 */
@Composable
internal fun AboutUpdateSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
            text = label,
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
