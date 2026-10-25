package net.thunderbird.feature.updater.internal.install

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import java.io.File

/**
 * Downloads files with the [DownloadManager] of the system.
 *
 * The download is done by the system, so it keeps running when the app is closed. The downloaded file is written into
 * the external files directory of the app, where the download manager of the system is allowed to write it.
 */
internal class DownloadManagerSystemDownloads(private val context: Context) : SystemDownloads {

    override fun enqueue(request: SystemDownloadRequest): Long? {
        val uri = Uri.parse(request.url)
        val downloadManager = downloadManager()
        val target = targetFile(request.fileName)
        return if (uri.scheme?.equals(HTTPS_SCHEME, ignoreCase = true) != true ||
            downloadManager == null ||
            target == null
        ) {
            // Only downloads over HTTPS are allowed and the download needs the download manager and a target file.
            null
        } else {
            try {
                downloadManager.enqueue(buildRequest(uri, target, request))
            } catch (_: Exception) {
                // The download couldn't be started, the caller has to report that to the user.
                null
            }
        }
    }

    override fun query(downloadId: Long): SystemDownloadStatus {
        val downloadManager = downloadManager() ?: return SystemDownloadStatus.Failed
        return try {
            readStatus(downloadManager, downloadId)
        } catch (_: Exception) {
            // A download that can't be queried failed, the caller has to report that to the user.
            SystemDownloadStatus.Failed
        }
    }

    override fun remove(downloadId: Long) {
        val downloadManager = downloadManager() ?: return
        try {
            downloadManager.remove(downloadId)
        } catch (_: Exception) {
            // The download is gone already, there is nothing left to remove.
        }
    }

    private fun readStatus(downloadManager: DownloadManager, downloadId: Long): SystemDownloadStatus {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query) ?: return SystemDownloadStatus.NotFound
        return cursor.use {
            if (it.moveToFirst()) {
                readCursorStatus(it)
            } else {
                SystemDownloadStatus.NotFound
            }
        }
    }

    private fun readCursorStatus(cursor: Cursor): SystemDownloadStatus {
        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        return when (status) {
            DownloadManager.STATUS_SUCCESSFUL -> downloadedFile(cursor)

            DownloadManager.STATUS_FAILED -> SystemDownloadStatus.Failed

            // A pending, a running and a paused download are all still on their way.
            else -> runningStatus(cursor)
        }
    }

    private fun runningStatus(cursor: Cursor): SystemDownloadStatus {
        val downloadedBytes = cursor.getLong(
            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
        )
        val totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        return SystemDownloadStatus.Running(downloadedBytes, totalBytes.takeIf { it > 0L } ?: UNKNOWN_TOTAL_BYTES)
    }

    private fun downloadedFile(cursor: Cursor): SystemDownloadStatus {
        val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
        val path = localUri?.let { uri -> Uri.parse(uri).path }
        val file = path?.let { filePath -> File(filePath) }
        return if (file != null && file.isFile && file.canRead()) {
            SystemDownloadStatus.Successful(file)
        } else {
            // The download succeeded, but the file it downloaded is gone or can't be read.
            SystemDownloadStatus.Failed
        }
    }

    private fun buildRequest(
        uri: Uri,
        target: File,
        request: SystemDownloadRequest,
    ): DownloadManager.Request {
        return DownloadManager.Request(uri)
            .setTitle(request.title)
            .setDescription(request.description)
            .setMimeType(APK_MIME_TYPE)
            .setDestinationUri(Uri.fromFile(target))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
    }

    private fun targetFile(fileName: String): File? {
        val downloadDir = context.getExternalFilesDir(null)?.let { dir -> File(dir, DOWNLOAD_DIR_NAME) }
        val target = downloadDir?.takeIf { dir -> dir.exists() || dir.mkdirs() }?.let { dir -> File(dir, fileName) }
        // A file with the same name would make the download fail.
        return target?.takeIf { file -> !file.exists() || file.delete() }
    }

    private fun downloadManager(): DownloadManager? {
        return context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
    }

    private companion object {
        const val HTTPS_SCHEME = "https"
        const val DOWNLOAD_DIR_NAME = "app-updates"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val UNKNOWN_TOTAL_BYTES = -1L
    }
}
