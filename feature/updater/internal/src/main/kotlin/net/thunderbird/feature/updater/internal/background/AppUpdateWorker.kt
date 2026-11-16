package net.thunderbird.feature.updater.internal.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.AppUpdateChecker
import net.thunderbird.feature.updater.api.AppUpdateConfig
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import net.thunderbird.feature.updater.api.UpdateCheckResult
import net.thunderbird.feature.updater.api.UpdateInstallState
import net.thunderbird.feature.updater.internal.check.ReleaseTag
import net.thunderbird.feature.updater.internal.settings.PreferencesAppUpdateSettings
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Checks in the background whether a new version of the app is available and tells the user about it.
 *
 * When the update is downloaded in the background, the update is downloaded right away, so that the installation is
 * instant. The user isn't told about the update then; the notification about the downloaded update comes later.
 */
internal class AppUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    private val checker: AppUpdateChecker by inject()
    private val settings: PreferencesAppUpdateSettings by inject()
    private val notifier: UpdateNotifier by inject()
    private val installer: AppUpdateInstaller by inject()
    private val config: AppUpdateConfig by inject()

    override suspend fun doWork(): Result {
        if (!settings.autoCheckEnabled.value || !checker.isEnabled) {
            return Result.success()
        }

        return when (val result = checker.check()) {
            is UpdateCheckResult.Available -> handleAvailable(result.update)

            // Failures are shown in the About screen, the background check stays quiet.
            is UpdateCheckResult.Failed -> Result.success()

            UpdateCheckResult.Disabled -> Result.success()

            UpdateCheckResult.UpToDate -> Result.success()
        }
    }

    private suspend fun handleAvailable(update: AppUpdate): Result {
        if (downloadInBackground(update)) {
            // The update is downloaded right away, so that the installation is instant.
            installer.download(update, settings.downloadOnlyOnWifi.value)
            return Result.success()
        }

        if (update.tag != settings.lastNotifiedTag && notifier.notifyUpdateAvailable(update)) {
            // The tag is only remembered when the notification was posted. Otherwise the user would
            // never be told about this release.
            settings.lastNotifiedTag = update.tag
        }
        return Result.success()
    }

    /**
     * Whether the update should be downloaded in the background.
     */
    private fun downloadInBackground(update: AppUpdate): Boolean {
        if (!settings.backgroundDownloadEnabled.value) {
            return false
        }

        return when (val state = installer.state.value) {
            is UpdateInstallState.Idle -> true

            is UpdateInstallState.Failed -> true

            // A newer update replaces the update that was downloaded before.
            is UpdateInstallState.ReadyToInstall -> isOlder(state.tag, update.tag)

            else -> false
        }
    }

    /**
     * Whether the release [keptTag] is older than the release [newTag].
     */
    private fun isOlder(keptTag: String, newTag: String): Boolean {
        val prefix = config.tagPrefix
        val kept = ReleaseTag.parse(keptTag, prefix)
        val newer = ReleaseTag.parse(newTag, prefix)
        return kept != null && newer != null && kept < newer
    }
}
