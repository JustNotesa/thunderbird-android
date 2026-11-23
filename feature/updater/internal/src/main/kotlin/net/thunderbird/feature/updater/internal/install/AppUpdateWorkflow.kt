package net.thunderbird.feature.updater.internal.install

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import net.thunderbird.components.core.logging.Logger
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.UpdateInstallFailure
import net.thunderbird.feature.updater.api.UpdateInstallState

private const val TAG = "AppUpdateWorkflow"

/**
 * Installs the updates of the app.
 *
 * The APK of an update is downloaded, verified against the checksum of the release and handed over to the package
 * installer of the system. An update that was downloaded in the background isn't installed right away; it waits until
 * the user or the automatic installation starts it.
 */
internal class AppUpdateWorkflow(
    private val downloader: AppUpdateDownloader,
    private val keptApks: KeptApkStore,
    private val apkVerifier: ApkVerifier,
    private val finisher: ApkFinisher,
    private val backgroundDownload: BackgroundDownload,
    private val state: MutableStateFlow<UpdateInstallState>,
    private val failures: InstallFailures,
    private val logger: Logger,
    private val isNewerThanInstalled: (String) -> Boolean = { true },
) {

    /**
     * Downloads, verifies and installs [update].
     */
    suspend fun install(update: AppUpdate) {
        failures.tag = update.tag
        val sha256 = update.apkSha256
        if (sha256 == null) {
            // Without a checksum the APK can't be verified, so it isn't downloaded at all.
            state.value = failures.create(UpdateInstallFailure.CHECKSUM)
            return
        }

        try {
            val keptFile = downloader.ensureDownloaded(
                update = update,
                sha256 = sha256,
                onlyOnWifi = false,
                installWhenDone = true,
            )
            if (keptFile != null) {
                finisher.installKeptApk(keptFile, update.tag, sha256)
            }
            // A cancellation that came while the work couldn't be stopped must not leave the state behind.
            currentCoroutineContext().ensureActive()
        } catch (cancellation: CancellationException) {
            // The installation was cancelled, the state must not stay in the middle of the installation.
            finisher.failUnfinished()
            throw cancellation
        } catch (_: Exception) {
            logger.error(TAG) { "Installing the app update failed." }
            state.value = failures.create(UpdateInstallFailure.REJECTED)
        }
    }

    /**
     * Downloads and verifies [update] without installing it.
     *
     * The update waits until it is installed with [installDownloaded].
     */
    suspend fun download(update: AppUpdate, onlyOnWifi: Boolean) {
        failures.tag = update.tag
        val sha256 = update.apkSha256
        if (sha256 == null) {
            // Without a checksum the APK can't be verified, so it isn't downloaded at all.
            state.value = failures.create(UpdateInstallFailure.CHECKSUM)
            return
        }

        try {
            val keptFile = downloader.ensureDownloaded(
                update = update,
                sha256 = sha256,
                onlyOnWifi = onlyOnWifi,
                installWhenDone = false,
            )
            if (keptFile != null) {
                // The APK was downloaded before, so the update can be installed right away.
                backgroundDownload.keep(update.tag, sha256, update.title)
            }
            currentCoroutineContext().ensureActive()
        } catch (cancellation: CancellationException) {
            // The download was cancelled, the state must not stay in the middle of the download.
            finisher.failUnfinished()
            throw cancellation
        } catch (_: Exception) {
            logger.error(TAG) { "Downloading the app update failed." }
            state.value = failures.create(UpdateInstallFailure.DOWNLOAD)
        }
    }

    /**
     * Installs the update that was downloaded and verified before.
     */
    suspend fun installDownloaded() {
        val kept = keptApks.load()
        if (kept == null) {
            logger.warn(TAG) { "There is no downloaded app update that could be installed." }
            state.value = failures.create(UpdateInstallFailure.DOWNLOAD)
            return
        }

        failures.tag = kept.tag
        val file = keptApks.verifiedFile(apkVerifier, kept.tag, kept.sha256)
        if (file == null) {
            logger.warn(TAG) { "The downloaded app update couldn't be verified." }
            state.value = failures.create(UpdateInstallFailure.DOWNLOAD)
            return
        }

        finisher.installKeptApk(file, kept.tag, kept.sha256)
    }

    /**
     * The update that was downloaded and verified before is ready to be installed.
     */
    fun restoreKeptUpdate() {
        val kept = keptApks.load() ?: return
        if (isNewerThanInstalled(kept.tag)) {
            // When the file of the downloaded update is gone, the update has to be downloaded again.
            if (keptApks.existingFile(kept.tag) != null) {
                logger.info(TAG) { "The downloaded app update is ready to be installed." }
                state.value = UpdateInstallState.ReadyToInstall(kept.tag)
            }
        } else {
            // The release is installed already (or is older), so there is nothing to install.
            logger.info(TAG) { "The downloaded app update isn't newer than the installed app and is removed." }
            keptApks.clear()
        }
    }
}
