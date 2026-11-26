package com.fsck.k9.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.thunderbird.core.common.provider.AppVersionProvider
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.AppUpdateChecker
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import net.thunderbird.feature.updater.api.AppUpdateSettings
import net.thunderbird.feature.updater.api.ReleaseNotesResult
import net.thunderbird.feature.updater.api.UpdateCheckResult
import net.thunderbird.feature.updater.api.UpdateInstallState

internal class FakeAppVersionProvider(
    private val versionNumber: String = "9.9.9",
) : AppVersionProvider {
    override fun getVersionNumber(): String = versionNumber
}

internal class FakeAppUpdateChecker(
    override val isEnabled: Boolean = true,
    override val currentReleaseTag: String = "mein-thunderbird-2026-10-25",
    var checkResult: UpdateCheckResult = UpdateCheckResult.UpToDate,
    var releaseNotesResult: ReleaseNotesResult = ReleaseNotesResult.Disabled,
) : AppUpdateChecker {
    var checkCount: Int = 0
        private set
    var installedReleaseNotesCount: Int = 0
        private set

    override suspend fun check(): UpdateCheckResult {
        checkCount++
        return checkResult
    }

    override suspend fun installedReleaseNotes(): ReleaseNotesResult {
        installedReleaseNotesCount++
        return releaseNotesResult
    }
}

internal class FakeAppUpdateSettings(
    initialBackgroundDownloadEnabled: Boolean = false,
    initialDownloadOnlyOnWifi: Boolean = true,
    initialAutoInstallWhenIdle: Boolean = false,
) : AppUpdateSettings {
    private val backgroundDownloadEnabledState = MutableStateFlow(initialBackgroundDownloadEnabled)
    override val backgroundDownloadEnabled: StateFlow<Boolean> = backgroundDownloadEnabledState.asStateFlow()

    private val downloadOnlyOnWifiState = MutableStateFlow(initialDownloadOnlyOnWifi)
    override val downloadOnlyOnWifi: StateFlow<Boolean> = downloadOnlyOnWifiState.asStateFlow()

    private val autoInstallWhenIdleState = MutableStateFlow(initialAutoInstallWhenIdle)
    override val autoInstallWhenIdle: StateFlow<Boolean> = autoInstallWhenIdleState.asStateFlow()

    var setBackgroundDownloadEnabledCount: Int = 0
        private set
    var setDownloadOnlyOnWifiCount: Int = 0
        private set
    var setAutoInstallWhenIdleCount: Int = 0
        private set

    override fun setBackgroundDownloadEnabled(enabled: Boolean) {
        setBackgroundDownloadEnabledCount++
        backgroundDownloadEnabledState.value = enabled
    }

    override fun setDownloadOnlyOnWifi(enabled: Boolean) {
        setDownloadOnlyOnWifiCount++
        downloadOnlyOnWifiState.value = enabled
    }

    override fun setAutoInstallWhenIdle(enabled: Boolean) {
        setAutoInstallWhenIdleCount++
        autoInstallWhenIdleState.value = enabled
    }
}

internal class FakeAppUpdateInstaller(
    initialState: UpdateInstallState = UpdateInstallState.Idle,
    var canInstallPackages: Boolean = true,
) : AppUpdateInstaller {
    private val installState = MutableStateFlow(initialState)
    override val state: StateFlow<UpdateInstallState> = installState.asStateFlow()

    val installedUpdates: MutableList<AppUpdate> = mutableListOf()
    val downloadedUpdates: MutableList<AppUpdate> = mutableListOf()
    var openInstallPermissionSettingsCount: Int = 0
        private set
    var resetStateCount: Int = 0
        private set
    var refreshCount: Int = 0
        private set
    var installDownloadedCount: Int = 0
        private set
    var reopenAppAfterInstallCount: Int = 0
        private set

    override fun canInstallPackages(): Boolean = this.canInstallPackages

    override fun openInstallPermissionSettings() {
        openInstallPermissionSettingsCount++
    }

    override fun reopenAppAfterInstall() {
        reopenAppAfterInstallCount++
    }

    override suspend fun install(update: AppUpdate) {
        installedUpdates += update
    }

    override suspend fun download(update: AppUpdate, onlyOnWifi: Boolean) {
        downloadedUpdates += update
    }

    override suspend fun installDownloaded() {
        installDownloadedCount++
    }

    override suspend fun refresh() {
        refreshCount++
    }

    override fun resetState() {
        resetStateCount++
        installState.value = UpdateInstallState.Idle
    }

    fun setInstallState(newState: UpdateInstallState) {
        installState.value = newState
    }
}
