package net.thunderbird.feature.updater.internal.install

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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
import net.thunderbird.feature.updater.internal.background.ReopenMarker
import net.thunderbird.feature.updater.internal.background.SharedPreferencesReopenMarker
import net.thunderbird.feature.updater.internal.background.UpdateNotifier

private const val TAG = "PackageInstallerAppUpdateInstaller"

private const val DEFAULT_POLL_INTERVAL_MILLIS = 500L

/**
 * Installs app updates with Android's package installer.
 *
 * The APK is downloaded by the download manager of the system, so that the download keeps running when the app is
 * closed. When the download is finished, the APK is copied into the private directory of the app, verified against
 * the checksum of the release and written into a [PackageInstaller] session, which the system installs, asking the
 * user only when Android requires it.
 *
 * The verified APK is kept, so that the installation can be repeated without downloading the APK again.
 *
 * The download is watched in [coroutineScope], an app wide scope, so that it survives the caller: when the screen
 * that started the installation is closed, the download keeps running and the state keeps being updated.
 */
@Suppress("TooManyFunctions")
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
    private val isNewerThanInstalled: (String) -> Boolean = { true },
    private val clock: () -> Long = System::currentTimeMillis,
    private val reopenMarker: ReopenMarker = SharedPreferencesReopenMarker(context, clock),
) : AppUpdateInstaller {

    private val failures = InstallFailures(clock)

    private val keptApks = KeptApkStore(context)

    private val _state = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    override val state: StateFlow<UpdateInstallState> = _state.asStateFlow()

    private val progressObserver = InstallProgressObserver(
        registrar = sessionRegistrar,
        state = _state,
        notifier = notifier,
        logger = logger,
    )

    private val backgroundDownload = BackgroundDownload(keptApks, notifier, _state)

    private val finisher = ApkFinisher(
        systemDownloads = systemDownloads,
        pendingDownloadStore = pendingDownloadStore,
        apkVerifier = apkVerifier,
        keptApks = keptApks,
        backgroundDownload = backgroundDownload,
        logger = logger,
        state = _state,
        failures = failures,
        writeSession = { file ->
            markReopenWhenRequested()
            writeSession(file)
        },
        onSessionCommitted = { sessionId -> progressObserver.watch(sessionId) },
    )

    /** Whether the user started the installation, so that the app is opened again after the update. */
    private val reopenRequested = AtomicBoolean(false)

    private val installMutex = Mutex()
    private val installJob = AtomicReference<Job?>(null)

    private val poll = DownloadPoll(
        systemDownloads = systemDownloads,
        pendingDownloadStore = pendingDownloadStore,
        state = _state,
        coroutineScope = coroutineScope,
        pollIntervalMillis = pollIntervalMillis,
        logger = logger,
        onDownloadFinished = { downloadId -> onDownloadFinished(downloadId) },
        onDownloadFailed = { downloadId -> finisher.failPending(UpdateInstallFailure.DOWNLOAD, downloadId) },
    )

    /** Removes the downloads of releases that aren't newer than the installed one. */
    internal val staleDownloads = StaleDownloads(
        systemDownloads = systemDownloads,
        pendingDownloadStore = pendingDownloadStore,
        keptApks = keptApks,
        poll = poll,
        state = _state,
        logger = logger,
        isNewerThanInstalled = isNewerThanInstalled,
    )

    private val downloader = AppUpdateDownloader(
        context = context,
        systemDownloads = systemDownloads,
        pendingDownloadStore = pendingDownloadStore,
        keptApks = keptApks,
        apkVerifier = apkVerifier,
        poll = poll,
        state = _state,
        failures = failures,
        logger = logger,
    )

    private val workflow = AppUpdateWorkflow(
        downloader = downloader,
        keptApks = keptApks,
        apkVerifier = apkVerifier,
        finisher = finisher,
        backgroundDownload = backgroundDownload,
        state = _state,
        failures = failures,
        logger = logger,
        isNewerThanInstalled = isNewerThanInstalled,
    )

    init {
        // The download of an update that was started before the app was closed is continued.
        coroutineScope.launch {
            // A download of a release that is installed already isn't continued and isn't reported as failed.
            staleDownloads.drop()
            val pending = pendingDownloadStore.load()
            if (pending == null) {
                // An update that was downloaded and verified before is ready to be installed.
                workflow.restoreKeptUpdate()
            } else {
                failures.tag = pending.tag
                poll.resume(pending)
            }
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
            poll.stop()
        }
        progressObserver.stopWatching()
        reopenRequested.set(false)
        reopenMarker.clear()
        _state.value = UpdateInstallState.Idle
    }

    override fun reopenAppAfterInstall() {
        reopenRequested.set(true)
    }

    override suspend fun install(update: AppUpdate) {
        // A new installation replaces the old one, so the old session isn't watched anymore.
        progressObserver.stopWatching()
        failures.tag = update.tag
        if (!canInstallPackages()) {
            _state.value = failures.create(UpdateInstallFailure.PERMISSION_MISSING, update.tag)
            return
        }
        startWork { workflow.install(update) }
    }

    override suspend fun download(update: AppUpdate, onlyOnWifi: Boolean) {
        startWork { workflow.download(update, onlyOnWifi) }
    }

    override suspend fun installDownloaded() {
        // The notification about the update that is ready isn't needed anymore.
        notifier.cancelUpdateReady()
        startWork { workflow.installDownloaded() }
    }

    /**
     * Compares the state of the app with the download manager of the system.
     *
     * A download that was finished while the app didn't notice it is verified and installed, a download that is still
     * running is watched again and a download that is gone is reported as failed.
     */
    override suspend fun refresh() {
        withContext(Dispatchers.IO) {
            try {
                logger.debug(TAG) { "The download of the app update is refreshed." }
                if (_state.value is UpdateInstallState.WaitingForConfirmation && progressObserver.isSessionGone()) {
                    // The user closed the dialog of the system, so there is nothing left to confirm.
                    logger.warn(TAG) { "The install session of the app update is gone." }
                    onInstallStatus(PackageInstaller.STATUS_FAILURE_ABORTED)
                    return@withContext
                }
                // A download of a release that is installed already is dropped, not reported as failed.
                staleDownloads.drop()
                val pending = pendingDownloadStore.load()
                if (pending != null) {
                    logger.debug(TAG) { "The pending download of the app update was found." }
                    failures.tag = pending.tag
                    poll.resume(pending)
                } else if (_state.value is UpdateInstallState.Downloading) {
                    // The download is gone, so it can't be continued.
                    logger.warn(TAG) { "The download of the app update was lost." }
                    _state.value = failures.create(UpdateInstallFailure.DOWNLOAD)
                } else {
                    logger.debug(TAG) { "There is no download of the app update." }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                logger.error(TAG) { "The download of the app update couldn't be refreshed." }
            }
        }
    }

    /**
     * Reports the status of the installation to the user.
     *
     * @param status one of the `PackageInstaller.STATUS_*` values.
     */
    internal fun onInstallStatus(status: Int) {
        logger.debug(TAG) { "The status of the installation is $status." }
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
        _state.value = failures.create(failure)
        reopenMarker.clear()
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

    /** Stores the marker that reopens the app, right before the installation is handed to the system. */
    private fun markReopenWhenRequested() {
        if (reopenRequested.getAndSet(false)) {
            reopenMarker.mark()
        }
    }

    /**
     * Runs the work [block] in the app wide scope, so that it isn't stopped when the caller is gone.
     *
     * The work is done on the IO dispatcher, because hashing the APK is slow.
     */
    private suspend fun startWork(block: suspend () -> Unit) {
        val job = installMutex.withLock {
            val running = installJob.get()
            if (running != null && running.isActive) {
                running
            } else {
                coroutineScope.launch(Dispatchers.IO) { block() }
                    .also { started -> installJob.set(started) }
            }
        }
        job.join()
    }
}
