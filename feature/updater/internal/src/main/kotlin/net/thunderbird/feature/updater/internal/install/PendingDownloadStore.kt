package net.thunderbird.feature.updater.internal.install

import android.content.Context
import androidx.core.content.edit
import java.io.File

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
        return pendingDownload(downloadId, tag, sha256, sizeBytes, title)
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
)

/**
 * Keeps the APK of an app update that was already downloaded and verified.
 *
 * The file of the kept APK and the tag and the checksum it belongs to are stored, so that the APK can be found again
 * after the app was closed and the installation can be repeated without downloading the APK again.
 */
internal class KeptApkStore(private val context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /**
     * The directory the kept APKs are stored in.
     */
    fun directory(): File {
        return File(context.cacheDir, APK_DIR_NAME)
    }

    /**
     * The name of the file the APK of [tag] is stored under.
     */
    fun fileName(tag: String): String {
        val sanitized = tag.map { character -> if (character in TAG_CHARACTERS) character else '_' }.joinToString("")
        return sanitized + APK_EXTENSION
    }

    /**
     * Stores that the verified APK of [tag] with the checksum [sha256] is kept.
     */
    fun save(tag: String, sha256: String) {
        preferences.edit {
            putString(KEY_KEPT_TAG, tag)
            putString(KEY_KEPT_SHA_256, sha256)
        }
    }

    /**
     * Returns the kept APK, `null` when there is none.
     */
    fun load(): KeptApk? {
        val tag = preferences.getString(KEY_KEPT_TAG, null)
        val sha256 = preferences.getString(KEY_KEPT_SHA_256, null)
        return if (tag != null && sha256 != null) {
            KeptApk(tag = tag, sha256 = sha256)
        } else {
            null
        }
    }

    /**
     * Returns the file of the kept APK of [tag], when it is still intact.
     *
     * A damaged APK is removed, so that it is downloaded again.
     */
    fun verifiedFile(verifier: ApkVerifier, tag: String, sha256: String): File? {
        val kept = load()
        if (kept == null || kept.tag != tag) {
            return null
        }
        val file = File(directory(), fileName(kept.tag))
        return if (verifier.verify(file, sha256, UNKNOWN_SIZE_BYTES) == ApkVerification.Valid) {
            file
        } else {
            clear()
            null
        }
    }

    /**
     * Removes the kept APK and the files it was kept in.
     */
    fun clear() {
        directory().listFiles()?.forEach { file -> file.delete() }
        preferences.edit {
            remove(KEY_KEPT_TAG)
            remove(KEY_KEPT_SHA_256)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "updater_kept_apk"
        const val KEY_KEPT_TAG = "kept_tag"
        const val KEY_KEPT_SHA_256 = "kept_sha256"
        const val APK_DIR_NAME = "app-updates"
        const val APK_EXTENSION = ".apk"
        const val UNKNOWN_SIZE_BYTES = 0L
        const val TAG_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._-"
    }
}

/** The APK of an app update that was already downloaded and verified. */
internal data class KeptApk(
    val tag: String,
    val sha256: String,
)
