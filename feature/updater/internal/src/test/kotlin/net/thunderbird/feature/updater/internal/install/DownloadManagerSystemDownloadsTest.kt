package net.thunderbird.feature.updater.internal.install

import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

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
