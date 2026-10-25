package net.thunderbird.feature.updater.api

import kotlinx.coroutines.flow.StateFlow

/** User settings of the updater. */
interface AppUpdateSettings {
    /** Whether the app checks for updates in the background once a day. Default `true`. */
    val autoCheckEnabled: StateFlow<Boolean>

    fun setAutoCheckEnabled(enabled: Boolean)
}
