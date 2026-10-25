package com.fsck.k9.ui.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.thunderbird.feature.updater.api.AppUpdateChecker
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import net.thunderbird.feature.updater.api.AppUpdateSettings
import net.thunderbird.feature.updater.api.UpdateCheckResult
import net.thunderbird.feature.updater.api.UpdateInstallState

/**
 * Holds the state of the "Updates" section of the about screen and reacts to the user's actions.
 *
 * The state is `null` as long as the updater isn't used by this build.
 */
internal class AboutUpdatesController(
    private val checker: AppUpdateChecker,
    private val settings: AppUpdateSettings,
    private val installer: AppUpdateInstaller,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<UpdatesState?>(null)
    val state: StateFlow<UpdatesState?> = _state.asStateFlow()

    fun start() {
        _state.value = UpdatesState(
            currentVersionTag = checker.currentReleaseTag,
            autoCheckEnabled = settings.autoCheckEnabled.value,
            check = CheckStatus.Idle,
            install = installer.state.value,
            canInstallPackages = installer.canInstallPackages(),
        )
        observeAutoCheckSetting()
        observeInstallState()
        scope.launch { runCheck() }
    }

    fun onAutoCheckChanged(enabled: Boolean) {
        settings.setAutoCheckEnabled(enabled)
    }

    fun onCheckForUpdatesClick() {
        val current = _state.value ?: return
        if (current.check is CheckStatus.Checking) return

        scope.launch { runCheck() }
    }

    fun onInstallClick() {
        val current = _state.value
        val update = (current?.check as? CheckStatus.Available)?.update
        val installState = current?.install
        val installPossible = installState is UpdateInstallState.Idle ||
            installState is UpdateInstallState.Failed ||
            installState is UpdateInstallState.WaitingForConfirmation
        if (update == null || !installPossible) {
            return
        }

        if (!installer.canInstallPackages()) {
            _state.update { state -> state?.copy(canInstallPackages = false) }
            return
        }

        scope.launch { installer.install(update) }
    }

    fun onCancelInstallClick() {
        installer.resetState()
    }

    fun onOpenInstallPermissionClick() {
        installer.openInstallPermissionSettings()
    }

    fun onScreenResumed() {
        _state.update { state -> state?.copy(canInstallPackages = installer.canInstallPackages()) }
    }

    fun onInstallFailureDismiss() {
        installer.resetState()
    }

    private fun observeAutoCheckSetting() {
        scope.launch {
            settings.autoCheckEnabled.collect { enabled ->
                _state.update { state -> state?.copy(autoCheckEnabled = enabled) }
            }
        }
    }

    private fun observeInstallState() {
        scope.launch {
            installer.state.collect { installState ->
                _state.update { state -> state?.copy(install = installState) }
            }
        }
    }

    private suspend fun runCheck() {
        _state.update { state -> state?.copy(check = CheckStatus.Checking) }

        val status = when (val result = checker.check()) {
            is UpdateCheckResult.Available -> CheckStatus.Available(result.update)
            is UpdateCheckResult.Failed -> CheckStatus.Failed(result.reason)
            UpdateCheckResult.UpToDate -> CheckStatus.UpToDate
            UpdateCheckResult.Disabled -> null
        }

        if (status == null) {
            _state.value = null
        } else {
            _state.update { state -> state?.copy(check = status) }
        }
    }
}
