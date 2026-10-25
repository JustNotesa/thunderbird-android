package net.thunderbird.feature.updater.internal.background

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.internal.R

/**
 * Tells the user with a notification that a new version of the app is ready to be installed.
 */
internal class UpdateNotifier(private val context: Context) {
    private val notificationManager = NotificationManagerCompat.from(context)

    /**
     * Notifies the user that a new version of the app is available.
     *
     * @return `true` when the notification was posted, `false` when the user didn't allow notifications.
     */
    fun notifyUpdateAvailable(update: AppUpdate): Boolean {
        if (!canPostNotifications()) return false

        notificationManager.createNotificationChannel(createChannel())
        notificationManager.notify(UPDATE_NOTIFICATION_TAG, UPDATE_NOTIFICATION_ID, createNotification(update))
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

        notificationManager.createNotificationChannel(createChannel())
        notificationManager.notify(
            CONFIRM_NOTIFICATION_TAG,
            CONFIRM_NOTIFICATION_ID,
            createConfirmNotification(confirmIntent),
        )
    }

    /** Removes the notification that offers the installation of the update. */
    fun cancelConfirmInstall() {
        notificationManager.cancel(CONFIRM_NOTIFICATION_TAG, CONFIRM_NOTIFICATION_ID)
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun createChannel(): NotificationChannelCompat {
        return NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.updater_notification_channel_name))
            .build()
    }

    private fun createNotification(update: AppUpdate): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.updater_ic_notification)
            .setContentTitle(context.getString(R.string.updater_notification_title))
            .setContentText(context.getString(R.string.updater_notification_text, update.title))
            .setAutoCancel(true)

        createContentIntent()?.let { builder.setContentIntent(it) }

        return builder.build()
    }

    private fun createConfirmNotification(confirmIntent: Intent): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.updater_ic_notification)
            .setContentTitle(context.getString(R.string.updater_confirm_notification_title))
            .setContentText(context.getString(R.string.updater_confirm_notification_text))
            .setAutoCancel(true)
            .setContentIntent(createConfirmIntent(confirmIntent))
            .build()
    }

    private fun createContentIntent(): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null

        return PendingIntent.getActivity(
            context,
            CONTENT_INTENT_REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun createConfirmIntent(confirmIntent: Intent): PendingIntent {
        return PendingIntent.getActivity(
            context,
            CONFIRM_INTENT_REQUEST_CODE,
            confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        // The notifications have their own tags and ids, so that they don't collide with the notifications of
        // other parts of the app.
        const val UPDATE_NOTIFICATION_TAG = "app_update"
        const val UPDATE_NOTIFICATION_ID = 0x5550
        const val CONFIRM_NOTIFICATION_TAG = "app_update_confirm"
        const val CONFIRM_NOTIFICATION_ID = 0x5551

        private const val CHANNEL_ID = "app_updates"
        private const val CONTENT_INTENT_REQUEST_CODE = 1
        private const val CONFIRM_INTENT_REQUEST_CODE = 2
    }
}
