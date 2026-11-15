package net.thunderbird.feature.updater.internal.install

import java.io.File
import net.thunderbird.feature.updater.api.DownloadWaiting

/**
 * Downloads files with the download manager of the system.
 *
 * The download is done by the system, so it keeps running when the app is closed.
 */
internal interface SystemDownloads {

    /**
     * Starts the download of [request].
     *
     * @return the id of the started download, `null` when the download couldn't be started.
     */
    fun enqueue(request: SystemDownloadRequest): Long?

    /**
     * Returns the status of the download with the id [downloadId].
     */
    fun query(downloadId: Long): SystemDownloadStatus

    /**
     * Removes the download with the id [downloadId] together with the file it downloaded.
     */
    fun remove(downloadId: Long)
}

/** Describes the file that should be downloaded by the download manager of the system. */
internal data class SystemDownloadRequest(
    val url: String,
    val fileName: String,
    val title: String,
    val description: String,
    /** Whether the download may only run over an unmetered network (Wi-Fi). */
    val onlyOnWifi: Boolean = false,
)

/** The status of a download that is handled by the download manager of the system. */
internal sealed interface SystemDownloadStatus {
    /**
     * The download isn't finished yet.
     *
     * @param downloadedBytes the number of bytes downloaded so far.
     * @param totalBytes the total number of bytes to download, `-1` when the size of the download isn't known.
     * @param waiting why the download manager of the system doesn't download right now, `null` while it downloads.
     */
    data class Running(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val waiting: DownloadWaiting? = null,
    ) : SystemDownloadStatus

    /** The download finished successfully. [file] is the downloaded file. */
    data class Successful(val file: File) : SystemDownloadStatus

    /** The download failed. */
    data object Failed : SystemDownloadStatus

    /** There is no download with the requested id. */
    data object NotFound : SystemDownloadStatus
}
