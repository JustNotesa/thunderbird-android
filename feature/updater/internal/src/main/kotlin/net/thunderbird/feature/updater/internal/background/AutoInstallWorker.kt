package net.thunderbird.feature.updater.internal.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import net.thunderbird.feature.updater.api.UpdateInstallState
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Installs the update that was downloaded in the background.
 *
 * The work only runs while the device is idle and charging, so that the installation doesn't disturb the user. When
 * Android still needs the user to confirm the installation, the notification of the updater asks for it.
 */
internal class AutoInstallWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    private val installer: AppUpdateInstaller by inject()

    override suspend fun doWork(): Result {
        if (installer.state.value is UpdateInstallState.ReadyToInstall) {
            installer.installDownloaded()
        }
        return Result.success()
    }
}
