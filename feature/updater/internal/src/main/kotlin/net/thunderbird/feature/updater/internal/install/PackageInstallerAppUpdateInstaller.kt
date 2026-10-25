package net.thunderbird.feature.updater.internal.install

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.thunderbird.components.core.logging.Logger
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import net.thunderbird.feature.updater.api.UpdateInstallFailure
import net.thunderbird.feature.updater.api.UpdateInstallState

private const val TAG = "PackageInstallerAppUpdateInstaller"

private const val UNKNOWN_TOTAL_BYTES = -1L

/**
 * Installs app updates with Android's package installer.
 *
 * The APK is downloaded by [ApkDownloader], then written into a [PackageInstaller] session and handed over to the
 * system, which asks the user to confirm the installation.
 *
 * The download runs in [coroutineScope], an app wide scope, so that it survives the caller: when the screen that
 * started the installation is closed, the download keeps running and the state keeps being updated.
 */
internal class PackageInstallerAppUpdateInstaller(
    private val context: Context,
    private val downloader: ApkDownloader,
    private val logger: Logger,
    private val coroutineScope: CoroutineScope,
) : AppUpdateInstaller {

    private val sessionWriter = ApkSessionWriter(context)

    private val _state = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    override val state: StateFlow<UpdateInstallState> = _state.asStateFlow()

    private val installMutex = Mutex()
    private var installJob: Job? = null

    override fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    override fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + context.packageName),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            logger.error(TAG) { "Could not open the settings for unknown app sources." }
        }
    }

    override fun resetState() {
        _state.value = UpdateInstallState.Idle
    }

    override suspend fun install(update: AppUpdate) {
        if (!canInstallPackages()) {
            _state.value = UpdateInstallState.Failed(UpdateInstallFailure.PERMISSION_MISSING)
            return
        }

        // The work runs in the app wide scope, the caller only waits for it. A cancelled caller doesn't stop the
        // download, the installation continues in the background.
        currentInstallJob(update).join()
    }

    /**
     * Reports the status of the installation to the user.
     *
     * @param status one of the `PackageInstaller.STATUS_*` values.
     */
    internal fun onInstallStatus(status: Int) {
        when (status) {
            // The app was replaced, so there is nothing left to report.
            PackageInstaller.STATUS_SUCCESS -> Unit

            PackageInstaller.STATUS_FAILURE_ABORTED ->
                _state.value = UpdateInstallState.Failed(UpdateInstallFailure.CANCELLED)

            PackageInstaller.STATUS_PENDING_USER_ACTION -> _state.value = UpdateInstallState.WaitingForConfirmation

            else -> _state.value = UpdateInstallState.Failed(UpdateInstallFailure.REJECTED)
        }
    }

    /**
     * Returns the job of the running installation. A running installation is reused, so that the APK isn't
     * downloaded twice.
     */
    private suspend fun currentInstallJob(update: AppUpdate): Job {
        return installMutex.withLock {
            val runningJob = installJob
            if (runningJob != null && runningJob.isActive) {
                runningJob
            } else {
                coroutineScope.launch { runInstall(update) }.also { installJob = it }
            }
        }
    }

    private suspend fun runInstall(update: AppUpdate) {
        try {
            _state.value = UpdateInstallState.Downloading(0L, UNKNOWN_TOTAL_BYTES)
            downloadAndInstall(update)
        } catch (cancellation: CancellationException) {
            // The installation was cancelled, the state must not stay in Downloading or Verifying.
            _state.value = UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD)
            throw cancellation
        } catch (_: Exception) {
            logger.error(TAG) { "Installing the app update failed." }
            _state.value = UpdateInstallState.Failed(UpdateInstallFailure.REJECTED)
        }
    }

    private suspend fun downloadAndInstall(update: AppUpdate) {
        val result = downloader.download(update) { downloaded, total ->
            _state.value = UpdateInstallState.Downloading(downloaded, total)
        }
        when (result) {
            is DownloadResult.Success -> installApk(result.file)
            DownloadResult.Failed -> _state.value = UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD)
            DownloadResult.ChecksumMismatch -> _state.value = UpdateInstallState.Failed(UpdateInstallFailure.CHECKSUM)
        }
    }

    private suspend fun installApk(file: File) {
        // The checksum of the APK was already verified by the downloader.
        _state.value = UpdateInstallState.Verifying
        val committed = withContext(Dispatchers.IO) { sessionWriter.write(file) }
        if (committed) {
            _state.value = UpdateInstallState.WaitingForConfirmation
        } else {
            logger.error(TAG) { "Writing the APK into the install session failed." }
            _state.value = UpdateInstallState.Failed(UpdateInstallFailure.REJECTED)
        }
    }
}
