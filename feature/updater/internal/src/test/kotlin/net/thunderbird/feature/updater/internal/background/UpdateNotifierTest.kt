package net.thunderbird.feature.updater.internal.background

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import net.thunderbird.feature.updater.api.AppUpdate
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateNotifierTest {
    private val application: Application = RuntimeEnvironment.getApplication()

    private lateinit var testSubject: UpdateNotifier

    @Before
    fun setUp() {
        testSubject = UpdateNotifier(application)
    }

    @Test
    fun `the update notification is posted with its own id`() {
        // Arrange
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        // Act
        val posted = testSubject.notifyUpdateAvailable(createUpdate())

        // Assert
        assertThat(posted).isTrue()
        assertThat(
            notification(UpdateNotifier.UPDATE_NOTIFICATION_TAG, UpdateNotifier.UPDATE_NOTIFICATION_ID),
        ).isNotNull()
    }

    @Test
    fun `the update notification is not posted without the permission`() {
        // Arrange
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        // Act
        val posted = testSubject.notifyUpdateAvailable(createUpdate())

        // Assert
        assertThat(posted).isFalse()
        assertThat(notificationCount()).isEqualTo(0)
    }

    @Test
    fun `the confirmation notification doesn't replace the update notification`() {
        // Arrange
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        testSubject.notifyUpdateAvailable(createUpdate())

        // Act
        testSubject.notifyConfirmInstall(Intent(CONFIRMATION_ACTION))

        // Assert
        assertThat(notificationCount()).isEqualTo(2)
        assertThat(
            notification(UpdateNotifier.UPDATE_NOTIFICATION_TAG, UpdateNotifier.UPDATE_NOTIFICATION_ID),
        ).isNotNull()
        assertThat(
            notification(UpdateNotifier.CONFIRM_NOTIFICATION_TAG, UpdateNotifier.CONFIRM_NOTIFICATION_ID),
        ).isNotNull()
    }

    @Test
    fun `the confirmation notification is cancelled`() {
        // Arrange
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        testSubject.notifyConfirmInstall(Intent(CONFIRMATION_ACTION))

        // Act
        testSubject.cancelConfirmInstall()

        // Assert
        assertThat(
            notification(UpdateNotifier.CONFIRM_NOTIFICATION_TAG, UpdateNotifier.CONFIRM_NOTIFICATION_ID),
        ).isNull()
    }

    @Test
    fun `the update notification is cancelled`() {
        // Arrange
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        testSubject.notifyUpdateAvailable(createUpdate())

        // Act
        testSubject.cancelUpdateAvailable()

        // Assert
        assertThat(
            notification(UpdateNotifier.UPDATE_NOTIFICATION_TAG, UpdateNotifier.UPDATE_NOTIFICATION_ID),
        ).isNull()
    }

    @Test
    fun `the updated notification is posted with its own id`() {
        // Arrange
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        // Act
        val posted = testSubject.notifyAppUpdated(RELEASE_TAG)

        // Assert
        assertThat(posted).isTrue()
        assertThat(
            notification(UpdateNotifier.UPDATED_NOTIFICATION_TAG, UpdateNotifier.UPDATED_NOTIFICATION_ID),
        ).isNotNull()
    }

    @Test
    fun `the updated notification is not posted without the permission`() {
        // Arrange
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        // Act
        val posted = testSubject.notifyAppUpdated(RELEASE_TAG)

        // Assert
        assertThat(posted).isFalse()
        assertThat(notificationCount()).isEqualTo(0)
    }

    @Test
    fun `the updated notification is on a channel with a high importance`() {
        // Arrange
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        // Act
        testSubject.notifyAppUpdated(RELEASE_TAG)

        // Assert
        val channel = notificationManager().getNotificationChannel(UPDATED_CHANNEL_ID)
        assertThat(channel).isNotNull()
        assertThat(channel?.importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
        assertThat(
            notification(UpdateNotifier.UPDATED_NOTIFICATION_TAG, UpdateNotifier.UPDATED_NOTIFICATION_ID)?.channelId,
        ).isEqualTo(UPDATED_CHANNEL_ID)
    }

    @Test
    fun `the installing notification is posted with its own id`() {
        // Arrange
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        // Act
        testSubject.notifyInstalling(INSTALL_PROGRESS)

        // Assert
        val installing = notification(
            UpdateNotifier.INSTALLING_NOTIFICATION_TAG,
            UpdateNotifier.INSTALLING_NOTIFICATION_ID,
        )
        assertThat(installing).isNotNull()
        assertThat(installing?.extras?.getInt(Notification.EXTRA_PROGRESS)).isEqualTo(INSTALL_PROGRESS_PERCENT)
    }

    @Test
    fun `the installing notification is ongoing`() {
        // Arrange
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        // Act
        testSubject.notifyInstalling(INSTALL_PROGRESS)

        // Assert
        val flags = notification(
            UpdateNotifier.INSTALLING_NOTIFICATION_TAG,
            UpdateNotifier.INSTALLING_NOTIFICATION_ID,
        )?.flags ?: 0
        assertThat(flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
    }

    @Test
    fun `the installing notification shows an unknown progress`() {
        // Arrange
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        // Act
        testSubject.notifyInstalling(UNKNOWN_PROGRESS)

        // Assert
        val installing = notification(
            UpdateNotifier.INSTALLING_NOTIFICATION_TAG,
            UpdateNotifier.INSTALLING_NOTIFICATION_ID,
        )
        assertThat(installing?.extras?.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)).isEqualTo(true)
    }

    @Test
    fun `the installing notification is cancelled`() {
        // Arrange
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        testSubject.notifyInstalling(INSTALL_PROGRESS)

        // Act
        testSubject.cancelInstalling()

        // Assert
        assertThat(
            notification(UpdateNotifier.INSTALLING_NOTIFICATION_TAG, UpdateNotifier.INSTALLING_NOTIFICATION_ID),
        ).isNull()
    }

    private fun notification(tag: String, id: Int) =
        shadowOf(notificationManager()).getNotification(tag, id)

    private fun notificationManager(): NotificationManager {
        return application.getSystemService(NotificationManager::class.java)
    }

    private fun notificationCount(): Int {
        return shadowOf(notificationManager()).allNotifications.size
    }

    private fun createUpdate() = AppUpdate(
        tag = TAG,
        title = "Test release",
        releaseNotes = "Release notes",
        apkUrl = "https://example.test/app.apk",
        apkSizeBytes = 0L,
        apkSha256 = null,
    )

    private companion object {
        const val TAG = "test-tag"
        const val RELEASE_TAG = "mein-thunderbird-2026-10-25"
        const val CONFIRMATION_ACTION = "com.android.packageinstaller.CONFIRM_INSTALL"
        const val UPDATED_CHANNEL_ID = "app_updated"
        const val INSTALL_PROGRESS = 0.5f
        const val INSTALL_PROGRESS_PERCENT = 50
        const val UNKNOWN_PROGRESS = -1f
    }
}
