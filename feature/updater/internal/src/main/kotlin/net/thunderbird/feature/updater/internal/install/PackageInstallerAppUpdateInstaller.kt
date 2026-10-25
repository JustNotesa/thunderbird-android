package net.thunderbird.feature.updater.internal.install

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.thunderbird.components.core.logging.Logger
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import net.thunderbird.feature.updater.api.UpdateInstallFailure
import net.thunderbird.feature.updater.api.UpdateInstallState
import net.thunderbird.feature.updater.internal.R
import net.thunderbird.feature.updater.internal.background.UpdateNotifier

private const val TAG = "PackageInstallerAppUpdateInstaller"

private const val DEFAULT_POLL_INTERVAL_MILLIS = 500L

/**
 * Installs app updates with Android's package installer.
 *
 * The APK is downloaded by the download manager of the system, so that the download keeps running when the app is
 * closed. When the download is finished, the APK is copied into the private directory of the app, verified against
 * the checksum of the release and written into a [PackageInstaller] session, which the system hands over to the user
 * to confirm the installation.
 *
 * The verified APK is kept, so that the installation can be repeated without downloading the APK again.
 *
 * The download is watched in [coroutineScope], an app wide scope, so that it survives the caller: when the screen
 * that started the installation is closed, the download keeps running and the state keeps being updated.
 */
