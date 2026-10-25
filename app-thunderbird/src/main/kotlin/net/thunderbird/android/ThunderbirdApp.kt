package net.thunderbird.android

import app.k9mail.feature.telemetry.api.TelemetryManager
import com.fsck.k9.K9
import net.thunderbird.app.common.FeatureFlagApplication
import net.thunderbird.feature.updater.internal.background.AppUpdateScheduler
import org.koin.android.ext.android.inject
import org.koin.core.module.Module

class ThunderbirdApp : FeatureFlagApplication() {
    private val telemetryManager: TelemetryManager by inject()
    private val appUpdateScheduler: AppUpdateScheduler by inject()

    override fun provideAppModule(): Module = appModule
    override val appName: String = "thunderbird"
    override val appVersion: String = BuildConfig.VERSION_NAME

    override fun onCreate() {
        super.onCreate()

        initializeTelemetry()
        appUpdateScheduler.start()
    }

    private fun initializeTelemetry() {
        telemetryManager.init(
            uploadEnabled = K9.isTelemetryEnabled,
            releaseChannel = BuildConfig.GLEAN_RELEASE_CHANNEL,
            versionCode = BuildConfig.VERSION_CODE,
            versionName = BuildConfig.VERSION_NAME,
        )
    }
}
