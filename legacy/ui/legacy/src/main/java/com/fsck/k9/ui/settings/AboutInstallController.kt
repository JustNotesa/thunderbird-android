package com.fsck.k9.ui.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
    fun start() {
        stateHolder.update { it.copy(canInstallPackages = installer.canInstallPackages()) }

        scope.launch {
            installer.state.collect { installState ->
                stateHolder.update { it.copy(install = installState) }
            }
        }
    }

    fun onInstallClick() {
        val state = stateHolder.state.value
        val update = (state?.check as? CheckStatus.Available)?.update
        val installState = state?.install
        val installPossible = installState is UpdateInstallState.Idle ||
            installState is UpdateInstallState.Failed ||
            installState is UpdateInstallState.WaitingForConfirmation
        if (update == null || !installPossible) {
            return
        }

        if (!installer.canInstallPackages()) {
            stateHolder.update { it.copy(canInstallPackages = false) }
            return
        }

        scope.launch { installer.install(update) }
    }

    fun onInstallDownloadedClick() {
        scope.launch { installer.installDownloaded() }
    }

    fun onCancelInstallClick() {
        installer.resetState()
    }

    fun onInstallFailureDismiss() {
        installer.resetState()
    }

    fun onOpenInstallPermissionClick() {
        installer.openInstallPermissionSettings()
    }

    fun onRefreshDownloadClick() {
        scope.launch { installer.refresh() }
    }

    fun onScreenResumed() {
        stateHolder.update { it.copy(canInstallPackages = installer.canInstallPackages()) }
        scope.launch { installer.refresh() }
    }
}
