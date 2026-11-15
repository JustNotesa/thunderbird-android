package net.thunderbird.feature.updater.internal.install

import android.content.Context
import androidx.core.content.edit

/**
 * Keeps the download of an app update that is handled by the download manager of the system.
 *
 * The download is stored, so that it can be found again when the app was closed while the download was running.
 */
internal class PendingDownloadStore(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /**
     * Stores the download [download] as the pending download.
     */
    fun save(download: PendingDownload) {
        preferences.edit {
            putLong(KEY_DOWNLOAD_ID, download.downloadId)
            putString(KEY_TAG, download.tag)
            putString(KEY_SHA_256, download.sha256)
            putLong(KEY_SIZE_BYTES, download.sizeBytes)
            putString(KEY_TITLE, download.title)
            putBoolean(KEY_INSTALL_WHEN_DONE, download.installWhenDone)
        }
    }

    /**
     * Returns the pending download, `null` when there is none or when it is incomplete.
     */
    fun load(): PendingDownload? {
        val downloadId = preferences.getLong(KEY_DOWNLOAD_ID, NO_DOWNLOAD_ID)
        val tag = preferences.getString(KEY_TAG, null)
        val sha256 = preferences.getString(KEY_SHA_256, null)
        val sizeBytes = preferences.getLong(KEY_SIZE_BYTES, NO_SIZE_BYTES)
        val title = preferences.getString(KEY_TITLE, null)
        // Downloads of older versions of the app were always installed when they were done.
        val installWhenDone = preferences.getBoolean(KEY_INSTALL_WHEN_DONE, true)
        return pendingDownload(downloadId, tag, sha256, sizeBytes, title, installWhenDone)
    }

    /**
     * Removes the pending download.
     */
    fun clear() {
        preferences.edit { clear() }
    }

    private fun pendingDownload(
        downloadId: Long,
        tag: String?,
        sha256: String?,
        sizeBytes: Long,
        title: String?,
        installWhenDone: Boolean,
    ): PendingDownload? {
        val download = tag?.let { tagValue ->
            sha256?.let { sha256Value ->
                title?.let { titleValue ->
                    PendingDownload(
                        downloadId = downloadId,
                        tag = tagValue,
                        sha256 = sha256Value,
                        sizeBytes = sizeBytes,
                        title = titleValue,
                        installWhenDone = installWhenDone,
                    )
                }
            }
        }
        return download?.takeIf { it.downloadId != NO_DOWNLOAD_ID }
    }

    private companion object {
        const val PREFERENCES_NAME = "updater_download"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_TAG = "tag"
        const val KEY_SHA_256 = "sha256"
        const val KEY_SIZE_BYTES = "size_bytes"
        const val KEY_TITLE = "title"
        const val KEY_INSTALL_WHEN_DONE = "install_when_done"
        const val NO_DOWNLOAD_ID = -1L
        const val NO_SIZE_BYTES = -1L
    }
}

/** A download of an app update that is handled by the download manager of the system. */
internal data class PendingDownload(
    val downloadId: Long,
    val tag: String,
    val sha256: String,
    val sizeBytes: Long,
    val title: String,
    /** Whether the update is installed when the download is done, instead of waiting for the user. */
    val installWhenDone: Boolean,
)
