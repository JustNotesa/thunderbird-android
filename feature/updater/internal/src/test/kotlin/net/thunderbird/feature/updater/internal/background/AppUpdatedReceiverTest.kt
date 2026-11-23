package net.thunderbird.feature.updater.internal.background

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.AppUpdateChecker
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import net.thunderbird.feature.updater.api.ReleaseNotesResult
import net.thunderbird.feature.updater.api.UpdateCheckResult
import net.thunderbird.feature.updater.api.UpdateInstallState
import net.thunderbird.feature.updater.internal.R
import net.thunderbird.feature.updater.internal.install.ApkVerifier
import net.thunderbird.feature.updater.internal.install.FakeInstallSessionRegistrar
import net.thunderbird.feature.updater.internal.install.FakeSystemDownloads
import net.thunderbird.feature.updater.internal.install.KeptApkStore
import net.thunderbird.feature.updater.internal.install.PackageInstallerAppUpdateInstaller
import net.thunderbird.feature.updater.internal.install.PendingDownload
import net.thunderbird.feature.updater.internal.install.PendingDownloadStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdatedReceiverTest {
    private val application: Application = RuntimeEnvironment.getApplication()

    private lateinit var checker: FakeUpdatedAppChecker
    private lateinit var testSubject: AppUpdatedReceiver
    private val installerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        checker = FakeUpdatedAppChecker()

        startKoin {
            modules(
                module {
                    single<AppUpdateChecker> { checker }
                    single { UpdateNotifier(application) }
                    single<AppUpdateInstaller> { FakeUpdatedAppInstaller() }
                },
            )
        }

        testSubject = AppUpdatedReceiver()
    }

    @After
    fun tearDown() {
        installerScope.cancel()
        stopKoin()
    }

    @Test
    fun `the updated notification is posted with the release tag`() {
        // Act
        testSubject.onReceive(application, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        // Assert
        val notification = shadowOf(notificationManager()).getNotification(
            UpdateNotifier.UPDATED_NOTIFICATION_TAG,
            UpdateNotifier.UPDATED_NOTIFICATION_ID,
        )
        assertThat(notification).isNotNull()
        assertThat(notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()).isEqualTo(
            application.getString(R.string.updater_updated_notification_text, checker.currentReleaseTag),
        )
    }

    @Test
    fun `no notification is posted when the updater is disabled`() {
        // Arrange
        checker.isEnabled = false

        // Act
        testSubject.onReceive(application, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        // Assert
        assertThat(notificationCount()).isEqualTo(0)
    }

    @Test
    fun `the notifications of the pending update are cancelled`() {
        // Arrange
        val notifier = UpdateNotifier(application)
        notifier.notifyUpdateAvailable(createUpdate())
        notifier.notifyConfirmInstall(Intent(Intent.ACTION_PACKAGE_REPLACED))

        // Act
        testSubject.onReceive(application, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        // Assert
        assertThat(notificationCount()).isEqualTo(1)
        assertThat(
            shadowOf(notificationManager()).getNotification(
                UpdateNotifier.UPDATED_NOTIFICATION_TAG,
                UpdateNotifier.UPDATED_NOTIFICATION_ID,
            ),
        ).isNotNull()
    }

    @Test
    fun `a stale pending download is cleared silently`() {
        // Arrange
        val downloads = FakeSystemDownloads()
        val store = PendingDownloadStore(application)
        store.clear()
        KeptApkStore(application).clear()
        val realInstaller = PackageInstallerAppUpdateInstaller(
            context = application,
            systemDownloads = downloads,
            pendingDownloadStore = store,
            apkVerifier = ApkVerifier(),
            logger = TestLogger(),
            notifier = UpdateNotifier(application),
            coroutineScope = installerScope,
            sessionRegistrar = FakeInstallSessionRegistrar(),
            isNewerThanInstalled = { false },
        )
        stopKoin()
        startKoin {
            modules(
                module {
                    single<AppUpdateChecker> { checker }
                    single { UpdateNotifier(application) }
                    single<AppUpdateInstaller> { realInstaller }
                },
            )
        }
        store.save(
            PendingDownload(
                downloadId = STALE_DOWNLOAD_ID,
                tag = RELEASE_TAG,
                sha256 = "sha",
                sizeBytes = 1L,
                title = "Mein Thunderbird",
                installWhenDone = false,
            ),
        )

        // Act
        AppUpdatedReceiver().onReceive(application, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        // Assert
        assertThat(store.load()).isNull()
        assertThat(downloads.removedDownloadIds).containsExactly(STALE_DOWNLOAD_ID)
        assertThat(realInstaller.state.value).isEqualTo(UpdateInstallState.Idle)
        assertThat(
            shadowOf(notificationManager()).getNotification(
                UpdateNotifier.FAILED_NOTIFICATION_TAG,
                UpdateNotifier.FAILED_NOTIFICATION_ID,
            ),
        ).isNull()
    }

    @Test
    fun `the notification about the installation is cancelled`() {
        // Arrange
        val notifier = UpdateNotifier(application)
        notifier.notifyInstalling()

        // Act
        testSubject.onReceive(application, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        // Assert
        assertThat(
            shadowOf(notificationManager()).getNotification(
                UpdateNotifier.INSTALLING_NOTIFICATION_TAG,
                UpdateNotifier.INSTALLING_NOTIFICATION_ID,
            ),
        ).isNull()
    }

    private fun notificationManager(): NotificationManager {
        return application.getSystemService(NotificationManager::class.java)
    }

    private fun notificationCount(): Int {
        return shadowOf(notificationManager()).allNotifications.size
    }

    private fun createUpdate() = AppUpdate(
        tag = RELEASE_TAG,
        title = "Mein Thunderbird",
        releaseNotes = "",
        apkUrl = "https://example.test/app.apk",
        apkSizeBytes = 0L,
        apkSha256 = null,
    )

    private companion object {
        const val RELEASE_TAG = "mein-thunderbird-2026-10-25"
        const val STALE_DOWNLOAD_ID = 42L
    }
}

private class FakeUpdatedAppChecker : AppUpdateChecker {
    override var isEnabled: Boolean = true

    override val currentReleaseTag: String = "mein-thunderbird-2026-10-25"

    override suspend fun check(): UpdateCheckResult = UpdateCheckResult.UpToDate

    override suspend fun installedReleaseNotes(): ReleaseNotesResult = ReleaseNotesResult.Disabled
}

private class FakeUpdatedAppInstaller : AppUpdateInstaller {
    private val _state = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    override val state: StateFlow<UpdateInstallState> = _state

    override fun canInstallPackages(): Boolean = true

    override fun openInstallPermissionSettings() = Unit

    override suspend fun install(update: AppUpdate) = Unit

    override suspend fun download(update: AppUpdate, onlyOnWifi: Boolean) = Unit

    override suspend fun installDownloaded() = Unit

    override suspend fun refresh() = Unit

    override fun resetState() {
        _state.value = UpdateInstallState.Idle
    }
}
