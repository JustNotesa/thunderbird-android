package net.thunderbird.feature.updater.internal.install

import kotlinx.coroutines.flow.MutableStateFlow
import net.thunderbird.components.core.logging.Logger
import net.thunderbird.feature.updater.api.UpdateInstallState

private const val TAG = "StaleDownloads"

/**
 * Removes the downloads of releases that aren't newer than the installed one.
 *
 * Such a download is left over, for example when a newer release was installed another way. It is neither continued
 * nor reported as failed, because the user has nothing to do about it.
 */
internal class StaleDownloads(
    private val systemDownloads: SystemDownloads,
    private val pendingDownloadStore: PendingDownloadStore,
    private val keptApks: KeptApkStore,
    private val poll: DownloadPoll,
    private val state: MutableStateFlow<UpdateInstallState>,
    private val logger: Logger,
    private val isNewerThanInstalled: (String) -> Boolean,
) {

    /**
     * Silently removes the pending download and the kept APK of a release that isn't newer than the installed one.
     */
    fun drop() {
        val pending = pendingDownloadStore.load()
        if (pending != null && !isNewerThanInstalled(pending.tag)) {
            logger.info(TAG) { "The pending download of the app update isn't newer than the installed app." }
            poll.stop()
            systemDownloads.remove(pending.downloadId)
            pendingDownloadStore.clear()
            if (state.value is UpdateInstallState.Downloading) {
                state.value = UpdateInstallState.Idle
            }
        }
        val kept = keptApks.load()
        if (kept != null && !isNewerThanInstalled(kept.tag)) {
            logger.info(TAG) { "The kept app update isn't newer than the installed app." }
            keptApks.clear()
            if (state.value == UpdateInstallState.ReadyToInstall(kept.tag)) {
                state.value = UpdateInstallState.Idle
            }
        }
    }
}
