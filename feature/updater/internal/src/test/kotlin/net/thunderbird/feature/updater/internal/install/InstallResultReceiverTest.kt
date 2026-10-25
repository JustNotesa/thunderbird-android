package net.thunderbird.feature.updater.internal.install

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageInstaller
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.feature.updater.api.UpdateInstallFailure
import net.thunderbird.feature.updater.api.UpdateInstallState
import net.thunderbird.feature.updater.internal.R
import net.thunderbird.feature.updater.internal.background.UpdateNotifier
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstallResultReceiverTest {
    private val application: Application = RuntimeEnvironment.getApplication()

    private lateinit var installerScope: CoroutineScope
    private lateinit var installer: PackageInstallerAppUpdateInstaller
    private lateinit var notifier: UpdateNotifier
    private lateinit var testSubject: InstallResultReceiver

    @Before
    fun setUp() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        installerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        installer = PackageInstallerAppUpdateInstaller(
            context = application,
            systemDownloads = FakeSystemDownloads(),
            pendingDownloadStore = PendingDownloadStore(application),
            apkVerifier = ApkVerifier(),
            logger = TestLogger(),
            notifier = UpdateNotifier(application),
            coroutineScope = installerScope,
            sessionRegistrar = FakeInstallSessionRegistrar(),
        )
        notifier = UpdateNotifier(application)
        testSubject = InstallResultReceiver()
    }

    @After
    fun tearDown() {
        installerScope.cancel()
    }

    @Test
    fun `pending user action starts the confirmation activity and reports the status`() {
        // Arrange
        val confirmationIntent = Intent(CONFIRMATION_ACTION)

        // Act
        testSubject.handleStatus(
            context = application,
            status = PackageInstaller.STATUS_PENDING_USER_ACTION,
            confirmationIntent = confirmationIntent,
            installer = installer,
            notifier = notifier,
        )

        // Assert
        val startedIntent = shadowOf(application).nextStartedActivity
        assertThat(startedIntent).isNotNull()
        assertThat(startedIntent?.action).isEqualTo(CONFIRMATION_ACTION)
        assertThat(installer.state.value).isEqualTo(UpdateInstallState.WaitingForConfirmation)
    }

    @Test
    fun `pending user action posts a notification that opens the confirmation`() {
        // Arrange
        val confirmationIntent = Intent(CONFIRMATION_ACTION)

        // Act
        testSubject.handleStatus(
            context = application,
            status = PackageInstaller.STATUS_PENDING_USER_ACTION,
            confirmationIntent = confirmationIntent,
            installer = installer,
            notifier = notifier,
        )

        // Assert
        val notification = shadowOf(notificationManager()).getNotification(
            UpdateNotifier.CONFIRM_NOTIFICATION_TAG,
            UpdateNotifier.CONFIRM_NOTIFICATION_ID,
        )
        assertThat(notification).isNotNull()
        assertThat(notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()).isEqualTo(
            application.getString(R.string.updater_confirm_notification_text),
        )
    }

    @Test
    fun `the confirmation notification is removed when the installation failed`() {
        // Arrange
        testSubject.handleStatus(
            context = application,
            status = PackageInstaller.STATUS_PENDING_USER_ACTION,
            confirmationIntent = Intent(CONFIRMATION_ACTION),
            installer = installer,
            notifier = notifier,
        )
        assertThat(notificationCount()).isEqualTo(1)

        // Act
        testSubject.handleStatus(
            context = application,
            status = PackageInstaller.STATUS_FAILURE_ABORTED,
            confirmationIntent = null,
            installer = installer,
            notifier = notifier,
        )

        // Assert
        assertThat(notificationCount()).isEqualTo(0)
    }

    @Test
    fun `aborted installation is reported as cancelled`() {
        // Act
        testSubject.handleStatus(
            context = application,
            status = PackageInstaller.STATUS_FAILURE_ABORTED,
            confirmationIntent = null,
            installer = installer,
            notifier = notifier,
        )

        // Assert
        assertThat(installer.state.value).isEqualTo(
            UpdateInstallState.Failed(UpdateInstallFailure.CANCELLED),
        )
    }

    @Test
    fun `rejected installation is reported as rejected`() {
        // Act
        testSubject.handleStatus(
            context = application,
            status = PackageInstaller.STATUS_FAILURE_STORAGE,
            confirmationIntent = null,
            installer = installer,
            notifier = notifier,
        )

        // Assert
        assertThat(installer.state.value).isEqualTo(
            UpdateInstallState.Failed(UpdateInstallFailure.REJECTED),
        )
    }

    @Test
    fun `a missing installer does not crash`() {
        // Act
        testSubject.handleStatus(
            context = application,
            status = PackageInstaller.STATUS_FAILURE_ABORTED,
            confirmationIntent = null,
            installer = null,
            notifier = notifier,
        )

        // Assert
        assertThat(shadowOf(application).nextStartedActivity).isNull()
    }

    private fun notificationManager(): NotificationManager {
        return application.getSystemService(NotificationManager::class.java)
    }

    private fun notificationCount(): Int {
        return shadowOf(notificationManager()).allNotifications.size
    }

    private companion object {
        const val CONFIRMATION_ACTION = "com.android.packageinstaller.CONFIRM_INSTALL"
    }
}
