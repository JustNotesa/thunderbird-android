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

    /** Sets [state] back to [UpdateInstallState.Idle] after a failure was shown. */
    fun resetState()
}
