package net.thunderbird.feature.updater.internal.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import org.koin.core.component.KoinComponent

/**
 * Installs the update that was downloaded in the background.
 *
 * The receiver is called by the notification that offers the installation of the update. It reaches the installer
 * through Koin, which binds [AppUpdateInstaller] as a singleton.
 */
internal class InstallDownloadedReceiver : BroadcastReceiver(), KoinComponent {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                // The installation of the update can take a moment, so the result is kept until it is done.
                handleInstallDownloaded()
            } catch (_: Exception) {
                // The update couldn't be installed, the installer reports the failure to the user.
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Hands the downloaded update over to the installer, which installs it.
     *
     * @param installer the installer of the app updates, `null` when it isn't available.
     */
    internal suspend fun handleInstallDownloaded(installer: AppUpdateInstaller? = installerOrNull()) {
        installer?.installDownloaded()
    }

    private fun installerOrNull(): AppUpdateInstaller? {
        return try {
            getKoin().getOrNull<AppUpdateInstaller>()
        } catch (_: IllegalStateException) {
            // Without Koin the installer can't be reached, so the update isn't installed.
            null
        }
    }

    companion object {
        const val ACTION_INSTALL_DOWNLOADED = "net.thunderbird.feature.updater.INSTALL_DOWNLOADED"
    }
}
