package net.thunderbird.feature.updater.internal.install

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.thunderbird.components.core.logging.Logger
import net.thunderbird.feature.updater.api.UpdateInstallState

private const val TAG = "DownloadPoll"

/**
 * Watches the download of the APK with the download manager of the system.
 *
 * The download manager of the system can finish a download without the app noticing it, so the download is polled.
 * The poll keeps running as long as the download is the pending download of the app. It doesn't stop when the state of
 * the app changes, because the download can be finished while the app doesn't know it yet.
 */
internal class DownloadPoll(
    private val systemDownloads: SystemDownloads,
    private val pendingDownloadStore: PendingDownloadStore,
    private val state: MutableStateFlow<UpdateInstallState>,
    private val coroutineScope: CoroutineScope,
    private val pollIntervalMillis: Long,
    private val logger: Logger,
    private val onDownloadFinished: suspend (Long) -> Unit,
    private val onDownloadFailed: suspend (Long) -> Unit,
) {

    private val pollMutex = Mutex()
    private val pollJob = AtomicReference<Job?>(null)

    /** The status of the download that was logged the last time. */
    @Volatile
    private var loggedStatus: String? = null

    /**
     * Watches the download with the id [downloadId] until it isn't the pending download anymore.
     */
    suspend fun watch(downloadId: Long) {
        pollMutex.withLock {
            // Only one download is watched at a time.
            pollJob.get()?.cancel()
            val job = coroutineScope.launch { watchUntilDone(downloadId) }
            pollJob.set(job)
        }
    }

    /**
     * Continues the download [pending], for example after the app was closed.
     *
     * A running download is watched again, a finished download is handed over and a failed download is reported.
     */
    suspend fun resume(pending: PendingDownload?) {
        if (pending == null) {
            return
        }

        val status = systemDownloads.query(pending.downloadId)
        logStatus(status)
        when (status) {
            is SystemDownloadStatus.Running -> {
                state.value = UpdateInstallState.Downloading(
                    downloadedBytes = status.downloadedBytes,
                    totalBytes = status.totalBytes,
                    waiting = status.waiting,
                )
                watch(pending.downloadId)
            }

            is SystemDownloadStatus.Successful -> onDownloadFinished(pending.downloadId)

            else -> onDownloadFailed(pending.downloadId)
        }
    }

    /** Stops watching the download. */
    fun stop() {
        pollJob.getAndSet(null)?.cancel()
    }

    private suspend fun watchUntilDone(downloadId: Long) {
        while (pendingDownloadStore.load()?.downloadId == downloadId) {
            delay(pollIntervalMillis)
            poll(downloadId)
        }
    }

    private suspend fun poll(downloadId: Long) {
        try {
            val status = systemDownloads.query(downloadId)
            logStatus(status)
            handleStatus(downloadId, status)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // A download that can't be queried is polled again, the download manager of the system may answer later.
            logger.warn(TAG) { "The status of the download couldn't be read." }
        }
    }

    private suspend fun handleStatus(downloadId: Long, status: SystemDownloadStatus) {
        when (status) {
            is SystemDownloadStatus.Running -> reportProgress(status)
            is SystemDownloadStatus.Successful -> onDownloadFinished(downloadId)
            else -> onDownloadFailed(downloadId)
        }
    }

    private fun reportProgress(status: SystemDownloadStatus.Running) {
        state.update { current ->
            // A finished download must not be reported as running again.
            if (current is UpdateInstallState.Downloading) {
                UpdateInstallState.Downloading(
                    downloadedBytes = status.downloadedBytes,
                    totalBytes = status.totalBytes,
                    waiting = status.waiting,
                )
            } else {
                current
            }
        }
    }

    private fun logStatus(status: SystemDownloadStatus) {
        val description = descriptionOf(status)
        if (description == loggedStatus) return

        loggedStatus = description
        logger.info(TAG) { "The download of the app update is $description." }
    }

    private fun descriptionOf(status: SystemDownloadStatus): String {
        return when (status) {
            is SystemDownloadStatus.Running ->
                status.waiting?.let { waiting -> "waiting: ${waiting.name.lowercase()}" } ?: "downloading"

            is SystemDownloadStatus.Successful -> "finished"

            SystemDownloadStatus.Failed -> "failed"

            SystemDownloadStatus.NotFound -> "not found"
        }
    }
}
