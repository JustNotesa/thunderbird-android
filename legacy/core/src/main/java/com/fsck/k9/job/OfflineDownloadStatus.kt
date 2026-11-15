package com.fsck.k9.job

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The state of the background job downloading all messages of all accounts.
 */
enum class OfflineDownloadState {
    /** The offline download is switched off. */
    OFF,

    /** Waiting for the requirements to be met, e.g. Wi-Fi, charging or the turn of this job. */
    WAITING,

    /** Messages are being downloaded. */
    RUNNING,

    /** Paused because the messages would use more storage than allowed. */
    PAUSED_STORAGE_LIMIT,

    /** Paused because the device is running out of storage. */
    PAUSED_LOW_STORAGE,

    /** Every message of every folder is on the device. */
    DONE,

    /** Downloading failed. */
    FAILED,
}

/**
 * Describes how the offline download is doing.
 */
data class OfflineDownloadStatus(
    /** The current state of the background job. */
    val state: OfflineDownloadState,
    /** The time the last run was started. */
    val lastRunStart: Long?,
    /** The duration of the last run. */
    val lastRunDurationMillis: Long?,
    /** The number of folders that are downloaded. */
    val folderCount: Int,
    /** The number of folders that are completely on the device. */
    val completeFolderCount: Int,
    /** The time when every folder was last completely on the device. This is how up to date the local copy is. */
    val lastCompleteTime: Long?,
    /** A short description of the last error. */
    val lastError: String?,
)

/**
 * Stores the [OfflineDownloadStatus] so it survives a restart of the app.
 */
class OfflineDownloadStatusStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val statusFlow = MutableStateFlow(readStatus())

    /** The current status of the offline download. */
    val status: StateFlow<OfflineDownloadStatus> = statusFlow.asStateFlow()

    /**
     * Updates the status and writes it to disk.
     */
    @Synchronized
    fun update(transform: (OfflineDownloadStatus) -> OfflineDownloadStatus) {
        val newStatus = transform(statusFlow.value)
        writeStatus(newStatus)
        statusFlow.value = newStatus
    }

    private fun readStatus(): OfflineDownloadStatus {
        return OfflineDownloadStatus(
            state = readState(),
            lastRunStart = readLong(KEY_LAST_RUN_START),
            lastRunDurationMillis = readLong(KEY_LAST_RUN_DURATION),
            folderCount = preferences.getInt(KEY_FOLDER_COUNT, 0),
            completeFolderCount = preferences.getInt(KEY_COMPLETE_FOLDER_COUNT, 0),
            lastCompleteTime = readLong(KEY_LAST_COMPLETE_TIME),
            lastError = preferences.getString(KEY_LAST_ERROR, null),
        )
    }

    private fun readState(): OfflineDownloadState {
        val name = preferences.getString(KEY_STATE, null)
        return OfflineDownloadState.entries.firstOrNull { it.name == name } ?: OfflineDownloadState.OFF
    }

    private fun readLong(key: String): Long? {
        return if (preferences.contains(key)) preferences.getLong(key, 0L) else null
    }

    private fun writeStatus(status: OfflineDownloadStatus) {
        val editor = preferences.edit()
        editor.putString(KEY_STATE, status.state.name)
        writeLong(editor, KEY_LAST_RUN_START, status.lastRunStart)
        writeLong(editor, KEY_LAST_RUN_DURATION, status.lastRunDurationMillis)
        editor.putInt(KEY_FOLDER_COUNT, status.folderCount)
        editor.putInt(KEY_COMPLETE_FOLDER_COUNT, status.completeFolderCount)
        writeLong(editor, KEY_LAST_COMPLETE_TIME, status.lastCompleteTime)
        writeString(editor, KEY_LAST_ERROR, status.lastError)
        editor.apply()
    }

    private fun writeLong(editor: SharedPreferences.Editor, key: String, value: Long?) {
        if (value == null) {
            editor.remove(key)
        } else {
            editor.putLong(key, value)
        }
    }

    private fun writeString(editor: SharedPreferences.Editor, key: String, value: String?) {
        if (value == null) {
            editor.remove(key)
        } else {
            editor.putString(key, value)
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "offline_download_status"
        private const val KEY_STATE = "state"
        private const val KEY_LAST_RUN_START = "lastRunStart"
        private const val KEY_LAST_RUN_DURATION = "lastRunDurationMillis"
        private const val KEY_FOLDER_COUNT = "folderCount"
        private const val KEY_COMPLETE_FOLDER_COUNT = "completeFolderCount"
        private const val KEY_LAST_COMPLETE_TIME = "lastCompleteTime"
        private const val KEY_LAST_ERROR = "lastError"
    }
}
