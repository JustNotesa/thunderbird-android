package net.thunderbird.feature.updater.internal.background

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.work.testing.TestListenableWorkerBuilder
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.AppUpdateChecker
import net.thunderbird.feature.updater.api.UpdateCheckResult
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
    }
}

private class FakeUpdateChecker : AppUpdateChecker {
    override val isEnabled: Boolean = true

    override val currentReleaseTag: String = "mein-thunderbird-2026-10-24"

    var result: UpdateCheckResult = UpdateCheckResult.UpToDate

    override suspend fun check(): UpdateCheckResult = result
}
