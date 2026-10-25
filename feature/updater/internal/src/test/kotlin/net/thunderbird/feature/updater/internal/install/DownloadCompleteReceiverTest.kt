package net.thunderbird.feature.updater.internal.install

import android.app.DownloadManager
import android.content.Intent
import assertk.assertThat
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.feature.updater.api.UpdateInstallFailure
import net.thunderbird.feature.updater.api.UpdateInstallState
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val DOWNLOAD_ID = 42L
private const val TAG = "test-tag"
private const val TITLE = "Test release"
private const val APK_CONTENT = "apk content"
private const val TIMEOUT_MILLIS = 30_000L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadCompleteReceiverTest {

    private lateinit var downloads: FakeSystemDownloads
    private lateinit var store: PendingDownloadStore
    private lateinit var sessionWrites: MutableList<File>

    @Before
    fun setUp() {
        downloads = FakeSystemDownloads()
        store = PendingDownloadStore(RuntimeEnvironment.getApplication())
        store.clear()
        sessionWrites = mutableListOf()
    }

    @Test
    fun `the id of the finished download is read from the intent`() {
        // Arrange
        val intent = Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            .putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, DOWNLOAD_ID)
        val testSubject = DownloadCompleteReceiver()

        // Act
        val downloadId = testSubject.downloadIdOf(intent)

        // Assert
        assertThat(downloadId).isEqualTo(DOWNLOAD_ID)
    }

    @Test
    fun `an intent without a download id is ignored`() {
        // Arrange
        val intent = Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        val testSubject = DownloadCompleteReceiver()

        // Act
        val downloadId = testSubject.downloadIdOf(intent)

        // Assert
        assertThat(downloadId).isNull()
    }

    @Test
    fun `the finished download is handed over to the installer`() = runTest {
        // Arrange
        val installer = createInstaller(backgroundScope)
        downloads.status = SystemDownloadStatus.Failed
        store.save(createPendingDownload())
        val testSubject = DownloadCompleteReceiver()

        // Act
        testSubject.handleDownloadFinished(DOWNLOAD_ID, installer)

        // Assert
        assertThat(installer.state.value).isEqualTo(
            UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD),
        )
    }

    @Test
    fun `the download is ignored when there is no installer`() = runTest {
        // Arrange
        val testSubject = DownloadCompleteReceiver()

        // Act
        testSubject.handleDownloadFinished(DOWNLOAD_ID, null)

        // Assert
        assertThat(downloads.requests).isEmpty()
    }

    @Test
    fun `the receiver waits for the download that is handled already`() = runTest {
        // Arrange
        val writeStarted = CountDownLatch(1)
        val allowWrite = CountDownLatch(1)
        val installer = createInstaller(
            backgroundScope,
            writeSession = { file ->
                sessionWrites.add(file)
                writeStarted.countDown()
                allowWrite.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                true
            },
        )
        downloads.status = SystemDownloadStatus.Successful(createDownloadedFile())
        store.save(createPendingDownload())
        val testSubject = DownloadCompleteReceiver()

        // Act
        // The app was started by the broadcast, so the installer handles the download already.
        val handling = backgroundScope.launch { installer.onDownloadFinished(DOWNLOAD_ID) }
        assertThat(awaitLatch(writeStarted)).isTrue()
        val received = backgroundScope.launch { testSubject.handleDownloadFinished(DOWNLOAD_ID, installer) }
        testScheduler.runCurrent()

        // Assert
        // The receiver waits for the installation that is in progress.
        assertThat(received.isCompleted).isFalse()
        allowWrite.countDown()
        awaitCompletion(received)
        awaitCompletion(handling)
        assertThat(received.isCompleted).isTrue()
        assertThat(handling.isCompleted).isTrue()
        assertThat(sessionWrites).hasSize(1)
    }

    private suspend fun awaitCompletion(job: Job) {
        // The jobs are finished on another thread, so the timeout has to be a real one.
        withContext(Dispatchers.IO) {
            withTimeout(TIMEOUT_MILLIS) {
                job.join()
            }
        }
    }

    private suspend fun awaitLatch(latch: CountDownLatch): Boolean {
        // The latch is awaited on another thread, so that the test isn't blocked.
        return withContext(Dispatchers.IO) {
            latch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        }
    }

    private fun createInstaller(
        coroutineScope: CoroutineScope,
        writeSession: (File) -> Boolean = { file ->
            sessionWrites.add(file)
            true
        },
    ): PackageInstallerAppUpdateInstaller {
        return PackageInstallerAppUpdateInstaller(
            context = RuntimeEnvironment.getApplication(),
            systemDownloads = downloads,
            pendingDownloadStore = store,
            apkVerifier = ApkVerifier(),
            logger = TestLogger(),
            coroutineScope = coroutineScope,
            writeSession = writeSession,
        )
    }

    private fun createDownloadedFile(): File {
        val directory = Files.createTempDirectory("download-complete-receiver-test").toFile()
        return File(directory, "downloaded.apk").apply { writeText(APK_CONTENT) }
    }

    private fun createPendingDownload(): PendingDownload {
        return PendingDownload(
            downloadId = DOWNLOAD_ID,
            tag = TAG,
            sha256 = sha256Of(APK_CONTENT),
            sizeBytes = APK_CONTENT.length.toLong(),
            title = TITLE,
        )
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
