package com.fsck.k9.job

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import net.thunderbird.core.preference.network.NetworkSettingsPreferenceManager
import net.thunderbird.core.preference.network.NetworkSettings
import net.thunderbird.legacy.logging.Log

private const val TAG = "OfflineDownloadWorkerManager"

/**
 * Schedules the background job downloading all messages of all accounts.
 */
class OfflineDownloadWorkerManager(
    private val workManager: WorkManager,
    private val networkSettingsManager: NetworkSettingsPreferenceManager,
    private val statusStore: OfflineDownloadStatusStore,
) {
    /**
     * Schedules the offline download job.
     *
     * This has to be called after the offline download settings have been changed. Pass the new settings then,
     * because saving them takes a moment.
     *
     * @param isSettingsChange `true` when the settings were changed. Only then the jobs are replaced, so their
     *   conditions follow the settings. Otherwise a download that is running keeps running, e.g. when the app starts.
     */
    fun schedule(
        settings: NetworkSettings = networkSettingsManager.getConfig(),
        isSettingsChange: Boolean = false,
    ) {
        if (!settings.offlineDownload) {
            cancelWork()
            return
        }

        val constraints = createConstraints(settings)
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            if (isSettingsChange) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            createPeriodicWorkRequest(constraints),
        )
        workManager.enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            if (isSettingsChange) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            createOneTimeWorkRequest(constraints),
        )

        if (statusStore.status.value.state == OfflineDownloadState.OFF) {
            statusStore.update { it.copy(state = OfflineDownloadState.WAITING) }
        }
    }

    private fun cancelWork() {
        Log.v(TAG, "Offline download is disabled. Canceling offline download work.")
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(ONE_TIME_WORK_NAME)
        statusStore.update { it.copy(state = OfflineDownloadState.OFF) }
    }

    private fun createConstraints(settings: NetworkSettings): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresStorageNotLow(true)
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(settings.offlineDownloadOnlyWhileCharging)
            .build()
    }

    private fun createPeriodicWorkRequest(constraints: Constraints): PeriodicWorkRequest {
        // The one-time job starts right away, the periodic one picks up new messages later
        return PeriodicWorkRequestBuilder<OfflineDownloadWorker>(REPEAT_INTERVAL_HOURS, TimeUnit.HOURS)
            .setInitialDelay(REPEAT_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .build()
    }

    private fun createOneTimeWorkRequest(constraints: Constraints): OneTimeWorkRequest {
        return OneTimeWorkRequestBuilder<OfflineDownloadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_DELAY_MINUTES, TimeUnit.MINUTES)
            .addTag(WORK_TAG)
            .build()
    }

    companion object {
        const val PERIODIC_WORK_NAME = "OfflineDownload"
        const val ONE_TIME_WORK_NAME = "OfflineDownloadNow"
        const val WORK_TAG = "OfflineDownload"
        private const val REPEAT_INTERVAL_HOURS = 6L
        private const val BACKOFF_DELAY_MINUTES = 1L
    }
}
