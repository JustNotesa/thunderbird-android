package net.thunderbird.feature.updater.internal.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.thunderbird.feature.updater.api.AppUpdateChecker
import net.thunderbird.feature.updater.api.AppUpdateSettings

/**
 * Schedules the daily background update check and keeps the schedule in sync with the user settings.
 */
class AppUpdateScheduler(
    private val context: Context,
    private val settings: AppUpdateSettings,
    private val checker: AppUpdateChecker,
    private val coroutineScope: CoroutineScope,
    private val workManager: () -> WorkManager = { WorkManager.getInstance(context) },
) {
    fun start() {
        applySchedule()

        coroutineScope.launch {
            settings.autoCheckEnabled.collect {
                applySchedule()
            }
        }
    }

    private fun applySchedule() {
        if (checker.isEnabled && settings.autoCheckEnabled.value) {
            enqueueUpdateCheck()
        } else {
            workManager().cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }

    private fun enqueueUpdateCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        workManager().enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "mein_thunderbird_update_check"

        private const val CHECK_INTERVAL_HOURS = 24L
    }
}
