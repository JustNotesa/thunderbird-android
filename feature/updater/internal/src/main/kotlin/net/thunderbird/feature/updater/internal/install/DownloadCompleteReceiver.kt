package net.thunderbird.feature.updater.internal.install

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import org.koin.core.component.KoinComponent

private const val NO_DOWNLOAD_ID = -1L

/**
 * Tells the installer that the download of an app update is finished.
 *
 * The APK is downloaded by the download manager of the system, which sends this broadcast when the download is
 * finished. The receiver is created by the system, not by Koin. It reaches the installer through Koin.
 */
internal class DownloadCompleteReceiver : BroadcastReceiver(), KoinComponent {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val downloadId = downloadIdOf(intent)
        if (downloadId == null) {
            // The broadcast isn't about a download of this app.
            return
        }
        val pendingResult = goAsync()
        scope.launch {
            try {
                // The installation of the download can take a moment, so the result is kept until it is done.
                handleDownloadFinished(downloadId)
            } catch (_: Exception) {
                // The download couldn't be handled, the installer reports the failure to the user.
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Reads the id of the finished download from [intent].
     *
     * @return the id of the download, `null` when the intent doesn't contain one.
     */
    internal fun downloadIdOf(intent: Intent): Long? {
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, NO_DOWNLOAD_ID)
        return downloadId.takeIf { it != NO_DOWNLOAD_ID }
    }

    /**
     * Hands the finished download over to the installer, which verifies and installs the downloaded APK.
     *
     * @param downloadId the id of the finished download.
     * @param installer the installer of the app updates, `null` when it isn't available.
     */
    internal suspend fun handleDownloadFinished(
        downloadId: Long,
        installer: AppUpdateInstaller? = installerOrNull(),
    ) {
        val updater = installer as? PackageInstallerAppUpdateInstaller ?: return
        updater.onDownloadFinished(downloadId)
    }

    private fun installerOrNull(): AppUpdateInstaller? {
        return try {
            getKoin().getOrNull<AppUpdateInstaller>()
        } catch (_: IllegalStateException) {
            // Without Koin the installer can't be reached, so the download isn't handled.
            null
        }
    }
}
