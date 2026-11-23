package com.fsck.k9.ui.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.thunderbird.feature.updater.api.AppUpdateChecker
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import net.thunderbird.feature.updater.api.AppUpdateSettings
import net.thunderbird.feature.updater.api.ReleaseNotesResult
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
    installer: AppUpdateInstaller,
    private val scope: CoroutineScope,
) {
    private val stateHolder = AboutUpdatesStateHolder()

    /** Downloads and installs an update. */
    val installController: AboutInstallController = AboutInstallController(installer, stateHolder, scope)

    val state: StateFlow<UpdatesState?> = stateHolder.state

    fun start() {
        stateHolder.set(
            UpdatesState(
                currentVersionTag = checker.currentReleaseTag,
                backgroundDownloadEnabled = settings.backgroundDownloadEnabled.value,
                downloadOnlyOnWifi = settings.downloadOnlyOnWifi.value,
                autoInstallWhenIdle = settings.autoInstallWhenIdle.value,
                check = CheckStatus.Idle,
                install = UpdateInstallState.Idle,
                canInstallPackages = false,
                installedNotes = InstalledNotesStatus.Hidden,
            ),
        )
        observeSettings(settings = settings, stateHolder = stateHolder, scope = scope)
        installController.start()
        scope.launch { runCheck() }
    }

    fun onBackgroundDownloadChanged(enabled: Boolean) {
        settings.setBackgroundDownloadEnabled(enabled)
    }

    fun onDownloadOnlyOnWifiChanged(enabled: Boolean) {
        settings.setDownloadOnlyOnWifi(enabled)
    }

    fun onAutoInstallWhenIdleChanged(enabled: Boolean) {
        settings.setAutoInstallWhenIdle(enabled)
    }

    /**
     * Shows the release notes of the installed version in a dialog. Does nothing when the dialog is already open.
     */
    fun onInstalledVersionClick() {
        val current = stateHolder.state.value ?: return
        if (current.installedNotes !is InstalledNotesStatus.Hidden) return

        stateHolder.update { it.copy(installedNotes = InstalledNotesStatus.Loading) }
        scope.launch { loadInstalledNotes() }
    }

    /**
     * Loads the release notes of the installed version again after they couldn't be loaded.
     */
    fun onRetryInstalledNotes() {
        val current = stateHolder.state.value ?: return
        if (current.installedNotes !is InstalledNotesStatus.Failed) return

        stateHolder.update { it.copy(installedNotes = InstalledNotesStatus.Loading) }
        scope.launch { loadInstalledNotes() }
    }

    /**
     * Hides the release notes of the installed version and closes the dialog showing them.
     */
    fun onInstalledNotesDismiss() {
        stateHolder.update { it.copy(installedNotes = InstalledNotesStatus.Hidden) }
    }

    fun onCheckForUpdatesClick() {
        val current = stateHolder.state.value ?: return
        if (current.check is CheckStatus.Checking) return

        scope.launch { runCheck() }
    }

    private suspend fun runCheck() {
        stateHolder.update { it.copy(check = CheckStatus.Checking) }

        val status = when (val result = checker.check()) {
            is UpdateCheckResult.Available -> CheckStatus.Available(result.update)
            is UpdateCheckResult.Failed -> CheckStatus.Failed(result.reason)
            UpdateCheckResult.UpToDate -> CheckStatus.UpToDate
            UpdateCheckResult.Disabled -> null
        }

        if (status == null) {
            stateHolder.clear()
        } else {
            if (status == CheckStatus.UpToDate) {
                // The installed app is current, so a failure of an earlier attempt would only contradict that.
                installController.resetFailedInstall()
            }
            stateHolder.update { it.copy(check = status) }
        }
    }

    private suspend fun loadInstalledNotes() {
        val status = when (val result = checker.installedReleaseNotes()) {
            is ReleaseNotesResult.Loaded -> InstalledNotesStatus.Loaded(result.title, result.notes, result.url)
            is ReleaseNotesResult.Failed -> InstalledNotesStatus.Failed(result.reason)
            ReleaseNotesResult.Disabled -> InstalledNotesStatus.Hidden
        }

        val current = stateHolder.state.value ?: return
        if (current.installedNotes != InstalledNotesStatus.Loading) return

        stateHolder.update { it.copy(installedNotes = status) }
    }
}

/**
 * Keeps the state of the update settings in sync with the values the user selected.
 */
private fun observeSettings(
    settings: AppUpdateSettings,
    stateHolder: AboutUpdatesStateHolder,
    scope: CoroutineScope,
) {
    scope.launch {
        settings.backgroundDownloadEnabled.collect { enabled ->
            stateHolder.update { it.copy(backgroundDownloadEnabled = enabled) }
        }
    }
    scope.launch {
        settings.downloadOnlyOnWifi.collect { enabled ->
            stateHolder.update { it.copy(downloadOnlyOnWifi = enabled) }
        }
    }
    scope.launch {
        settings.autoInstallWhenIdle.collect { enabled ->
            stateHolder.update { it.copy(autoInstallWhenIdle = enabled) }
        }
    }
}
