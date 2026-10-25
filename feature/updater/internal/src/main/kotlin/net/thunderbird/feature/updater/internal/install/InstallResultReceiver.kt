package net.thunderbird.feature.updater.internal.install

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import net.thunderbird.feature.updater.internal.background.UpdateNotifier
import org.koin.core.component.KoinComponent

/**
 * Receives the result of the installation started by [PackageInstallerAppUpdateInstaller].
 *
 * The receiver is created by the system, not by Koin. It reaches the installer through Koin, which binds
 * [AppUpdateInstaller] as a singleton.
 */
internal class InstallResultReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val confirmationIntent = if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            readConfirmationIntent(intent)
        } else {
            null
        }
        handleStatus(context, status, confirmationIntent, installer, notifier)
    }

    private val installer: PackageInstallerAppUpdateInstaller?
        get() = getKoin().getOrNull<AppUpdateInstaller>() as? PackageInstallerAppUpdateInstaller

    private val notifier: UpdateNotifier?
        get() = getKoin().getOrNull<UpdateNotifier>()

    /**
     * Reports the status of the installation to the installer and shows the confirmation screen of the system.
     *
     * @param context the context to start the confirmation screen with.
     * @param status one of the `PackageInstaller.STATUS_*` values.
     * @param confirmationIntent the intent of the system that asks the user to confirm the installation.
     * @param installer the installer to report to, `null` when it isn't available.
     * @param notifier the notifier that shows the notification, `null` when it isn't available.
     */
    internal fun handleStatus(
        context: Context,
        status: Int,
        confirmationIntent: Intent?,
        installer: PackageInstallerAppUpdateInstaller?,
        notifier: UpdateNotifier?,
    ) {
        installer?.onInstallStatus(status)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            handlePendingUserAction(context, confirmationIntent, installer, notifier)
        } else {
            // The update was installed or it failed, the notification isn't needed anymore.
            notifier?.cancelConfirmInstall()
        }
    }

    private fun handlePendingUserAction(
        context: Context,
        confirmationIntent: Intent?,
        installer: PackageInstallerAppUpdateInstaller?,
        notifier: UpdateNotifier?,
    ) {
        if (confirmationIntent == null) {
            installer?.onInstallStatus(PackageInstaller.STATUS_FAILURE)
            notifier?.cancelConfirmInstall()
            return
        }
        // Android doesn't show the confirmation screen while the app is in the background, so the notification
        // gives the user a way to install the update anyway.
        notifier?.notifyConfirmInstall(confirmationIntent)
        startConfirmationActivity(context, confirmationIntent, installer, notifier)
    }

    private fun startConfirmationActivity(
        context: Context,
        confirmationIntent: Intent,
        installer: PackageInstallerAppUpdateInstaller?,
        notifier: UpdateNotifier?,
    ) {
        try {
            context.startActivity(confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            installer?.onInstallStatus(PackageInstaller.STATUS_FAILURE)
            notifier?.cancelConfirmInstall()
        } catch (_: SecurityException) {
            installer?.onInstallStatus(PackageInstaller.STATUS_FAILURE)
            notifier?.cancelConfirmInstall()
        }
    }

    @Suppress("DEPRECATION")
    private fun readConfirmationIntent(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "net.thunderbird.feature.updater.INSTALL_RESULT"
    }
}
