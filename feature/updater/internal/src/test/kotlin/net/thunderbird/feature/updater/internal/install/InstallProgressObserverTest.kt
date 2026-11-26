package net.thunderbird.feature.updater.internal.install

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageInstaller
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlinx.coroutines.flow.MutableStateFlow
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.feature.updater.api.UpdateInstallState
import net.thunderbird.feature.updater.internal.background.InstallingNotifier
import net.thunderbird.feature.updater.internal.background.UpdateNotifier
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val SESSION_ID = 7
private const val OTHER_SESSION_ID = 8
private const val STAGING_PROGRESS = 0.5f
private const val INSTALL_PROGRESS = 0.9f

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstallProgressObserverTest {
    private val application: Application = RuntimeEnvironment.getApplication()

    private lateinit var state: MutableStateFlow<UpdateInstallState>
    private lateinit var registrar: FakeInstallSessionRegistrar
    private lateinit var testSubject: InstallProgressObserver

    @Before
    fun setUp() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        state = MutableStateFlow(UpdateInstallState.WaitingForConfirmation)
        registrar = FakeInstallSessionRegistrar()
        testSubject = InstallProgressObserver(
            registrar = registrar,
            state = state,
            notifier = UpdateNotifier(application),
            logger = TestLogger(),
        )
        testSubject.watch(SESSION_ID)
    }

    @Test
    fun `the callback is registered for the watched session`() {
        // Assert
        assertThat(registrar.registered).contains(testSubject)
    }

    @Test
    fun `a session that exists isn't gone`() {
        // Act
        val gone = testSubject.isSessionGone()

        // Assert
        assertThat(gone).isFalse()
    }

    @Test
    fun `a session that doesn't exist anymore is gone`() {
        // Arrange
        registrar.sessionsExist = false

        // Act
        val gone = testSubject.isSessionGone()

        // Assert
        assertThat(gone).isTrue()
    }

    @Test
    fun `no session is gone when none is watched`() {
        // Arrange
        registrar.sessionsExist = false
        testSubject.stopWatching()

        // Act
        val gone = testSubject.isSessionGone()

        // Assert
        assertThat(gone).isFalse()
    }

    @Test
    fun `the progress of the staging of the APK isn't reported`() {
        // Act
        testSubject.onProgress(SESSION_ID, STAGING_PROGRESS)

        // Assert
        assertThat(state.value).isEqualTo(UpdateInstallState.WaitingForConfirmation)
        assertThat(installingNotification()).isNull()
    }

    @Test
    fun `the installation is reported`() {
        // Act
        testSubject.onProgress(SESSION_ID, INSTALL_PROGRESS)

        // Assert
        assertThat(state.value).isEqualTo(UpdateInstallState.Installing)
        assertThat(installingNotification()).isNotNull()
    }

    @Test
    fun `the installation is reported only once`() {
        // Arrange
        val notifier = FakeInstallingNotifier()
        val observer = InstallProgressObserver(
            registrar = registrar,
            state = state,
            notifier = notifier,
            logger = TestLogger(),
        )
        observer.watch(SESSION_ID)

        // Act
        observer.onProgress(SESSION_ID, INSTALL_PROGRESS)
        observer.onProgress(SESSION_ID, INSTALL_PROGRESS)

        // Assert
        assertThat(state.value).isEqualTo(UpdateInstallState.Installing)
        assertThat(notifier.installingNotifications).isEqualTo(1)
    }

    @Test
    fun `the progress of another session is ignored`() {
        // Act
        testSubject.onProgress(OTHER_SESSION_ID, INSTALL_PROGRESS)

        // Assert
        assertThat(state.value).isEqualTo(UpdateInstallState.WaitingForConfirmation)
        assertThat(installingNotification()).isNull()
    }

    @Test
    fun `a failed installation cancels the notification about the installation`() {
        // Arrange
        testSubject.onProgress(SESSION_ID, INSTALL_PROGRESS)

        // Act
        testSubject.onFinished(SESSION_ID, false)

        // Assert
        assertThat(installingNotification()).isNull()
        assertThat(registrar.registered).isEmpty()
    }

    @Test
    fun `a failed installation that didn't start cancels the notification about the installation`() {
        // Act
        testSubject.onFinished(SESSION_ID, false)

        // Assert
        assertThat(installingNotification()).isNull()
    }

    @Test
    fun `a successful installation keeps the notification about the installation`() {
        // Arrange
        testSubject.onProgress(SESSION_ID, INSTALL_PROGRESS)

        // Act
        testSubject.onFinished(SESSION_ID, true)

        // Assert
        assertThat(installingNotification()).isNotNull()
        assertThat(registrar.registered).isEmpty()
    }

    private fun installingNotification(): Notification? {
        return shadowOf(notificationManager()).getNotification(
            UpdateNotifier.INSTALLING_NOTIFICATION_TAG,
            UpdateNotifier.INSTALLING_NOTIFICATION_ID,
        )
    }

    private fun notificationManager(): NotificationManager {
        return application.getSystemService(NotificationManager::class.java)
    }
}

/**
 * A fake of the registrar of the install sessions, for the tests of the updater.
 */
internal class FakeInstallSessionRegistrar : InstallSessionRegistrar {

    val registered = mutableListOf<PackageInstaller.SessionCallback>()

    /** Whether the install sessions exist in the system. */
    var sessionsExist = true

    override fun register(callback: PackageInstaller.SessionCallback) {
        registered.add(callback)
    }

    override fun unregister(callback: PackageInstaller.SessionCallback) {
        registered.remove(callback)
    }

    override fun sessionExists(sessionId: Int): Boolean = sessionsExist
}

/**
 * A fake of the notifier of the installation, for the tests of the updater.
 */
internal class FakeInstallingNotifier : InstallingNotifier {

    /** The number of times the user was told that the update is being installed. */
    var installingNotifications = 0

    /** The number of times the notification about the installation was removed. */
    var cancelledNotifications = 0

    override fun notifyInstalling() {
        installingNotifications++
    }

    override fun cancelInstalling() {
        cancelledNotifications++
    }
}
