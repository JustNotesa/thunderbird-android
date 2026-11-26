package net.thunderbird.feature.updater.internal.install

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageInstaller
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.DownloadWaiting
import net.thunderbird.feature.updater.api.UpdateInstallFailure
import net.thunderbird.feature.updater.api.UpdateInstallState
import net.thunderbird.feature.updater.internal.background.FakeReopenMarker
import net.thunderbird.feature.updater.internal.background.UpdateNotifier
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val TAG = "test-tag"
private const val APK_URL = "https://example.test/app.apk"
private const val APK_CONTENT = "apk content"
private const val OTHER_CONTENT = "other content"
private const val DOWNLOAD_ID = 42L
private const val UNKNOWN_DOWNLOAD_ID = 99L
private const val OTHER_DOWNLOAD_ID = 43L
private const val DOWNLOADED_BYTES = 5L
private const val POLL_INTERVAL_MILLIS = 500L
private const val SESSION_ID = 7
private const val TIMEOUT_MILLIS = 30_000L
private const val WAIT_INTERVAL_MILLIS = 10L
private const val NOW_MILLIS = 1_700_000_000_000L

@Suppress("LargeClass")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PackageInstallerAppUpdateInstallerTest {
    private val application: Application = RuntimeEnvironment.getApplication()

    private val apkSize = APK_CONTENT.length.toLong()

    private lateinit var tempDir: File
    private lateinit var downloads: FakeSystemDownloads
    private lateinit var store: PendingDownloadStore
    private lateinit var sessionWrites: MutableList<File>
    private lateinit var notifier: UpdateNotifier
    private lateinit var registrar: FakeInstallSessionRegistrar
    private lateinit var reopenMarker: FakeReopenMarker

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("apk-installer-test").toFile()
        downloads = FakeSystemDownloads()
        // The download runs, so that the state of the download is reported like it is in the app.
        downloads.status = SystemDownloadStatus.Running(0L, apkSize)
        store = PendingDownloadStore(application)
        store.clear()
        KeptApkStore(application).clear()
        sessionWrites = mutableListOf()
        notifier = UpdateNotifier(application)
        registrar = FakeInstallSessionRegistrar()
        reopenMarker = FakeReopenMarker()
        shadowOf(application.packageManager).setCanRequestPackageInstalls(true)
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `install without the install permission fails without downloading`() = runTest {
        // Arrange
        shadowOf(application.packageManager).setCanRequestPackageInstalls(false)
        val testSubject = createInstaller(backgroundScope)

        // Act
        testSubject.install(createUpdate())

        // Assert
        assertThat(testSubject.state.value).isEqualTo(
            UpdateInstallState.Failed(UpdateInstallFailure.PERMISSION_MISSING, TAG, NOW_MILLIS),
        )
        assertThat(downloads.requests).isEmpty()
    }

    @Test
    fun `install without a checksum fails without downloading`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)

        // Act
        testSubject.install(createUpdate(sha256 = null))

        // Assert
        assertThat(testSubject.state.value).isEqualTo(
            UpdateInstallState.Failed(UpdateInstallFailure.CHECKSUM, TAG, NOW_MILLIS),
        )
        assertThat(downloads.requests).isEmpty()
    }

    @Test
    fun `install starts the download of the APK`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)

        // Act
        testSubject.install(createUpdate())

        // Assert
        assertThat(testSubject.state.value).isEqualTo(UpdateInstallState.Downloading(0L, apkSize))
        assertThat(downloads.requests).hasSize(1)
        assertThat(downloads.requests.first().url).isEqualTo(APK_URL)
        assertThat(downloads.requests.first().fileName).isEqualTo("$TAG.apk")
    }

    @Test
    fun `the progress of the download is reported`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Running(DOWNLOADED_BYTES, apkSize)

        // Act
        testSubject.install(createUpdate())
        testScheduler.advanceTimeBy(POLL_INTERVAL_MILLIS)
        testScheduler.runCurrent()

        // Assert
        assertThat(testSubject.state.value).isEqualTo(
            UpdateInstallState.Downloading(DOWNLOADED_BYTES, apkSize),
        )
    }

    @Test
    fun `a finished download is verified and installed`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())
        testSubject.install(createUpdate())

        // Act
        testSubject.onDownloadFinished(DOWNLOAD_ID)

        // Assert
        assertThat(testSubject.state.value).isEqualTo(UpdateInstallState.WaitingForConfirmation)
        assertThat(sessionWrites).hasSize(1)
        assertThat(downloads.removedDownloadIds).contains(DOWNLOAD_ID)
    }

    @Test
    fun `a finished download is installed when the download is polled`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        testSubject.install(createUpdate())
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())

        // Act
        testScheduler.advanceTimeBy(POLL_INTERVAL_MILLIS)
        testScheduler.runCurrent()

        // Assert
        val state = awaitState(testSubject) { it is UpdateInstallState.WaitingForConfirmation }
        assertThat(state).isEqualTo(UpdateInstallState.WaitingForConfirmation)
    }

    @Test
    fun `the poll doesn't overwrite the state of a finished download`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        testSubject.install(createUpdate())
        // The poll of the download is waiting for the next poll.
        testScheduler.runCurrent()
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())
        testSubject.onDownloadFinished(DOWNLOAD_ID)
        // The download isn't there anymore, because it was removed after it was finished.
        downloads.status = SystemDownloadStatus.NotFound

        // Act
        testScheduler.advanceTimeBy(POLL_INTERVAL_MILLIS)
        testScheduler.runCurrent()

        // Assert
        assertThat(testSubject.state.value).isEqualTo(UpdateInstallState.WaitingForConfirmation)
    }

    @Test
    fun `the poll of an old download doesn't fail a new download`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        testSubject.install(createUpdate())
        // The poll of the download is waiting for the next poll.
        testScheduler.runCurrent()
        // Another download replaced the download of this update.
        store.save(createPendingDownload(downloadId = OTHER_DOWNLOAD_ID))
        downloads.status = SystemDownloadStatus.Failed

        // Act
        testScheduler.advanceTimeBy(POLL_INTERVAL_MILLIS)
        testScheduler.runCurrent()

        // Assert
        assertThat(testSubject.state.value).isEqualTo(UpdateInstallState.Downloading(0L, apkSize))
    }

    @Test
    fun `the download is started in the app scope and not on the calling thread`() = runTest {
        // Arrange
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val testSubject = createInstaller(appScope)

        // Act
        testSubject.install(createUpdate())
        awaitDownloadStarted(downloads)
        appScope.cancel()

        // Assert
        assertThat(downloads.lastEnqueueThread == Thread.currentThread().name).isFalse()
        assertThat(testSubject.state.value).isEqualTo(UpdateInstallState.Downloading(0L, apkSize))
    }

    @Test
    fun `the install work survives the cancellation of the caller`() = runTest {
        // Arrange
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val enqueueStarted = CountDownLatch(1)
        val allowEnqueue = CountDownLatch(1)
        downloads.onEnqueue = {
            enqueueStarted.countDown()
            allowEnqueue.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        }
        val testSubject = createInstaller(appScope)
        // The caller is a screen, which runs on the main thread.
        val caller = launch { testSubject.install(createUpdate()) }
        assertThat(awaitLatch(enqueueStarted)).isTrue()

        // Act
        // The screen is closed while the download is started.
        caller.cancel()
        allowEnqueue.countDown()

        // Assert
        val state = awaitState(testSubject) { it !is UpdateInstallState.Idle }
        appScope.cancel()
        assertThat(state).isEqualTo(UpdateInstallState.Downloading(0L, apkSize))
        assertThat(caller.isCancelled).isTrue()
        assertThat(store.load()?.downloadId).isEqualTo(DOWNLOAD_ID)
    }

    @Test
    fun `a cancelled install doesn't leave the download state behind`() = runTest {
        // Arrange
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val enqueueStarted = CountDownLatch(1)
        val allowEnqueue = CountDownLatch(1)
        downloads.onEnqueue = {
            enqueueStarted.countDown()
            allowEnqueue.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        }
        val testSubject = createInstaller(appScope)
        val caller = launch { testSubject.install(createUpdate()) }
        assertThat(awaitLatch(enqueueStarted)).isTrue()

        // Act
        // The app wide scope is cancelled while the download is started.
        appScope.cancel()
        allowEnqueue.countDown()

        // Assert
        val state = awaitState(testSubject) { it is UpdateInstallState.Failed }
        caller.join()
        assertThat(state).isEqualTo(UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD, TAG, NOW_MILLIS))
    }

    @Test
    fun `a failure while the download is finished doesn't leave the verifying state behind`() = runTest {
        // Arrange
        val testSubject = createInstaller(
            backgroundScope,
            writeSession = { error("The install session couldn't be written.") },
        )
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())
        testSubject.install(createUpdate())

        // Act
        testSubject.onDownloadFinished(DOWNLOAD_ID)

        // Assert
        assertThat(testSubject.state.value).isEqualTo(
            UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD, TAG, NOW_MILLIS),
        )
    }

    @Test
    fun `the download is given up after the APK was copied`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())
        // The state of the app at the moment the download is given up.
        var copyWhenRemoved = false
        downloads.onRemove = { copyWhenRemoved = keptApkFiles().isNotEmpty() }
        testSubject.install(createUpdate())

        // Act
        testSubject.onDownloadFinished(DOWNLOAD_ID)

        // Assert
        // The private copy of the APK exists, so the download isn't lost when the app is closed in between.
        assertThat(copyWhenRemoved).isTrue()
        assertThat(store.load()).isNull()
        assertThat(sessionWrites).hasSize(1)
    }

    @Test
    fun `a download with a wrong checksum fails and the files are removed`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile(content = OTHER_CONTENT))
        testSubject.install(createUpdate())

        // Act
        testSubject.onDownloadFinished(DOWNLOAD_ID)

        // Assert
        assertThat(testSubject.state.value).isEqualTo(
            UpdateInstallState.Failed(UpdateInstallFailure.CHECKSUM, TAG, NOW_MILLIS),
        )
        assertThat(downloads.removedDownloadIds).contains(DOWNLOAD_ID)
        assertThat(keptApkFiles()).isEmpty()
        assertThat(sessionWrites).isEmpty()
    }

    @Test
    fun `a failed download is reported`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Failed
        testSubject.install(createUpdate())

        // Act
        testScheduler.advanceTimeBy(POLL_INTERVAL_MILLIS)
        testScheduler.runCurrent()

        // Assert
        assertThat(testSubject.state.value).isEqualTo(
            UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD, TAG, NOW_MILLIS),
        )
        assertThat(downloads.removedDownloadIds).contains(DOWNLOAD_ID)
    }

    @Test
    fun `a finished download is handled only once`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())
        testSubject.install(createUpdate())

        // Act
        testSubject.onDownloadFinished(DOWNLOAD_ID)
        testSubject.onDownloadFinished(DOWNLOAD_ID)

        // Assert
        assertThat(sessionWrites).hasSize(1)
        assertThat(testSubject.state.value).isEqualTo(UpdateInstallState.WaitingForConfirmation)
    }

    @Test
    fun `the download of an unknown id is ignored`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)

        // Act
        testSubject.onDownloadFinished(UNKNOWN_DOWNLOAD_ID)

        // Assert
        assertThat(testSubject.state.value).isEqualTo(UpdateInstallState.Idle)
        assertThat(downloads.requests).isEmpty()
    }

    @Test
    fun `the download continues after the app was closed`() = runTest {
        // Arrange
        val firstInstaller = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Running(DOWNLOADED_BYTES, apkSize)
        firstInstaller.install(createUpdate())

        // Act
        val secondInstaller = createInstaller(backgroundScope)
        testScheduler.runCurrent()

        // Assert
        assertThat(secondInstaller.state.value).isEqualTo(
            UpdateInstallState.Downloading(DOWNLOADED_BYTES, apkSize),
        )
        assertThat(downloads.requests).hasSize(1)
    }

    @Test
    fun `resetState cancels the download`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        testSubject.install(createUpdate())

        // Act
        testSubject.resetState()

        // Assert
        assertThat(testSubject.state.value).isEqualTo(UpdateInstallState.Idle)
        assertThat(downloads.removedDownloadIds).containsExactly(DOWNLOAD_ID)
        assertThat(store.load()).isNull()
    }

    @Test
    fun `a second install of the same update doesn't download the APK again`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())
        testSubject.install(createUpdate())
        testSubject.onDownloadFinished(DOWNLOAD_ID)

        // Act
        testSubject.install(createUpdate())

        // Assert
        assertThat(downloads.requests).hasSize(1)
        assertThat(sessionWrites).hasSize(2)
        assertThat(testSubject.state.value).isEqualTo(UpdateInstallState.WaitingForConfirmation)
    }

    @Test
    fun `a damaged kept APK is downloaded again`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())
        testSubject.install(createUpdate())
        testSubject.onDownloadFinished(DOWNLOAD_ID)
        keptApkFiles().forEach { file -> file.writeText(OTHER_CONTENT) }
        // The new download is running.
        downloads.status = SystemDownloadStatus.Running(0L, apkSize)

        // Act
        testSubject.install(createUpdate())

        // Assert
        assertThat(downloads.requests).hasSize(2)
        assertThat(testSubject.state.value).isEqualTo(UpdateInstallState.Downloading(0L, apkSize))
    }

    @Test
    fun `a successful installation keeps the current state`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        testSubject.onInstallStatus(PackageInstaller.STATUS_PENDING_USER_ACTION)

        // Act
        testSubject.onInstallStatus(PackageInstaller.STATUS_SUCCESS)

        // Assert
        assertThat(testSubject.state.value).isEqualTo(UpdateInstallState.WaitingForConfirmation)
    }

    @Test
    fun `aborted installation is reported as cancelled`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)

        // Act
        testSubject.onInstallStatus(PackageInstaller.STATUS_FAILURE_ABORTED)

        // Assert
        assertThat(testSubject.state.value).isEqualTo(
            UpdateInstallState.Failed(UpdateInstallFailure.CANCELLED, null, NOW_MILLIS),
        )
    }

    @Test
    fun `rejected installation is reported as rejected`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)

        // Act
        testSubject.onInstallStatus(PackageInstaller.STATUS_FAILURE_STORAGE)

        // Assert
        assertThat(testSubject.state.value).isEqualTo(
            UpdateInstallState.Failed(UpdateInstallFailure.REJECTED, null, NOW_MILLIS),
        )
    }

    @Test
    fun `pending user action is reported as waiting for the confirmation`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)

        // Act
        testSubject.onInstallStatus(PackageInstaller.STATUS_PENDING_USER_ACTION)

        // Assert
        assertThat(testSubject.state.value).isEqualTo(UpdateInstallState.WaitingForConfirmation)
    }

    @Test
    fun `the install session is watched after it was written`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())
        testSubject.install(createUpdate())

        // Act
        testSubject.onDownloadFinished(DOWNLOAD_ID)

        // Assert
        assertThat(registrar.registered).hasSize(1)
    }

    @Test
    fun `a failed installation cancels the notification about the installation`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        testSubject.onInstallStatus(PackageInstaller.STATUS_PENDING_USER_ACTION)
        notifier.notifyInstalling()

        // Act
        testSubject.onInstallStatus(PackageInstaller.STATUS_FAILURE_ABORTED)

        // Assert
        assertThat(installingNotification()).isNull()
    }

    @Test
    fun `a new installation stops watching the old session`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())
        testSubject.install(createUpdate())
        testSubject.onDownloadFinished(DOWNLOAD_ID)

        // Act
        testSubject.install(createUpdate())

        // Assert
        assertThat(registrar.registered).hasSize(1)
    }

    @Test
    fun `the reason why the download waits is reported`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Running(DOWNLOADED_BYTES, apkSize, DownloadWaiting.NETWORK)

        // Act
        testSubject.install(createUpdate())
        testScheduler.advanceTimeBy(POLL_INTERVAL_MILLIS)
        testScheduler.runCurrent()

        // Assert
        assertThat(testSubject.state.value).isEqualTo(
            UpdateInstallState.Downloading(DOWNLOADED_BYTES, apkSize, DownloadWaiting.NETWORK),
        )
    }

    @Test
    fun `the poll continues when the status of the download can't be read`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Running(DOWNLOADED_BYTES, apkSize)
        testSubject.install(createUpdate())
        // The status of the download can't be read the first time.
        downloads.failingQueries = 1

        // Act
        testScheduler.advanceTimeBy(POLL_INTERVAL_MILLIS)
        testScheduler.runCurrent()
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())
        testScheduler.advanceTimeBy(POLL_INTERVAL_MILLIS)
        testScheduler.runCurrent()

        // Assert
        val state = awaitState(testSubject) { it is UpdateInstallState.WaitingForConfirmation }
        assertThat(state).isEqualTo(UpdateInstallState.WaitingForConfirmation)
    }

    @Test
    fun `refresh installs a download that was finished while the app didn't notice it`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Running(DOWNLOADED_BYTES, apkSize)
        testSubject.install(createUpdate())
        // The download manager of the system finished the download, the app didn't notice it.
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())

        // Act
        testSubject.refresh()

        // Assert
        assertThat(testSubject.state.value).isEqualTo(UpdateInstallState.WaitingForConfirmation)
        assertThat(sessionWrites).hasSize(1)
    }

    @Test
    fun `refresh reports a download that is gone as failed`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Running(DOWNLOADED_BYTES, apkSize)
        testSubject.install(createUpdate())
        // The download is gone.
        store.clear()

        // Act
        testSubject.refresh()

        // Assert
        assertThat(testSubject.state.value).isEqualTo(
            UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD, TAG, NOW_MILLIS),
        )
    }

    @Test
    fun `refresh reports a pending confirmation whose session is gone as cancelled`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())
        testSubject.install(createUpdate())
        testSubject.onDownloadFinished(DOWNLOAD_ID)
        registrar.sessionsExist = false

        // Act
        testSubject.refresh()

        // Assert
        assertThat(testSubject.state.value).isEqualTo(
            UpdateInstallState.Failed(UpdateInstallFailure.CANCELLED, TAG, NOW_MILLIS),
        )
        assertThat(registrar.registered).isEmpty()
    }

    @Test
    fun `refresh keeps a pending confirmation whose session still exists`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())
        testSubject.install(createUpdate())
        testSubject.onDownloadFinished(DOWNLOAD_ID)

        // Act
        testSubject.refresh()

        // Assert
        assertThat(testSubject.state.value).isEqualTo(UpdateInstallState.WaitingForConfirmation)
    }

    @Test
    fun `the marker to reopen the app is stored when the user's installation is handed to the system`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())
        testSubject.reopenAppAfterInstall()
        testSubject.install(createUpdate())

        // Act
        testSubject.onDownloadFinished(DOWNLOAD_ID)

        // Assert
        assertThat(reopenMarker.markedAtMillis()).isEqualTo(NOW_MILLIS)
    }

    @Test
    fun `no marker to reopen the app is stored for an installation that the user didn't start`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())
        testSubject.install(createUpdate())

        // Act
        testSubject.onDownloadFinished(DOWNLOAD_ID)

        // Assert
        assertThat(reopenMarker.markedAtMillis()).isNull()
    }

    @Test
    fun `cancelling the installation removes the marker to reopen the app`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())
        testSubject.reopenAppAfterInstall()
        testSubject.install(createUpdate())
        testSubject.onDownloadFinished(DOWNLOAD_ID)

        // Act
        testSubject.resetState()

        // Assert
        assertThat(reopenMarker.markedAtMillis()).isNull()
    }

    @Test
    fun `a failed installation removes the marker to reopen the app`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())
        testSubject.reopenAppAfterInstall()
        testSubject.install(createUpdate())
        testSubject.onDownloadFinished(DOWNLOAD_ID)

        // Act
        testSubject.onInstallStatus(PackageInstaller.STATUS_FAILURE_ABORTED)

        // Assert
        assertThat(reopenMarker.markedAtMillis()).isNull()
    }

    @Test
    fun `the download of the background is ready to install`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())

        // Act
        testSubject.download(createUpdate(), onlyOnWifi = false)
        testSubject.onDownloadFinished(DOWNLOAD_ID)

        // Assert
        // The update isn't installed without asking, it waits until it is installed.
        assertThat(testSubject.state.value).isEqualTo(UpdateInstallState.ReadyToInstall(TAG))
        assertThat(sessionWrites).isEmpty()
    }

    @Test
    fun `the downloaded update is installed when the user asks for it`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())
        testSubject.download(createUpdate(), onlyOnWifi = false)
        testSubject.onDownloadFinished(DOWNLOAD_ID)

        // Act
        testSubject.installDownloaded()

        // Assert
        assertThat(sessionWrites).hasSize(1)
        assertThat(testSubject.state.value).isEqualTo(UpdateInstallState.WaitingForConfirmation)
    }

    @Test
    fun `the download of the background only runs over wifi when it is asked for`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)

        // Act
        testSubject.download(createUpdate(), onlyOnWifi = true)

        // Assert
        assertThat(downloads.requests).hasSize(1)
        assertThat(downloads.requests.first().onlyOnWifi).isEqualTo(true)
    }

    @Test
    fun `a failed download of the background is reported to the user`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Failed

        // Act
        testSubject.download(createUpdate(), onlyOnWifi = false)
        testScheduler.advanceTimeBy(POLL_INTERVAL_MILLIS)
        testScheduler.runCurrent()

        // Assert
        assertThat(testSubject.state.value).isEqualTo(
            UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD, TAG, NOW_MILLIS),
        )
        assertThat(downloadFailedNotification()).isNotNull()
    }

    @Test
    fun `a downloaded update is ready to install after the app was closed`() = runTest {
        // Arrange
        val firstInstaller = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())
        firstInstaller.download(createUpdate(), onlyOnWifi = false)
        firstInstaller.onDownloadFinished(DOWNLOAD_ID)

        // Act
        val secondInstaller = createInstaller(backgroundScope)
        testScheduler.runCurrent()

        // Assert
        assertThat(secondInstaller.state.value).isEqualTo(UpdateInstallState.ReadyToInstall(TAG))
    }

    @Test
    fun `the installation of a downloaded update fails without a downloaded update`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)

        // Act
        testSubject.installDownloaded()

        // Assert
        assertThat(testSubject.state.value).isEqualTo(
            UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD, null, NOW_MILLIS),
        )
        assertThat(sessionWrites).isEmpty()
    }

    @Test
    fun `a stale pending download is dropped silently when the app starts`() = runTest {
        // Arrange
        downloads.status = SystemDownloadStatus.Failed
        store.save(createPendingDownload().copy(installWhenDone = false))

        // Act
        val testSubject = createInstaller(backgroundScope, isNewerThanInstalled = { false })
        testScheduler.runCurrent()

        // Assert
        assertThat(testSubject.state.value).isEqualTo(UpdateInstallState.Idle)
        assertThat(downloads.removedDownloadIds).containsExactly(DOWNLOAD_ID)
        assertThat(store.load()).isNull()
        assertThat(downloadFailedNotification()).isNull()
    }

    @Test
    fun `a newer pending download is continued when the app starts`() = runTest {
        // Arrange
        downloads.status = SystemDownloadStatus.Running(DOWNLOADED_BYTES, apkSize)
        store.save(createPendingDownload())

        // Act
        val testSubject = createInstaller(backgroundScope, isNewerThanInstalled = { true })
        testScheduler.runCurrent()

        // Assert
        assertThat(testSubject.state.value).isEqualTo(UpdateInstallState.Downloading(DOWNLOADED_BYTES, apkSize))
        assertThat(downloads.removedDownloadIds).isEmpty()
        assertThat(store.load()).isNotNull()
    }

    @Test
    fun `a failed newer pending download is still reported when the app starts`() = runTest {
        // Arrange
        downloads.status = SystemDownloadStatus.Failed
        store.save(createPendingDownload())

        // Act
        val testSubject = createInstaller(backgroundScope, isNewerThanInstalled = { true })
        testScheduler.runCurrent()

        // Assert
        assertThat(testSubject.state.value).isEqualTo(
            UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD, TAG, NOW_MILLIS),
        )
    }

    @Test
    fun `refresh drops a stale pending download silently`() = runTest {
        // Arrange
        var newer = true
        val testSubject = createInstaller(backgroundScope, isNewerThanInstalled = { newer })
        testScheduler.runCurrent()
        downloads.status = SystemDownloadStatus.Failed
        store.save(createPendingDownload().copy(installWhenDone = false))
        // The release became stale in the meantime, e.g. it was installed another way.
        newer = false

        // Act
        testSubject.refresh()

        // Assert
        assertThat(testSubject.state.value).isEqualTo(UpdateInstallState.Idle)
        assertThat(downloads.removedDownloadIds).containsExactly(DOWNLOAD_ID)
        assertThat(store.load()).isNull()
        assertThat(downloadFailedNotification()).isNull()
    }

    @Test
    fun `a stale kept update isn't offered when the app starts`() = runTest {
        // Arrange
        val firstInstaller = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())
        firstInstaller.download(createUpdate(), onlyOnWifi = false)
        firstInstaller.onDownloadFinished(DOWNLOAD_ID)

        // Act
        val secondInstaller = createInstaller(backgroundScope, isNewerThanInstalled = { false })
        testScheduler.runCurrent()

        // Assert
        assertThat(secondInstaller.state.value).isEqualTo(UpdateInstallState.Idle)
        assertThat(KeptApkStore(application).load()).isNull()
        assertThat(keptApkFiles()).isEmpty()
    }

    @Test
    fun `a failed installation names the version and the time`() = runTest {
        // Arrange
        val testSubject = createInstaller(backgroundScope)
        testSubject.install(createUpdate())

        // Act
        testSubject.onInstallStatus(PackageInstaller.STATUS_FAILURE_ABORTED)

        // Assert
        val state = testSubject.state.value as UpdateInstallState.Failed
        assertThat(state.tag).isEqualTo(TAG)
        assertThat(state.failedAtMillis).isEqualTo(NOW_MILLIS)
    }

    private fun createInstaller(
        coroutineScope: CoroutineScope,
        writeSession: (File) -> Int? = { file ->
            sessionWrites.add(file)
            SESSION_ID
        },
        isNewerThanInstalled: (String) -> Boolean = { true },
    ): PackageInstallerAppUpdateInstaller {
        return PackageInstallerAppUpdateInstaller(
            context = application,
            systemDownloads = downloads,
            pendingDownloadStore = store,
            apkVerifier = ApkVerifier(),
            logger = TestLogger(),
            notifier = notifier,
            coroutineScope = coroutineScope,
            pollIntervalMillis = POLL_INTERVAL_MILLIS,
            writeSession = writeSession,
            sessionRegistrar = registrar,
            isNewerThanInstalled = isNewerThanInstalled,
            clock = { NOW_MILLIS },
            reopenMarker = reopenMarker,
        )
    }

    private fun installingNotification(): Notification? {
        return shadowOf(notificationManager()).getNotification(
            UpdateNotifier.INSTALLING_NOTIFICATION_TAG,
            UpdateNotifier.INSTALLING_NOTIFICATION_ID,
        )
    }

    private fun downloadFailedNotification(): Notification? {
        return shadowOf(notificationManager()).getNotification(
            UpdateNotifier.FAILED_NOTIFICATION_TAG,
            UpdateNotifier.FAILED_NOTIFICATION_ID,
        )
    }

    private fun notificationManager(): NotificationManager {
        return application.getSystemService(NotificationManager::class.java)
    }

    private suspend fun awaitState(
        testSubject: PackageInstallerAppUpdateInstaller,
        predicate: (UpdateInstallState) -> Boolean,
    ): UpdateInstallState {
        // The install session is written on another thread, so the timeout has to be a real one.
        return withContext(Dispatchers.IO) {
            withTimeout(TIMEOUT_MILLIS) {
                testSubject.state.first(predicate)
            }
        }
    }

    private suspend fun awaitLatch(latch: CountDownLatch): Boolean {
        // The latch is awaited on another thread, so that the test isn't blocked.
        return withContext(Dispatchers.IO) {
            latch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        }
    }

    private suspend fun awaitDownloadStarted(downloads: FakeSystemDownloads) {
        // The download is started in another thread, so the timeout has to be a real one.
        withContext(Dispatchers.IO) {
            withTimeout(TIMEOUT_MILLIS) {
                while (downloads.requests.isEmpty()) {
                    delay(WAIT_INTERVAL_MILLIS)
                }
            }
        }
    }

    private fun createPendingDownload(downloadId: Long = DOWNLOAD_ID): PendingDownload {
        return PendingDownload(
            downloadId = downloadId,
            tag = TAG,
            sha256 = sha256Of(APK_CONTENT),
            sizeBytes = apkSize,
            title = "Test release",
            installWhenDone = true,
        )
    }

    private fun createUpdate(sha256: String? = sha256Of(APK_CONTENT)): AppUpdate {
        return AppUpdate(
            tag = TAG,
            title = "Test release",
            releaseNotes = "Release notes",
            apkUrl = APK_URL,
            apkSizeBytes = apkSize,
            apkSha256 = sha256,
        )
    }

    private fun createDownloadedFile(content: String = APK_CONTENT): File {
        return File(tempDir, "downloaded.apk").apply { writeText(content) }
    }

    private fun keptApkFiles(): List<File> {
        return File(application.cacheDir, "app-updates").listFiles()?.toList().orEmpty()
    }

    private fun sha256Of(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
        return digest.joinToString(separator = "") { byte ->
            val value = byte.toInt() and BYTE_MASK
            HEX_DIGITS[value shr NIBBLE_SHIFT].toString() + HEX_DIGITS[value and NIBBLE_MASK]
        }
    }

    private companion object {
        const val HEX_DIGITS = "0123456789abcdef"
        const val BYTE_MASK = 0xFF
        const val NIBBLE_MASK = 0x0F
        const val NIBBLE_SHIFT = 4
    }
}

