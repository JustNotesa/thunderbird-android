package net.thunderbird.feature.updater.internal.install

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import net.thunderbird.components.core.logging.Logger
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.UpdateInstallFailure
import net.thunderbird.feature.updater.api.UpdateInstallState
import net.thunderbird.feature.updater.internal.R

private const val TAG = "AppUpdateDownloader"

/**
 * Downloads the APK of an app update.
 *
 * The APK is downloaded by the download manager of the system, so that the download keeps running when the app is
 * closed. An APK that was downloaded before isn't downloaded again and a download that is still running is watched
 * again.
 */
internal class AppUpdateDownloader(
    private val context: Context,
    private val systemDownloads: SystemDownloads,
    private val pendingDownloadStore: PendingDownloadStore,
    private val keptApks: KeptApkStore,
    private val apkVerifier: ApkVerifier,
    private val poll: DownloadPoll,
    private val state: MutableStateFlow<UpdateInstallState>,
    private val logger: Logger,
) {

    /**
     * Makes sure that the APK of [update] is downloaded.
     *
     * @param sha256 the checksum of the APK, as reported by GitHub.
     * @param onlyOnWifi whether the download may only run over an unmetered network.
     * @param installWhenDone whether the update is installed when the download is done.
     * @return the APK of the update when it was downloaded and verified before, `null` when it is being downloaded.
     */
    suspend fun ensureDownloaded(
        update: AppUpdate,
        sha256: String,
        onlyOnWifi: Boolean,
        installWhenDone: Boolean,
    ): File? {
        val keptFile = keptApks.verifiedFile(apkVerifier, update.tag, sha256)
        if (keptFile != null) {
            // The APK was downloaded before, so it doesn't have to be downloaded again.
            return keptFile
        }

        val pending = pendingDownloadStore.load()?.takeIf { it.tag == update.tag }
        if (pending != null) {
            resume(pending, installWhenDone)
        } else {
            start(update, sha256, onlyOnWifi, installWhenDone)
        }
        return null
    }

    /**
     * Continues the download [pending] that is still running.
     */
    private suspend fun resume(pending: PendingDownload, installWhenDone: Boolean) {
        if (installWhenDone && !pending.installWhenDone) {
            // The update is installed when the download is done, because it was asked for.
            pendingDownloadStore.save(pending.copy(installWhenDone = true))
        }
        poll.resume(pending)
    }

    /**
     * Starts the download of the APK of the update.
     */
    private suspend fun start(update: AppUpdate, sha256: String, onlyOnWifi: Boolean, installWhenDone: Boolean) {
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
                onlyOnWifi = onlyOnWifi,
            ),
        )
        if (downloadId == null) {
            logger.error(TAG) { "The download of the app update couldn't be started." }
            state.value = UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD)
            return
        }
        logger.info(TAG) { "The download $downloadId of the app update was started." }
        pendingDownloadStore.save(
            PendingDownload(
                downloadId = downloadId,
                tag = update.tag,
                sha256 = sha256,
                sizeBytes = update.apkSizeBytes,
                title = update.title,
                installWhenDone = installWhenDone,
            ),
        )
        state.value = UpdateInstallState.Downloading(0L, update.apkSizeBytes)
        poll.watch(downloadId)
    }
}
