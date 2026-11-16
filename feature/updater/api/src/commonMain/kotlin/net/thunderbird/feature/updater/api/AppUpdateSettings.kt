package net.thunderbird.feature.updater.api

import kotlinx.coroutines.flow.StateFlow

/**
 * User settings of the updater.
 *
 * The app always checks for updates in the background. The user only decides whether the updates are downloaded and
 * installed automatically.
 */
interface AppUpdateSettings {
    /** Whether an update found by the background check is downloaded and verified right away. Default `false`. */
    val backgroundDownloadEnabled: StateFlow<Boolean>

    fun setBackgroundDownloadEnabled(enabled: Boolean)

    /** Whether the background download only runs over an unmetered network (Wi-Fi). Default `true`. */
    val downloadOnlyOnWifi: StateFlow<Boolean>

    fun setDownloadOnlyOnWifi(enabled: Boolean)

    /**
     * Whether a downloaded update is installed without asking while the device is idle and charging. Only used
     * together with [backgroundDownloadEnabled]. Default `false`.
     */
    val autoInstallWhenIdle: StateFlow<Boolean>

    fun setAutoInstallWhenIdle(enabled: Boolean)
}