/**
 * A fake of the downloads of the system, for the tests of the updater.
 */
internal class FakeSystemDownloads : SystemDownloads {

    val requests = mutableListOf<SystemDownloadRequest>()
    val removedDownloadIds = mutableListOf<Long>()

    var nextDownloadId: Long? = DOWNLOAD_ID
    var status: SystemDownloadStatus = SystemDownloadStatus.Running(0L, 0L)

    /** The number of queries that fail before the status of the download is read again. */
    var failingQueries = 0

    /** The name of the thread the download was started on. */
    var lastEnqueueThread: String? = null

    /** Called when a download is started, to block the start of the download. */
    var onEnqueue: (() -> Unit)? = null

    /** Called when a download is removed, to observe the state of the app at that moment. */
    var onRemove: ((Long) -> Unit)? = null

    override fun enqueue(request: SystemDownloadRequest): Long? {
        lastEnqueueThread = Thread.currentThread().name
        onEnqueue?.invoke()
        requests.add(request)
        return nextDownloadId
    }

    override fun query(downloadId: Long): SystemDownloadStatus {
        if (failingQueries > 0) {
            failingQueries--
            throw IllegalStateException("The status of the download couldn't be read.")
        }
        return status
    }

    override fun remove(downloadId: Long) {
        onRemove?.invoke(downloadId)
        removedDownloadIds.add(downloadId)
    }
}
