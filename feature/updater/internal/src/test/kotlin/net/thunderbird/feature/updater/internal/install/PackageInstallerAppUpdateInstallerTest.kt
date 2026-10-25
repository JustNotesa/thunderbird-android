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
import net.thunderbird.feature.updater.api.UpdateInstallFailure
import net.thunderbird.feature.updater.api.UpdateInstallState
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
private const val INSTALL_PROGRESS = 0.5f
private const val TIMEOUT_MILLIS = 30_000L
private const val WAIT_INTERVAL_MILLIS = 10L

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
            UpdateInstallState.Failed(UpdateInstallFailure.PERMISSION_MISSING),
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
            UpdateInstallState.Failed(UpdateInstallFailure.CHECKSUM),
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
        assertThat(state).isEqualTo(UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD))
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
            UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD),
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
            UpdateInstallState.Failed(UpdateInstallFailure.CHECKSUM),
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
            UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD),
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
            UpdateInstallState.Failed(UpdateInstallFailure.CANCELLED),
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
            UpdateInstallState.Failed(UpdateInstallFailure.REJECTED),
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
        notifier.notifyInstalling(INSTALL_PROGRESS)

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

    private fun createInstaller(
        coroutineScope: CoroutineScope,
        writeSession: (File) -> Int? = { file ->
            sessionWrites.add(file)
            SESSION_ID
        },
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
        )
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
        return status
    }

    override fun remove(downloadId: Long) {
        onRemove?.invoke(downloadId)
        removedDownloadIds.add(downloadId)
    }
}
