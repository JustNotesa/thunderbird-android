package com.fsck.k9.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.thunderbird.core.common.provider.AppVersionProvider
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.AppUpdateChecker
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import net.thunderbird.feature.updater.api.AppUpdateSettings
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
) : AppUpdateChecker {
    var checkCount: Int = 0
        private set

    override suspend fun check(): UpdateCheckResult {
        checkCount++
        return checkResult
    }
}

internal class FakeAppUpdateSettings(
    initialAutoCheckEnabled: Boolean = true,
) : AppUpdateSettings {
    private val autoCheckEnabledState = MutableStateFlow(initialAutoCheckEnabled)
    override val autoCheckEnabled: StateFlow<Boolean> = autoCheckEnabledState.asStateFlow()

    var setAutoCheckEnabledCount: Int = 0
        private set

    override fun setAutoCheckEnabled(enabled: Boolean) {
        setAutoCheckEnabledCount++
        autoCheckEnabledState.value = enabled
    }
}

internal class FakeAppUpdateInstaller(
    initialState: UpdateInstallState = UpdateInstallState.Idle,
    var canInstallPackages: Boolean = true,
) : AppUpdateInstaller {
    private val installState = MutableStateFlow(initialState)
    override val state: StateFlow<UpdateInstallState> = installState.asStateFlow()

    val installedUpdates: MutableList<AppUpdate> = mutableListOf()
    var openInstallPermissionSettingsCount: Int = 0
        private set
    var resetStateCount: Int = 0
        private set

    override fun canInstallPackages(): Boolean = this.canInstallPackages

    override fun openInstallPermissionSettings() {
        openInstallPermissionSettingsCount++
    }

    override suspend fun install(update: AppUpdate) {
        installedUpdates += update
    }

    override fun resetState() {
        resetStateCount++
        installState.value = UpdateInstallState.Idle
    }

    fun setInstallState(newState: UpdateInstallState) {
        installState.value = newState
    }
}
