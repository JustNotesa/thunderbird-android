package net.thunderbird.feature.updater.internal.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import net.thunderbird.feature.updater.api.AppUpdateChecker
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import net.thunderbird.feature.updater.internal.install.PackageInstallerAppUpdateInstaller
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Tells the user that the app was updated to a new version.
 *
 * Android closes the app while it installs an update of itself, so the user would think that nothing happened. The
 * system calls this receiver after the app was replaced; it shows a notification that names the new version.
 *
 * When the user started the installation from the app, the app is opened again as well.
 *
 * The receiver is created by the system, not by Koin. It reaches the checker, the notifier, the installer and the
 * reopening of the app through Koin.
 */
internal class AppUpdatedReceiver : BroadcastReceiver(), KoinComponent {
    private val checker: AppUpdateChecker by inject()
    private val notifier: UpdateNotifier by inject()
    private val installer: AppUpdateInstaller by inject()
    private val reopenAfterUpdate: ReopenAfterUpdate by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (checker.isEnabled) {
            notifier.notifyAppUpdated(checker.currentReleaseTag)
            // The update was installed, the notifications about the pending update aren't needed anymore.
            notifier.cancelUpdateAvailable()
            notifier.cancelConfirmInstall()
            notifier.cancelInstalling()
        }
        // The notification above stays as the fallback, because the system may block the start from the background.
        reopenAfterUpdate.onAppUpdated()
        // The kept APK of the installed version isn't needed anymore.
        (installer as? PackageInstallerAppUpdateInstaller)?.let { packageInstaller ->
            packageInstaller.clearKeptApk()
            // A download of an older release can be left over, it isn't continued and isn't reported as failed.
            packageInstaller.staleDownloads.drop()
        }
    }
}
