package net.thunderbird.feature.updater.internal.install

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.updater.api.AppUpdate
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class ApkDownloaderTest {
    private val server = MockWebServer()

    // The downloader only accepts HTTPS URLs. This interceptor sends those requests to the mock server and puts
    // the requested HTTPS URL back onto the response, so that the downloader sees the URL it asked for.
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val serverUrl = server.url(request.url.encodedPath)
            val response = chain.proceed(request.newBuilder().url(serverUrl).build())
            if (response.request.url == serverUrl) {
                response.newBuilder().request(request).build()
            } else {
                // The request was redirected, the downloader has to see the URL of the redirect target.
                response
            }
        }
        .build()

    private lateinit var downloadDir: File
    private lateinit var testSubject: ApkDownloader

    @Before
    fun setUp() {
        downloadDir = Files.createTempDirectory("apk-downloader-test").toFile()
        server.start()
        testSubject = ApkDownloader(httpClient, downloadDir)
    }

    @After
    fun tearDown() {
        server.shutdown()
        downloadDir.deleteRecursively()
    }

    @Test
    fun `download with matching checksum stores the apk`() = runTest {
        // Arrange
        val body = "apk content"
        server.enqueue(MockResponse().setBody(body))
        val update = createUpdate(sha256 = sha256(body))

        // Act
        val result = testSubject.download(update) { _, _ -> }

        // Assert
        val expectedFile = File(downloadDir, "$TAG$APK_EXTENSION")
        assertThat(result).isEqualTo(DownloadResult.Success(expectedFile))
        assertThat(expectedFile.readText()).isEqualTo(body)
    }

    @Test
    fun `download with mismatching checksum fails and leaves no files behind`() = runTest {
        // Arrange
        server.enqueue(MockResponse().setBody("apk content"))
        val update = createUpdate(sha256 = "0".repeat(SHA_256_LENGTH))

        // Act
        val result = testSubject.download(update) { _, _ -> }

        // Assert
        assertThat(result).isEqualTo(DownloadResult.ChecksumMismatch)
        assertThat(filesInDownloadDir()).isEmpty()
    }

    @Test
    fun `download without checksum fails without contacting the server`() = runTest {
        // Arrange
        server.enqueue(MockResponse().setBody("apk content"))
        val update = createUpdate(sha256 = null)

        // Act
        val result = testSubject.download(update) { _, _ -> }

        // Assert
        assertThat(result).isEqualTo(DownloadResult.ChecksumMismatch)
        assertThat(server.requestCount).isEqualTo(0)
        assertThat(filesInDownloadDir()).isEmpty()
    }

    @Test
    fun `download with unexpected size fails`() = runTest {
        // Arrange
        val body = "apk content"
        server.enqueue(MockResponse().setBody(body))
        val update = createUpdate(sha256 = sha256(body), apkSizeBytes = body.length + 1L)

        // Act
        val result = testSubject.download(update) { _, _ -> }

        // Assert
        assertThat(result).isEqualTo(DownloadResult.Failed)
        assertThat(filesInDownloadDir()).isEmpty()
    }

    @Test
    fun `download with server error fails`() = runTest {
        // Arrange
        server.enqueue(MockResponse().setResponseCode(500))
        val update = createUpdate(sha256 = sha256("apk content"))

        // Act
        val result = testSubject.download(update) { _, _ -> }

        // Assert
        assertThat(result).isEqualTo(DownloadResult.Failed)
        assertThat(filesInDownloadDir()).isEmpty()
    }

    @Test
    fun `download with non-https url fails without contacting the server`() = runTest {
        // Arrange
        val update = createUpdate(apkUrl = "http://example.test/app.apk")

        // Act
        val result = testSubject.download(update) { _, _ -> }

        // Assert
        assertThat(result).isEqualTo(DownloadResult.Failed)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `download that was redirected to http fails`() = runTest {
        // Arrange
        server.enqueue(
            MockResponse()
                .setResponseCode(HTTP_MOVED_TEMP)
                .setHeader("Location", server.url("/redirected.apk").toString()),
        )
        server.enqueue(MockResponse().setBody("apk content"))
        val update = createUpdate(sha256 = sha256("apk content"))

        // Act
        val result = testSubject.download(update) { _, _ -> }

        // Assert
        assertThat(result).isEqualTo(DownloadResult.Failed)
        assertThat(filesInDownloadDir()).isEmpty()
    }

    @Test
    fun `download deletes previously downloaded files`() = runTest {
        // Arrange
        val oldApk = File(downloadDir, "old.apk").apply { writeText("old apk") }
        val oldPart = File(downloadDir, "old.part").apply { writeText("old part") }
        val body = "apk content"
        server.enqueue(MockResponse().setBody(body))
        val update = createUpdate(sha256 = sha256(body))

        // Act
        val result = testSubject.download(update) { _, _ -> }

        // Assert
        assertThat(result).isInstanceOf<DownloadResult.Success>()
        assertThat(oldApk.exists()).isFalse()
        assertThat(oldPart.exists()).isFalse()
    }

    @Test
    fun `download reports progress`() = runTest {
        // Arrange
        val body = "a".repeat(BODY_SIZE)
        server.enqueue(MockResponse().setBody(body))
        val update = createUpdate(sha256 = sha256(body))
        val progress = mutableListOf<Pair<Long, Long>>()

        // Act
        testSubject.download(update) { downloaded, total -> progress.add(downloaded to total) }

        // Assert
        assertThat(progress).isNotEmpty()
        assertThat(progress.first().first).isEqualTo(0L)
        assertThat(progress.last().first).isEqualTo(body.length.toLong())
        assertThat(progress.last().second).isEqualTo(body.length.toLong())
        for (index in 1 until progress.size) {
            assertThat(progress[index].first).isGreaterThanOrEqualTo(progress[index - 1].first)
        }
    }

    private fun createUpdate(
        apkUrl: String = "https://example.test/app.apk",
        apkSizeBytes: Long = 0L,
        sha256: String? = null,
    ): AppUpdate {
        return AppUpdate(
            tag = TAG,
            title = "Test release",
            releaseNotes = "Release notes",
            apkUrl = apkUrl,
            apkSizeBytes = apkSizeBytes,
            apkSha256 = sha256,
        )
    }

    private fun filesInDownloadDir(): List<String> {
        return downloadDir.listFiles().orEmpty().map { file -> file.name }
    }

    private fun sha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
        return digest.joinToString(separator = "") { byte ->
            val value = byte.toInt() and BYTE_MASK
            HEX_DIGITS[value shr NIBBLE_SHIFT].toString() + HEX_DIGITS[value and NIBBLE_MASK]
        }
    }

    private companion object {
        const val TAG = "test-tag"
        const val APK_EXTENSION = ".apk"
        const val BODY_SIZE = 1024
        const val SHA_256_LENGTH = 64
        const val HEX_DIGITS = "0123456789abcdef"
        const val BYTE_MASK = 0xFF
        const val NIBBLE_MASK = 0x0F
        const val NIBBLE_SHIFT = 4
        const val HTTP_MOVED_TEMP = 302
    }
}
