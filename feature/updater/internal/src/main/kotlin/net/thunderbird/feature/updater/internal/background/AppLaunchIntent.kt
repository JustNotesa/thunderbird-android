package net.thunderbird.feature.updater.internal.background

import android.app.PendingIntent
import android.content.Context

/**
 * Creates the intent that opens the app.
 *
 * The app can't always be opened, for example when it isn't the launcher of the app.
 */
internal class AppLaunchIntent(private val context: Context) {

    /**
     * The intent that opens the app.
     *
     * @param requestCode the number that tells the intents of the app apart.
     * @return the intent, `null` when the app can't be opened.
     */
    fun pendingIntent(requestCode: Int): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null

        return PendingIntent.getActivity(
            context,
            requestCode,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
