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
 * The receiver is created by the system, not by Koin. It reaches the checker, the notifier and the installer through
 * Koin.
 */
internal class AppUpdatedReceiver : BroadcastReceiver(), KoinComponent {
    private val checker: AppUpdateChecker by inject()
    private val notifier: UpdateNotifier by inject()
    private val installer: AppUpdateInstaller by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (checker.isEnabled) {
            notifier.notifyAppUpdated(checker.currentReleaseTag)
            // The update was installed, the notifications about the pending update aren't needed anymore.
            notifier.cancelUpdateAvailable()
            notifier.cancelConfirmInstall()
            notifier.cancelInstalling()
        }
        // The kept APK of the installed version isn't needed anymore.
        (installer as? PackageInstallerAppUpdateInstaller)?.clearKeptApk()
    }
}
