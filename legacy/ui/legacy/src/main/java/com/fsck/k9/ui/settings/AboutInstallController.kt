package com.fsck.k9.ui.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import net.thunderbird.feature.updater.api.UpdateInstallState

/**
 * Downloads and installs an update and reacts to the user's actions.
 */
internal class AboutInstallController(
    private val installer: AppUpdateInstaller,
    private val stateHolder: AboutUpdatesStateHolder,
    private val scope: CoroutineScope,
) {
    /** Whether the install permission was requested from the updater, so that the installation continues after it. */
    private var installAfterPermissionGranted = false

    fun start() {
        stateHolder.update { it.copy(canInstallPackages = installer.canInstallPackages()) }

        scope.launch {
            installer.state.collect { installState ->
                stateHolder.update { it.copy(install = installState) }
            }
        }
    }

    fun onInstallClick() {
        val update = installableUpdate()
        if (update == null) {
            return
        }

        if (!installer.canInstallPackages()) {
            stateHolder.update { it.copy(canInstallPackages = false) }
            return
        }

        installer.reopenAppAfterInstall()
        scope.launch { installer.install(update) }
    }

    fun onInstallDownloadedClick() {
        installer.reopenAppAfterInstall()
        scope.launch { installer.installDownloaded() }
    }

    fun onCancelInstallClick() {
        installer.resetState()
    }

    fun onInstallFailureDismiss() {
        installer.resetState()
    }

    /**
     * Clears the failure of an earlier installation. A download or an installation that is in progress is kept.
     */
    fun resetFailedInstall() {
        if (installer.state.value is UpdateInstallState.Failed) {
            installer.resetState()
        }
    }

    fun onOpenInstallPermissionClick() {
        // The installation continues by itself when the user comes back with the permission.
        installAfterPermissionGranted = installableUpdate() != null
        installer.openInstallPermissionSettings()
    }

    fun onScreenResumed() {
        val canInstallPackages = installer.canInstallPackages()
        stateHolder.update { it.copy(canInstallPackages = canInstallPackages) }

        // The permission is only waited for once: without it the user has to ask for the installation again.
        val continueInstall = installAfterPermissionGranted && canInstallPackages
        installAfterPermissionGranted = false
        if (continueInstall) {
            onInstallClick()
        }

        scope.launch { installer.refresh() }
    }

    /** The available update, `null` when there is none or when no installation can be started right now. */
    private fun installableUpdate(): AppUpdate? {
        val state = stateHolder.state.value
        val installState = state?.install
        val installPossible = installState is UpdateInstallState.Idle ||
            installState is UpdateInstallState.Failed ||
            installState is UpdateInstallState.WaitingForConfirmation
        return (state?.check as? CheckStatus.Available)?.update?.takeIf { installPossible }
    }
}
