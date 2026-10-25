package net.thunderbird.feature.updater.internal.background

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import net.thunderbird.feature.updater.api.AppUpdate

/**
 * Tells the user with a notification that a new version of the app is ready to be installed.
 *
 * Every function that posts a notification checks [canPostNotifications] first; lint doesn't see that check.
 */
@SuppressLint("MissingPermission")
internal class UpdateNotifier(private val context: Context) {
    private val notificationManager = NotificationManagerCompat.from(context)
    private val notifications = UpdateNotifications(context)

    /**
     * Notifies the user that a new version of the app is available.
     *
     * @return `true` when the notification was posted, `false` when the user didn't allow notifications.
     */
    fun notifyUpdateAvailable(update: AppUpdate): Boolean {
        if (!canPostNotifications()) return false

        notificationManager.createNotificationChannel(notifications.updateChannel())
        notificationManager.notify(
            UPDATE_NOTIFICATION_TAG,
            UPDATE_NOTIFICATION_ID,
            notifications.updateAvailable(update),
        )
        return true
    }

    /**
     * Notifies the user that the update is ready to be installed.
     *
     * Android doesn't show the confirmation screen of the system while the app is in the background, so the
     * notification gives the user a way to install the update anyway.
     *
     * @param confirmIntent the intent of the system that asks the user to confirm the installation.
     */
    fun notifyConfirmInstall(confirmIntent: Intent) {
        if (!canPostNotifications()) return

        notificationManager.createNotificationChannel(notifications.updateChannel())
        notificationManager.notify(
            CONFIRM_NOTIFICATION_TAG,
            CONFIRM_NOTIFICATION_ID,
            notifications.confirmInstall(confirmIntent),
        )
    }

    /** Removes the notification that offers the installation of the update. */
    fun cancelConfirmInstall() {
        notificationManager.cancel(CONFIRM_NOTIFICATION_TAG, CONFIRM_NOTIFICATION_ID)
    }

    /** Removes the notification that announces an available update. */
    fun cancelUpdateAvailable() {
        notificationManager.cancel(UPDATE_NOTIFICATION_TAG, UPDATE_NOTIFICATION_ID)
    }

    /**
     * Notifies the user that the app was updated to a new version.
     *
     * Android closes the app while it installs an update of itself, so the user would think that nothing happened.
     *
     * @param releaseTag the release tag of the installed version.
     * @return `true` when the notification was posted, `false` when the user didn't allow notifications.
     */
    fun notifyAppUpdated(releaseTag: String): Boolean {
        if (!canPostNotifications()) return false

        notificationManager.createNotificationChannel(notifications.updatedChannel())
        notificationManager.notify(
            UPDATED_NOTIFICATION_TAG,
            UPDATED_NOTIFICATION_ID,
            notifications.appUpdated(releaseTag),
        )
        return true
    }

    /**
     * Notifies the user that the update is being installed.
     *
     * The notification is ongoing and silent, so that it doesn't disturb the user while Android installs the update.
     *
     * @param progress the progress of the installation from 0 to 1, or -1 if it is unknown.
     */
    fun notifyInstalling(progress: Float) {
        if (!canPostNotifications()) return

        notificationManager.createNotificationChannel(notifications.updateChannel())
        notificationManager.notify(
            INSTALLING_NOTIFICATION_TAG,
            INSTALLING_NOTIFICATION_ID,
            notifications.installing(progress),
        )
    }

    /** Removes the notification that tells the user that the update is being installed. */
    fun cancelInstalling() {
        notificationManager.cancel(INSTALLING_NOTIFICATION_TAG, INSTALLING_NOTIFICATION_ID)
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        // The notifications have their own tags and ids, so that they don't collide with the notifications of
        // other parts of the app.
        const val UPDATE_NOTIFICATION_TAG = "app_update"
        const val UPDATE_NOTIFICATION_ID = 0x5550
        const val CONFIRM_NOTIFICATION_TAG = "app_update_confirm"
        const val CONFIRM_NOTIFICATION_ID = 0x5551
        const val UPDATED_NOTIFICATION_TAG = "app_updated"
        const val UPDATED_NOTIFICATION_ID = 0x5552
        const val INSTALLING_NOTIFICATION_TAG = "app_installing"
        const val INSTALLING_NOTIFICATION_ID = 0x5553
    }
}
