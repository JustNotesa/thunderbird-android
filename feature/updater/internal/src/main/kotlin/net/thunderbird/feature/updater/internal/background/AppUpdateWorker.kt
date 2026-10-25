package net.thunderbird.feature.updater.internal.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import net.thunderbird.feature.updater.api.AppUpdateChecker
import net.thunderbird.feature.updater.api.UpdateCheckResult
import net.thunderbird.feature.updater.internal.settings.PreferencesAppUpdateSettings
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Checks in the background whether a new version of the app is available and tells the user about it.
 */
internal class AppUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    private val checker: AppUpdateChecker by inject()
    private val settings: PreferencesAppUpdateSettings by inject()
    private val notifier: UpdateNotifier by inject()

    override suspend fun doWork(): Result {
        if (!settings.autoCheckEnabled.value || !checker.isEnabled) {
            return Result.success()
        }

        return when (val result = checker.check()) {
            is UpdateCheckResult.Available -> {
                val update = result.update
                if (update.tag != settings.lastNotifiedTag && notifier.notifyUpdateAvailable(update)) {
                    // The tag is only remembered when the notification was posted. Otherwise the user would
                    // never be told about this release.
                    settings.lastNotifiedTag = update.tag
                }
                Result.success()
            }

            // Failures are shown in the About screen, the background check stays quiet.
            is UpdateCheckResult.Failed -> Result.success()

            UpdateCheckResult.Disabled -> Result.success()

            UpdateCheckResult.UpToDate -> Result.success()
        }
    }
}
