package net.thunderbird.feature.updater.internal.install

import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.thunderbird.components.core.logging.Logger
import net.thunderbird.feature.updater.api.UpdateInstallFailure
import net.thunderbird.feature.updater.api.UpdateInstallState

private const val TAG = "ApkFinisher"

/**
 * Finishes the download of an app update.
 *
 * The downloaded APK is copied into the private directory of the app, verified against the checksum of the release and
 * written into an install session, which the system hands over to the user to confirm the installation.
 *
 * The download is handled at most once. When it is handled already, the caller waits for the work that handles it, so
 * that the download isn't lost when the app was started by the broadcast of the download manager of the system.
 */
internal class ApkFinisher(
    private val systemDownloads: SystemDownloads,
    private val pendingDownloadStore: PendingDownloadStore,
    private val apkVerifier: ApkVerifier,
    private val keptApks: KeptApkStore,
    private val logger: Logger,
    private val state: MutableStateFlow<UpdateInstallState>,
    private val writeSession: (File) -> Int?,
    private val onSessionCommitted: (Int) -> Unit = {},
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val claimMutex = Mutex()
    private val workMutex = Mutex()
    private val inProgress = AtomicReference<FinishWork?>(null)

    /**
     * Handles the download with the id [downloadId] that is finished.
     *
     * When the download is handled already, this call waits for the work that handles it.
     */
    suspend fun finish(downloadId: Long) {
        val claim = claim(downloadId)
        if (!claim.isOwner) {
            // The download is handled already, so this call waits for the work that handles it.
            claim.work.completed.await()
            return
        }
        try {
            withContext(ioDispatcher) {
                workMutex.withLock { handle(downloadId) }
            }
        } catch (cancellation: CancellationException) {
            // The handling was cancelled, the state must not stay in the middle of the installation.
            failUnfinished()
            throw cancellation
        } catch (_: Exception) {
            // The state must not stay in the middle of the installation, the failure is reported to the user.
            logger.error(TAG) { "Finishing the download of the app update failed." }
            failUnfinished()
        } finally {
            claim.work.completed.complete(Unit)
            inProgress.compareAndSet(claim.work, null)
        }
    }

    /**
     * Hands the kept APK [file] over to the package installer.
     */
    fun installKeptApk(file: File, tag: String, sha256: String) {
        state.value = UpdateInstallState.Verifying
        writeInstallSession(file, tag, sha256)
    }

    /**
     * Reports the failure of the download with the id [downloadId] to the user and cleans the download up.
     */
    fun fail(reason: UpdateInstallFailure, downloadId: Long) {
        pendingDownloadStore.clear()
        systemDownloads.remove(downloadId)
        state.value = UpdateInstallState.Failed(reason)
    }

    /**
     * Reports the failure of the download with the id [downloadId], when it is still the pending download.
     *
     * The check is done while no finished download is handled, so that a download that was just finished and removed
     * isn't reported as failed.
     */
    suspend fun failPending(reason: UpdateInstallFailure, downloadId: Long) {
        workMutex.withLock {
            if (pendingDownloadStore.load()?.downloadId == downloadId) {
                fail(reason, downloadId)
            }
        }
    }

    /**
     * Reports the failure of an installation that was cancelled to the user.
     *
     * The state is only changed when the installation isn't finished yet.
     */
    fun failUnfinished() {
        val current = state.value
        if (current is UpdateInstallState.Downloading || current is UpdateInstallState.Verifying) {
            state.value = UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD)
        }
    }

    private fun handle(downloadId: Long) {
        val pending = pendingDownloadStore.load()
        if (pending == null || pending.downloadId != downloadId) {
            // The download isn't the one the app is waiting for.
            return
        }
        val downloaded = downloadedFile(downloadId)
        if (downloaded == null) {
            fail(UpdateInstallFailure.DOWNLOAD, downloadId)
            return
        }
        installDownloadedFile(downloaded, pending, downloadId)
    }

    private fun downloadedFile(downloadId: Long): File? {
        return (systemDownloads.query(downloadId) as? SystemDownloadStatus.Successful)?.file
    }

    private fun installDownloadedFile(downloaded: File, pending: PendingDownload, downloadId: Long) {
        state.value = UpdateInstallState.Verifying
        // The private copy is made before the download is given up, so that the download isn't lost when the app is
        // closed in between.
        val copy = apkVerifier.copyToPrivateDir(
            source = downloaded,
            targetDir = keptApks.directory(),
            fileName = keptApks.fileName(pending.tag),
        )
        if (copy == null) {
            fail(UpdateInstallFailure.DOWNLOAD, downloadId)
            return
        }
        // The private copy exists, so the download isn't needed anymore.
        pendingDownloadStore.clear()
        systemDownloads.remove(downloadId)
        val verification = apkVerifier.verify(copy, pending.sha256, pending.sizeBytes)
        if (verification != ApkVerification.Valid) {
            copy.delete()
            state.value = UpdateInstallState.Failed(failureOf(verification))
            return
        }
        writeInstallSession(copy, pending.tag, pending.sha256)
    }

    private fun writeInstallSession(file: File, tag: String, sha256: String) {
        // The APK is kept, so that the installation can be repeated without a new download.
        keptApks.save(tag, sha256)
        val sessionId = writeSession(file)
        if (sessionId == null) {
            logger.error(TAG) { "Writing the APK into the install session failed." }
            state.value = UpdateInstallState.Failed(UpdateInstallFailure.REJECTED)
            return
        }
        state.value = UpdateInstallState.WaitingForConfirmation
        // The session is watched, so that the user is told about the progress of the installation.
        onSessionCommitted(sessionId)
    }

    private fun failureOf(verification: ApkVerification): UpdateInstallFailure {
        return when (verification) {
            ApkVerification.ChecksumMismatch, ApkVerification.SizeMismatch -> UpdateInstallFailure.CHECKSUM
            else -> UpdateInstallFailure.DOWNLOAD
        }
    }

    private suspend fun claim(downloadId: Long): Claim {
        return claimMutex.withLock {
            val current = inProgress.get()
            if (current != null && current.downloadId == downloadId) {
                Claim(work = current, isOwner = false)
            } else {
                val work = FinishWork(downloadId = downloadId, completed = CompletableDeferred())
                inProgress.set(work)
                Claim(work = work, isOwner = true)
            }
        }
    }

    private class FinishWork(val downloadId: Long, val completed: CompletableDeferred<Unit>)

    private class Claim(val work: FinishWork, val isOwner: Boolean)
}
