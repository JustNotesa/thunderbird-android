package net.thunderbird.feature.updater.internal.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.thunderbird.feature.updater.api.AppUpdateChecker
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import net.thunderbird.feature.updater.api.AppUpdateSettings
import net.thunderbird.feature.updater.api.UpdateInstallState

/**
 * Schedules the daily background update check and keeps the schedule in sync with the user settings.
 *
 * The check runs over an unmetered network when the update is downloaded in the background and the download may only
 * run over Wi-Fi. A downloaded update is installed without asking, while the device is idle and charging.
 */
class AppUpdateScheduler(
    private val context: Context,
    private val settings: AppUpdateSettings,
    private val checker: AppUpdateChecker,
    private val installer: AppUpdateInstaller,
    private val coroutineScope: CoroutineScope,
    private val workManager: () -> WorkManager = { WorkManager.getInstance(context) },
) {
    fun start() {
        applySchedule()

        coroutineScope.launch {
            combine(
                settings.autoCheckEnabled,
                settings.backgroundDownloadEnabled,
                settings.downloadOnlyOnWifi,
            ) { _, _, _ -> Unit }
                .collect {
                    applySchedule()
                }
        }

        coroutineScope.launch {
            combine(
                installer.state,
                settings.autoInstallWhenIdle,
                settings.backgroundDownloadEnabled,
            ) { state, autoInstallWhenIdle, backgroundDownloadEnabled ->
                state is UpdateInstallState.ReadyToInstall && autoInstallWhenIdle && backgroundDownloadEnabled
            }
                .collect { installWhenIdle ->
                    applyAutoInstall(installWhenIdle)
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
            .setRequiredNetworkType(networkType())
            .build()

        val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        workManager().enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * The network the check needs; the download of the update may only run over Wi-Fi.
     */
    private fun networkType(): NetworkType {
        val onlyOnWifi = settings.backgroundDownloadEnabled.value && settings.downloadOnlyOnWifi.value
        return if (onlyOnWifi) NetworkType.UNMETERED else NetworkType.CONNECTED
    }

    private fun applyAutoInstall(installWhenIdle: Boolean) {
        if (installWhenIdle) {
            enqueueAutoInstall()
        } else {
            workManager().cancelUniqueWork(AUTO_INSTALL_WORK_NAME)
        }
    }

    private fun enqueueAutoInstall() {
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .build()

        val request = OneTimeWorkRequestBuilder<AutoInstallWorker>()
            .setConstraints(constraints)
            .build()

        workManager().enqueueUniqueWork(AUTO_INSTALL_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "mein_thunderbird_update_check"
        const val AUTO_INSTALL_WORK_NAME = "mein_thunderbird_auto_install"

        private const val CHECK_INTERVAL_HOURS = 24L
    }
}
