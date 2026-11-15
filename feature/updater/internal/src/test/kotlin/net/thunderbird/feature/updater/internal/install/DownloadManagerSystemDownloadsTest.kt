package net.thunderbird.feature.updater.internal.install

import android.app.DownloadManager
import android.content.Context
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import net.thunderbird.feature.updater.api.DownloadWaiting
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

private const val TOTAL_SIZE = 100L
private const val BYTES_SO_FAR = 42L

@RunWith(RobolectricTestRunner::class)
class DownloadManagerSystemDownloadsTest {
    private val context = RuntimeEnvironment.getApplication()

    private val testSubject by lazy { DownloadManagerSystemDownloads(context) }

    @Test
    fun `download over http is not started`() {
        // Arrange
        val request = createRequest(url = "http://example.test/app.apk")

        // Act
        val downloadId = testSubject.enqueue(request)

        // Assert
        assertThat(downloadId).isNull()
    }

    @Test
    fun `download over https is started`() {
        // Arrange
        val request = createRequest(url = "https://example.test/app.apk")

        // Act
        val downloadId = testSubject.enqueue(request)

        // Assert
        assertThat(downloadId).isNotNull()
    }

    @Test
    fun `a paused download is reported as waiting`() {
        // Arrange
        val downloadId = testSubject.enqueue(createRequest(url = "https://example.test/app.apk"))
        val request = shadowOf(downloadManager()).getRequest(downloadId!!)
        shadowOf(request).setStatus(DownloadManager.STATUS_PAUSED)
        shadowOf(request).setTotalSize(TOTAL_SIZE)
        shadowOf(request).setBytesSoFar(BYTES_SO_FAR)

        // Act
        val status = testSubject.query(downloadId)

        // Assert
        assertThat(status).isEqualTo(
            SystemDownloadStatus.Running(BYTES_SO_FAR, TOTAL_SIZE, DownloadWaiting.PAUSED),
        )
    }

    @Test
    fun `a download that waits for a network connection is reported`() {
        // Act
        val waiting = DownloadManagerSystemDownloads.waitingOf(DownloadManager.PAUSED_WAITING_FOR_NETWORK)

        // Assert
        assertThat(waiting).isEqualTo(DownloadWaiting.NETWORK)
    }

    @Test
    fun `a download that waits for a Wi-Fi connection is reported`() {
        // Act
        val waiting = DownloadManagerSystemDownloads.waitingOf(DownloadManager.PAUSED_QUEUED_FOR_WIFI)

        // Assert
        assertThat(waiting).isEqualTo(DownloadWaiting.WIFI)
    }

    @Test
    fun `a download that waits to be retried is reported`() {
        // Act
        val waiting = DownloadManagerSystemDownloads.waitingOf(DownloadManager.PAUSED_WAITING_TO_RETRY)

        // Assert
        assertThat(waiting).isEqualTo(DownloadWaiting.RETRY)
    }

    @Test
    fun `a download that is paused for another reason is reported`() {
        // Act
        val waiting = DownloadManagerSystemDownloads.waitingOf(DownloadManager.PAUSED_UNKNOWN)

        // Assert
        assertThat(waiting).isEqualTo(DownloadWaiting.PAUSED)
    }

    private fun downloadManager(): DownloadManager {
        return context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    private fun createRequest(url: String): SystemDownloadRequest {
        return SystemDownloadRequest(
            url = url,
            fileName = FILE_NAME,
            title = TITLE,
            description = DESCRIPTION,
        )
    }

    private companion object {
        const val FILE_NAME = "app.apk"
        const val TITLE = "Mein Thunderbird 2026-10-25"
        const val DESCRIPTION = "The update of Mein Thunderbird"
    }
}
