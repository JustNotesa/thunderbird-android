package net.thunderbird.feature.updater.internal.background

/**
 * Tells the user that the app update is being installed.
 */
internal interface InstallingNotifier {

    /** Notifies the user that the update is being installed. */
    fun notifyInstalling()

    /** Removes the notification that tells the user that the update is being installed. */
    fun cancelInstalling()
}
