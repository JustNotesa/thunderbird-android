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
     * Tells the installer that the next installation was started by the user, so that the app is opened again after
     * it was replaced by the update. Installations without this call, like the one of the background, only show a
     * notification. Does nothing by default.
     */
    fun reopenAppAfterInstall() = Unit

    /**
     * Downloads, verifies and installs [update]. Progress and the outcome are reported through [state]; when the
     * installation succeeds Android replaces the running app. Never throws.
     */
    suspend fun install(update: AppUpdate)

    /**
     * Downloads and verifies [update] without installing it; afterwards [state] is
     * [UpdateInstallState.ReadyToInstall]. Used by the background download. [onlyOnWifi] keeps the download of the
     * system off metered networks. Never throws.
     */
    suspend fun download(update: AppUpdate, onlyOnWifi: Boolean)

    /**
     * Installs the update that was downloaded and verified before ([UpdateInstallState.ReadyToInstall]); the APK is
     * verified again first. Without such an update [state] becomes [UpdateInstallState.Failed] with
     * [UpdateInstallFailure.DOWNLOAD]. Never throws.
     */
    suspend fun installDownloaded()

    /**
     * Compares [state] with the download manager of the system and continues from there: a finished download is
     * verified and installed, a running download is watched again, a lost download is reported as failed. Called when
     * the screen is shown again. A pending confirmation whose install session is gone is reported as cancelled.
     * Never throws.
     */
    suspend fun refresh()

    /** Sets [state] back to [UpdateInstallState.Idle] after a failure was shown. */
    fun resetState()
}