internal class PackageInstallerAppUpdateInstaller(
    private val context: Context,
    private val systemDownloads: SystemDownloads,
    private val pendingDownloadStore: PendingDownloadStore,
    private val apkVerifier: ApkVerifier,
    private val logger: Logger,
    private val notifier: UpdateNotifier,
    private val coroutineScope: CoroutineScope,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
    private val writeSession: (File) -> Int? = { ApkSessionWriter(context).write(it) },
    private val sessionRegistrar: InstallSessionRegistrar = PackageInstallerSessionRegistrar(context),
) : AppUpdateInstaller {

    private val keptApks = KeptApkStore(context)

    private val _state = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    override val state: StateFlow<UpdateInstallState> = _state.asStateFlow()

    private val progressObserver = InstallProgressObserver(
        registrar = sessionRegistrar,
        state = _state,
        notifier = notifier,
        logger = logger,
    )

    private val finisher = ApkFinisher(
        systemDownloads = systemDownloads,
        pendingDownloadStore = pendingDownloadStore,
        apkVerifier = apkVerifier,
        keptApks = keptApks,
        logger = logger,
        state = _state,
        writeSession = writeSession,
        onSessionCommitted = { sessionId -> progressObserver.watch(sessionId) },
    )

    private val installMutex = Mutex()
    private val installJob = AtomicReference<Job?>(null)
    private val pollMutex = Mutex()
    private val pollJob = AtomicReference<Job?>(null)

    init {
        // The download of an update that was started before the app was closed is continued.
        coroutineScope.launch {
            resumeDownload(pendingDownloadStore.load())
        }
    }

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
        if (_state.value is UpdateInstallState.Downloading) {
            // The user cancelled the update, so the download isn't needed anymore.
            pendingDownloadStore.load()?.let { pending -> systemDownloads.remove(pending.downloadId) }
            pendingDownloadStore.clear()
            pollJob.getAndSet(null)?.cancel()
        }
        progressObserver.stopWatching()
        _state.value = UpdateInstallState.Idle
    }

    override suspend fun install(update: AppUpdate) {
        // The work is done in the app wide scope, so that it isn't stopped when the screen that started the
        // installation is closed. It is done on the IO dispatcher, because hashing the APK is slow.
        val job = installMutex.withLock {
            val running = installJob.get()
            if (running != null && running.isActive) {
                running
            } else {
                coroutineScope.launch(Dispatchers.IO) { installUpdate(update) }
                    .also { started -> installJob.set(started) }
            }
        }
        job.join()
    }

    /**
     * Reports the status of the installation to the user.
     *
     * @param status one of the `PackageInstaller.STATUS_*` values.
     */
    internal fun onInstallStatus(status: Int) {
        val failure = when (status) {
            // The app was replaced, so there is nothing left to report.
            PackageInstaller.STATUS_SUCCESS -> null

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                _state.value = UpdateInstallState.WaitingForConfirmation
                null
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateInstallFailure.CANCELLED

            else -> UpdateInstallFailure.REJECTED
        }
        if (failure == null) return

        // The installation failed, so the session isn't watched anymore and the user is told about it.
        _state.value = UpdateInstallState.Failed(failure)
        progressObserver.stopWatching()
        notifier.cancelInstalling()
    }

    /**
     * Handles the download with the id [downloadId] that is finished.
     *
     * The download is handled at most once. The ids of other downloads are ignored. When the download is handled
     * already, this call waits for the work that handles it.
     */
    internal suspend fun onDownloadFinished(downloadId: Long) {
        finisher.finish(downloadId)
    }

    /**
     * Removes the APK that was kept for an installation that was already done.
     */
    internal fun clearKeptApk() {
        keptApks.clear()
    }

    /**
     * Downloads the APK of the update, or installs the APK that was downloaded before.
     */
    private suspend fun installUpdate(update: AppUpdate) {
        try {
            // A new installation replaces the old one, so the old session isn't watched anymore.
            progressObserver.stopWatching()
            if (!canInstallPackages()) {
                _state.value = UpdateInstallState.Failed(UpdateInstallFailure.PERMISSION_MISSING)
                return
            }
            val sha256 = update.apkSha256
            if (sha256 == null) {
                // Without a checksum the APK can't be verified, so it isn't downloaded at all.
                _state.value = UpdateInstallState.Failed(UpdateInstallFailure.CHECKSUM)
                return
            }
            val keptFile = keptApks.verifiedFile(apkVerifier, update.tag, sha256)
            val pending = pendingDownloadStore.load()?.takeIf { it.tag == update.tag }
            when {
                // The APK was downloaded before, so it doesn't have to be downloaded again.
                keptFile != null -> finisher.installKeptApk(keptFile, update.tag, sha256)

                // The download of this update is still running, so it isn't downloaded again.
                pending != null -> resumeDownload(pending)

                // The APK has to be downloaded.
                else -> startDownload(update, sha256)
            }
            // A cancellation that came while the work couldn't be stopped must not leave the state behind.
            currentCoroutineContext().ensureActive()
        } catch (cancellation: CancellationException) {
            // The installation was cancelled, the state must not stay in the middle of the installation.
            finisher.failUnfinished()
            throw cancellation
        } catch (_: Exception) {
            logger.error(TAG) { "Installing the app update failed." }
            _state.value = UpdateInstallState.Failed(UpdateInstallFailure.REJECTED)
        }
    }

    /**
     * Starts the download of the APK of the update.
     */
    private suspend fun startDownload(update: AppUpdate, sha256: String) {
        // A new download replaces the download of an older update.
        pendingDownloadStore.load()?.let { old -> systemDownloads.remove(old.downloadId) }
        pendingDownloadStore.clear()
        // The APK of an older update isn't needed anymore.
        keptApks.clear()
        val downloadId = systemDownloads.enqueue(
            SystemDownloadRequest(
                url = update.apkUrl,
                fileName = keptApks.fileName(update.tag),
                title = context.getString(R.string.updater_download_title),
                description = update.title,
            ),
        )
        if (downloadId == null) {
            _state.value = UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD)
            return
        }
        pendingDownloadStore.save(
            PendingDownload(
                downloadId = downloadId,
                tag = update.tag,
                sha256 = sha256,
                sizeBytes = update.apkSizeBytes,
                title = update.title,
            ),
        )
        _state.value = UpdateInstallState.Downloading(0L, update.apkSizeBytes)
        startPolling(downloadId)
    }

    /**
     * Watches the download with the id [downloadId] until it is finished.
     */
    private suspend fun startPolling(downloadId: Long) {
        pollMutex.withLock {
            // Only one download is watched at a time.
            pollJob.get()?.cancel()
            val job = coroutineScope.launch {
                do {
                    delay(pollIntervalMillis)
                    // The download may have been finished, cancelled or replaced while the poll was waiting.
                    val watched = _state.value is UpdateInstallState.Downloading &&
                        pendingDownloadStore.load()?.downloadId == downloadId
                    if (watched) {
                        when (val status = systemDownloads.query(downloadId)) {
                            is SystemDownloadStatus.Running -> _state.update { current ->
                                // A finished download must not be reported as running again.
                                if (current is UpdateInstallState.Downloading) {
                                    UpdateInstallState.Downloading(status.downloadedBytes, status.totalBytes)
                                } else {
                                    current
                                }
                            }

                            is SystemDownloadStatus.Successful -> onDownloadFinished(downloadId)

                            else -> finisher.failPending(UpdateInstallFailure.DOWNLOAD, downloadId)
                        }
                    }
                } while (watched)
            }
            pollJob.set(job)
        }
    }

    /**
     * Continues the download of the pending download [pending], for example after the app was closed.
     */
    private suspend fun resumeDownload(pending: PendingDownload?) {
        if (pending == null) {
            return
        }
        when (val status = systemDownloads.query(pending.downloadId)) {
            is SystemDownloadStatus.Running -> {
                _state.value = UpdateInstallState.Downloading(status.downloadedBytes, status.totalBytes)
                startPolling(pending.downloadId)
            }

            is SystemDownloadStatus.Successful -> onDownloadFinished(pending.downloadId)

            else -> finisher.failPending(UpdateInstallFailure.DOWNLOAD, pending.downloadId)
        }
    }
}
