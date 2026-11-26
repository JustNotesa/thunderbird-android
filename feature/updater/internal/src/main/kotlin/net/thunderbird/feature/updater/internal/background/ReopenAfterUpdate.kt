package net.thunderbird.feature.updater.internal.background

import android.content.Context
import android.content.Intent
import java.util.concurrent.TimeUnit
import net.thunderbird.components.core.logging.Logger

private const val TAG = "ReopenAfterUpdate"

private const val MAX_MARKER_AGE_MINUTES = 15L

private val MAX_MARKER_AGE_MILLIS = TimeUnit.MINUTES.toMillis(MAX_MARKER_AGE_MINUTES)

/**
 * Starts the app.
 *
 * The seam exists, so that [ReopenAfterUpdate] can be tested without the system.
 */
internal fun interface AppLauncher {
    /**
     * Starts the app.
     *
     * @return `false` when the app has no activity that can be started.
     */
    fun launch(): Boolean
}

internal class ContextAppLauncher(private val context: Context) : AppLauncher {
    override fun launch(): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return false
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}

/**
 * Opens the app again after the user installed an update from the app.
 *
 * Android closes the app while it replaces itself. When the user started that installation a short time ago, the app
 * is started again. Android 10 and newer may silently block the start from the background, so the notification about
 * the update is always shown as well.
 */
internal class ReopenAfterUpdate(
    private val marker: ReopenMarker,
    private val launcher: AppLauncher,
    private val logger: Logger,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Starts the app when the user started the installation not long ago. The marker is always removed.
     */
    fun onAppUpdated() {
        val markedAt = marker.markedAtMillis()
        marker.clear()
        if (markedAt == null || clock() - markedAt !in 0..MAX_MARKER_AGE_MILLIS) {
            return
        }

        try {
            if (!launcher.launch()) {
                logger.warn(TAG) { "The app has no activity that can be opened." }
            }
        } catch (_: Exception) {
            logger.error(TAG) { "The app couldn't be opened after the update." }
        }
    }
}
