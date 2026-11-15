package net.thunderbird.feature.updater.internal.background

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.internal.R
import net.thunderbird.feature.updater.internal.install.InstallDownloadedReceiver

/**
 * Builds the channels and the notifications of the updater.
 *
 * The notifications are built here, so that [UpdateNotifier] only has to post them.
 */
internal class UpdateNotifications(private val context: Context) {

    private val launchIntent = AppLaunchIntent(context)

    /** The channel of the notifications about the pending update. */
    fun updateChannel(): NotificationChannelCompat {
        return NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.updater_notification_channel_name))
            .build()
    }

    /** The channel of the notification that tells the user that the app was updated. */
    fun updatedChannel(): NotificationChannelCompat {
        return NotificationChannelCompat.Builder(UPDATED_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.updater_updated_channel_name))
            .build()
    }

    /** The notification that tells the user that a new version of the app is available. */
    fun updateAvailable(update: AppUpdate): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.updater_ic_notification)
            .setContentTitle(context.getString(R.string.updater_notification_title))
            .setContentText(context.getString(R.string.updater_notification_text, update.title))
            .setAutoCancel(true)

        contentIntent()?.let { builder.setContentIntent(it) }

        return builder.build()
    }

    /** The notification that gives the user a way to confirm the installation of the update. */
    fun confirmInstall(confirmIntent: Intent): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.updater_ic_notification)
            .setContentTitle(context.getString(R.string.updater_confirm_notification_title))
            .setContentText(context.getString(R.string.updater_confirm_notification_text))
            .setAutoCancel(true)
            .setContentIntent(confirmIntent(confirmIntent))
            .build()
    }

    /**
     * The notification that tells the user that the update is being installed.
     *
     * The notification is ongoing, because the installation isn't finished when the app is closed. It is silent, so
     * that it doesn't disturb the user while Android installs the update. Android doesn't report a meaningful
     * progress of the installation, so the progress of the notification is indeterminate.
     */
    fun installing(): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.updater_ic_notification)
            .setContentTitle(context.getString(R.string.updater_installing_notification_title))
            .setContentText(context.getString(R.string.updater_installing_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .build()
    }

    /**
     * The notification that tells the user that the app was updated.
     *
     * It is on a channel with a high importance, so that the user doesn't miss it.
     */
    fun appUpdated(releaseTag: String): Notification {
        val builder = NotificationCompat.Builder(context, UPDATED_CHANNEL_ID)
            .setSmallIcon(R.drawable.updater_ic_notification)
            .setContentTitle(context.getString(R.string.updater_updated_notification_title))
            .setContentText(context.getString(R.string.updater_updated_notification_text, releaseTag))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)

        contentIntent()?.let { builder.setContentIntent(it) }

        return builder.build()
    }

    /**
     * The notification that tells the user that the update was downloaded and can be installed now.
     *
     * The notification offers the installation of the update, so that the user doesn't have to open the app first.
     */
    fun updateReady(tag: String, title: String): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.updater_ic_notification)
            .setContentTitle(context.getString(R.string.updater_ready_notification_title))
            .setContentText(context.getString(R.string.updater_ready_notification_text, title))
            .setAutoCancel(true)
            .addAction(
                R.drawable.updater_ic_notification,
                context.getString(R.string.updater_install_action),
                installIntent(tag),
            )

        contentIntent()?.let { builder.setContentIntent(it) }

        return builder.build()
    }

    /**
     * The notification that tells the user that the download of the update failed.
     */
    fun downloadFailed(): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.updater_ic_notification)
            .setContentTitle(context.getString(R.string.updater_failed_notification_title))
            .setContentText(context.getString(R.string.updater_failed_notification_text))
            .setAutoCancel(true)

        contentIntent()?.let { builder.setContentIntent(it) }

        return builder.build()
    }

    private fun contentIntent(): PendingIntent? {
        return launchIntent.pendingIntent(CONTENT_INTENT_REQUEST_CODE)
    }

    private fun confirmIntent(confirmIntent: Intent): PendingIntent {
        return PendingIntent.getActivity(
            context,
            CONFIRM_INTENT_REQUEST_CODE,
            confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * The intent that installs the update that was downloaded.
     */
    private fun installIntent(tag: String): PendingIntent {
        // The tag is part of the intent, so that the action of an older update isn't used for a newer one.
        val data = Uri.Builder()
            .scheme(RELEASE_URI_SCHEME)
            .authority(RELEASE_URI_AUTHORITY)
            .appendPath(tag)
            .build()
        val intent = Intent(context, InstallDownloadedReceiver::class.java)
            .setAction(InstallDownloadedReceiver.ACTION_INSTALL_DOWNLOADED)
            .setData(data)
            .setPackage(context.packageName)

        return PendingIntent.getBroadcast(
            context,
            INSTALL_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val CHANNEL_ID = "app_updates"
        const val UPDATED_CHANNEL_ID = "app_updated"
        const val CONTENT_INTENT_REQUEST_CODE = 1
        const val CONFIRM_INTENT_REQUEST_CODE = 2
        const val INSTALL_INTENT_REQUEST_CODE = 3
        const val RELEASE_URI_SCHEME = "thunderbird"
        const val RELEASE_URI_AUTHORITY = "app-update"
    }
}
