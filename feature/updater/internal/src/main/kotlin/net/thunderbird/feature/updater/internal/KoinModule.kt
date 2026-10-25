package net.thunderbird.feature.updater.internal

import java.io.File
import java.util.concurrent.TimeUnit
import net.thunderbird.feature.updater.api.AppUpdateChecker
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import net.thunderbird.feature.updater.api.AppUpdateSettings
import net.thunderbird.feature.updater.internal.background.AppUpdateScheduler
import net.thunderbird.feature.updater.internal.background.UpdateNotifier
import net.thunderbird.feature.updater.internal.check.DefaultAppUpdateChecker
import net.thunderbird.feature.updater.internal.check.GithubReleaseClient
import net.thunderbird.feature.updater.internal.install.ApkDownloader
import net.thunderbird.feature.updater.internal.install.PackageInstallerAppUpdateInstaller
import net.thunderbird.feature.updater.internal.settings.PreferencesAppUpdateSettings
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

private const val TIMEOUT_SECONDS = 30L
private const val UPDATE_DOWNLOAD_DIR = "app-updates"

// Own client under a qualifier so the updater never replaces or depends on another module's OkHttpClient binding.
private val updaterHttpClient = named("UpdaterOkHttpClient")

val featureUpdaterModule = module {
    single(updaterHttpClient) {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // Redirects from HTTPS to HTTP aren't followed, the APK is only downloaded over HTTPS.
            .followSslRedirects(false)
            .build()
    }
    single { PreferencesAppUpdateSettings(androidContext()) } bind AppUpdateSettings::class
    single<AppUpdateChecker> {
        DefaultAppUpdateChecker(
            config = get(),
            client = GithubReleaseClient(httpClient = get(updaterHttpClient)),
            logger = get(),
        )
    }
    single<AppUpdateInstaller> {
        PackageInstallerAppUpdateInstaller(
            context = androidContext(),
            downloader = ApkDownloader(
                httpClient = get(updaterHttpClient),
                downloadDir = File(androidContext().cacheDir, UPDATE_DOWNLOAD_DIR),
            ),
            logger = get(),
            // The download runs in an app wide scope, so that it isn't stopped when the screen that started the
            // installation is closed.
            coroutineScope = get(named("AppCoroutineScope")),
        )
    }
    single { UpdateNotifier(androidContext()) }
    single {
        AppUpdateScheduler(
            context = androidContext(),
            settings = get(),
            checker = get(),
            coroutineScope = get(named("AppCoroutineScope")),
        )
    }
}
