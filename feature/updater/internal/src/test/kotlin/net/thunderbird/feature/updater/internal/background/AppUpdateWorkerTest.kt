package net.thunderbird.feature.updater.internal.background

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.work.testing.TestListenableWorkerBuilder
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.AppUpdateChecker
import net.thunderbird.feature.updater.api.AppUpdateConfig
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import net.thunderbird.feature.updater.api.ReleaseNotesResult
import net.thunderbird.feature.updater.api.UpdateCheckResult
import net.thunderbird.feature.updater.api.UpdateInstallState
import net.thunderbird.feature.updater.internal.R
import net.thunderbird.feature.updater.internal.settings.PreferencesAppUpdateSettings
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

@RunWith(RobolectricTestRunner::class)
class AppUpdateWorkerTest {
    private val application: Application = RuntimeEnvironment.getApplication()

    private val checker = FakeUpdateChecker()
    private val installer = FakeWorkerInstaller()
    private val settings = PreferencesAppUpdateSettings(application)

    @Before
    fun setUp() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        startKoin {
            modules(
                module {
                    single<AppUpdateChecker> { checker }
                    single { settings }
                    single { UpdateNotifier(application) }
                    single<AppUpdateInstaller> { installer }
                    single { AppUpdateConfig(repository = REPOSITORY, currentReleaseTag = INSTALLED_TAG) }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `available update is notified`() = runTest {
        // Arrange
        checker.result = UpdateCheckResult.Available(availableUpdate(FIRST_TAG))
        val testSubject = createWorker()

        // Act
        testSubject.doWork()

        // Assert
        assertThat(notificationText()).isEqualTo(expectedNotificationText(FIRST_TAG))
    }

    @Test
    fun `a newer release is notified again`() = runTest {
        // Arrange
        checker.result = UpdateCheckResult.Available(availableUpdate(FIRST_TAG))
        val testSubject = createWorker()
        testSubject.doWork()

        // Act
        checker.result = UpdateCheckResult.Available(availableUpdate(SECOND_TAG))
        testSubject.doWork()

        // Assert
        assertThat(notificationText()).isEqualTo(expectedNotificationText(SECOND_TAG))
    }

    @Test
    fun `the notified tag is not remembered when the notification was not posted`() = runTest {
        // Arrange
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        checker.result = UpdateCheckResult.Available(availableUpdate(FIRST_TAG))
        val testSubject = createWorker()

        // Act
        testSubject.doWork()

        // Assert
        // Without the notification the user doesn't know about the release, so it has to be checked again.
        assertThat(settings.lastNotifiedTag).isNull()
    }

    @Test
    fun `the notified tag is remembered when the notification was posted`() = runTest {
        // Arrange
        checker.result = UpdateCheckResult.Available(availableUpdate(FIRST_TAG))
        val testSubject = createWorker()

        // Act
        testSubject.doWork()

        // Assert
        assertThat(settings.lastNotifiedTag).isEqualTo(FIRST_TAG)
    }

    @Test
    fun `the update is downloaded in the background without a notification`() = runTest {
        // Arrange
        settings.setBackgroundDownloadEnabled(true)
        checker.result = UpdateCheckResult.Available(availableUpdate(FIRST_TAG))
        val testSubject = createWorker()

        // Act
        testSubject.doWork()

        // Assert
        // The user isn't told about the update, because it is downloaded right away.
        assertThat(installer.downloadedUpdates).hasSize(1)
        assertThat(installer.downloadedUpdates.first().tag).isEqualTo(FIRST_TAG)
        assertThat(notificationText()).isNull()
    }

    @Test
    fun `the download of the background only runs over wifi when it is asked for`() = runTest {
        // Arrange
        settings.setBackgroundDownloadEnabled(true)
        settings.setDownloadOnlyOnWifi(true)
        checker.result = UpdateCheckResult.Available(availableUpdate(FIRST_TAG))
        val testSubject = createWorker()

        // Act
        testSubject.doWork()

        // Assert
        assertThat(installer.onlyOnWifi).isEqualTo(true)
    }

    @Test
    fun `the update is notified when the background download is switched off`() = runTest {
        // Arrange
        settings.setBackgroundDownloadEnabled(false)
        checker.result = UpdateCheckResult.Available(availableUpdate(FIRST_TAG))
        val testSubject = createWorker()

        // Act
        testSubject.doWork()

        // Assert
        assertThat(installer.downloadedUpdates).hasSize(0)
        assertThat(notificationText()).isEqualTo(expectedNotificationText(FIRST_TAG))
    }

    @Test
    fun `a newer update is downloaded in the background when an older one is ready`() = runTest {
        // Arrange
        settings.setBackgroundDownloadEnabled(true)
        installer.setState(UpdateInstallState.ReadyToInstall(FIRST_TAG))
        checker.result = UpdateCheckResult.Available(availableUpdate(SECOND_TAG))
        val testSubject = createWorker()

        // Act
        testSubject.doWork()

        // Assert
        assertThat(installer.downloadedUpdates).hasSize(1)
        assertThat(installer.downloadedUpdates.first().tag).isEqualTo(SECOND_TAG)
    }

    @Test
    fun `the update is notified when the download of the background is running`() = runTest {
        // Arrange
        settings.setBackgroundDownloadEnabled(true)
        installer.setState(UpdateInstallState.Downloading(0L, 0L))
        checker.result = UpdateCheckResult.Available(availableUpdate(FIRST_TAG))
        val testSubject = createWorker()

        // Act
        testSubject.doWork()

        // Assert
        assertThat(installer.downloadedUpdates).hasSize(0)
        assertThat(notificationText()).isEqualTo(expectedNotificationText(FIRST_TAG))
    }

    private fun createWorker() =
        TestListenableWorkerBuilder.from(application, AppUpdateWorker::class.java).build()

    private fun notificationText(): String? {
        val notificationManager = application.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(notificationManager).allNotifications.firstOrNull()
        return notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    }

    private fun expectedNotificationText(tag: String): String {
        return application.getString(R.string.updater_notification_text, releaseTitle(tag))
    }

    private fun availableUpdate(tag: String) = AppUpdate(
        tag = tag,
        title = releaseTitle(tag),
        releaseNotes = "",
        apkUrl = "https://example.com/$tag.apk",
        apkSizeBytes = 0L,
        apkSha256 = null,
    )

    private fun releaseTitle(tag: String) = "Mein Thunderbird $tag"

    private companion object {
        private const val FIRST_TAG = "mein-thunderbird-2026-10-25"
        private const val SECOND_TAG = "mein-thunderbird-2026-10-26"
        private const val INSTALLED_TAG = "mein-thunderbird-2026-10-24"
        private const val REPOSITORY = "JustNotesa/thunderbird-android"
    }
}

private class FakeUpdateChecker : AppUpdateChecker {
    override val isEnabled: Boolean = true

    override val currentReleaseTag: String = "mein-thunderbird-2026-10-24"

    var result: UpdateCheckResult = UpdateCheckResult.UpToDate

    override suspend fun check(): UpdateCheckResult = result

    override suspend fun installedReleaseNotes(): ReleaseNotesResult = ReleaseNotesResult.Disabled
}

private class FakeWorkerInstaller : AppUpdateInstaller {
    private val _state = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    override val state: StateFlow<UpdateInstallState> = _state

    val downloadedUpdates = mutableListOf<AppUpdate>()
    var onlyOnWifi: Boolean? = null

    fun setState(state: UpdateInstallState) {
        _state.value = state
    }

    override fun canInstallPackages(): Boolean = true

    override fun openInstallPermissionSettings() = Unit

    override suspend fun install(update: AppUpdate) = Unit

    override suspend fun download(update: AppUpdate, onlyOnWifi: Boolean) {
        downloadedUpdates.add(update)
        this.onlyOnWifi = onlyOnWifi
    }

    override suspend fun installDownloaded() = Unit

    override suspend fun refresh() = Unit

    override fun resetState() {
        _state.value = UpdateInstallState.Idle
    }
}
