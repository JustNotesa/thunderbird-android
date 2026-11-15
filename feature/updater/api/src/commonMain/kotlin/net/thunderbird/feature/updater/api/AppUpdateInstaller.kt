package net.thunderbird.feature.updater.api

import kotlinx.coroutines.flow.StateFlow

/** Downloads an [AppUpdate] and hands it to Android's package installer. */
interface AppUpdateInstaller {
    val state: StateFlow<UpdateInstallState>

    /** Whether the user has allowed this app to install apps ("Install unknown apps"). */
    fun canInstallPackages(): Boolean

    /** Opens the system screen where the user can allow this app to install apps. */
    fun openInstallPermissionSettings()

    /**
     * Downloads, verifies and installs [update]. Progress and the outcome are reported through [state]; when the
     * installation succeeds Android replaces the running app. Never throws.
     */
    suspend fun install(update: AppUpdate)

    /**
     * Compares [state] with the download manager of the system and continues from there: a finished download is
     * verified and installed, a running download is watched again, a lost download is reported as failed. Called when
     * the screen is shown again and when the user taps the button again while the download seems to hang. Never
     * throws.
     */
    suspend fun refresh()

    /** Sets [state] back to [UpdateInstallState.Idle] after a failure was shown. */
    fun resetState()
}
