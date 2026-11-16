package net.thunderbird.feature.updater.internal.install

import kotlinx.coroutines.flow.MutableStateFlow
import net.thunderbird.feature.updater.api.UpdateInstallState
import net.thunderbird.feature.updater.internal.background.UpdateNotifier

/**
 * Handles a download of an app update that was started in the background.
 *
 * The verified APK is kept, so that the update can be installed without another download, and the user is told that
 * the update is ready to be installed. A download of the background that fails isn't allowed to fail unnoticed.
 */
internal class BackgroundDownload(
    private val keptApks: KeptApkStore,
    private val notifier: UpdateNotifier,
    private val state: MutableStateFlow<UpdateInstallState>,
) {

    /**
     * Keeps the verified APK of the release [tag] with the checksum [sha256], so that the update can be installed
     * later.
     */
    fun keep(tag: String, sha256: String, title: String) {
        keptApks.save(tag, sha256)
        state.value = UpdateInstallState.ReadyToInstall(tag)
        notifier.notifyUpdateReady(tag, title)
    }

    /**
     * Tells the user that the download of the background failed.
     */
    fun failed(pending: PendingDownload?) {
        if (pending != null && !pending.installWhenDone) {
            notifier.notifyDownloadFailed()
        }
    }
}
