package net.thunderbird.feature.updater.internal.background

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.AppUpdateChecker
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import net.thunderbird.feature.updater.api.UpdateCheckResult
import net.thunderbird.feature.updater.api.UpdateInstallState
import net.thunderbird.feature.updater.internal.R
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
    fun `the notification about the installation is cancelled`() {
        // Arrange
        val notifier = UpdateNotifier(application)
        notifier.notifyInstalling(INSTALL_PROGRESS)

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
        const val INSTALL_PROGRESS = 0.5f
    }
}

private class FakeUpdatedAppChecker : AppUpdateChecker {
    override var isEnabled: Boolean = true

    override val currentReleaseTag: String = "mein-thunderbird-2026-10-25"

    override suspend fun check(): UpdateCheckResult = UpdateCheckResult.UpToDate
}

private class FakeUpdatedAppInstaller : AppUpdateInstaller {
    private val _state = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    override val state: StateFlow<UpdateInstallState> = _state

    override fun canInstallPackages(): Boolean = true

    override fun openInstallPermissionSettings() = Unit

    override suspend fun install(update: AppUpdate) = Unit

    override fun resetState() {
        _state.value = UpdateInstallState.Idle
    }
}
