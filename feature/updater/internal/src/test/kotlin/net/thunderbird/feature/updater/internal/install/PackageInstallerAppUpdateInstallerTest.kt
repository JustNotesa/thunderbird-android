package net.thunderbird.feature.updater.internal.install

import android.app.Application
import android.content.pm.PackageInstaller
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotInstanceOf
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.UpdateInstallFailure
import net.thunderbird.feature.updater.api.UpdateInstallState
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
class PackageInstallerAppUpdateInstallerTest {
    private val application: Application = RuntimeEnvironment.getApplication()

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

    @Before
    fun setUp() {
        downloadDir = Files.createTempDirectory("apk-installer-test").toFile()
        server.start()
        shadowOf(application.packageManager).setCanRequestPackageInstalls(true)
    }

    @After
    fun tearDown() {
        server.shutdown()
        downloadDir.deleteRecursively()
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
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `install with a failing download reports a download failure`() = runTest {
        // Arrange
        server.enqueue(MockResponse().setResponseCode(500))
        val testSubject = createInstaller(backgroundScope)

        // Act
        testSubject.install(createUpdate())

        // Assert
        assertThat(testSubject.state.value).isEqualTo(
            UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD),
        )
    }

    @Test
    fun `install with a mismatching checksum reports a checksum failure`() = runTest {
        // Arrange
        server.enqueue(MockResponse().setBody(APK_CONTENT))
        val testSubject = createInstaller(backgroundScope)

        // Act
        testSubject.install(createUpdate(sha256 = "0".repeat(SHA_256_LENGTH)))

        // Assert
        assertThat(testSubject.state.value).isEqualTo(
            UpdateInstallState.Failed(UpdateInstallFailure.CHECKSUM),
        )
    }

    @Test
    fun `resetState sets the state back to idle`() = runTest {
        // Arrange
        shadowOf(application.packageManager).setCanRequestPackageInstalls(false)
        val testSubject = createInstaller(backgroundScope)
        testSubject.install(createUpdate())

        // Act
        testSubject.resetState()

        // Assert
        assertThat(testSubject.state.value).isEqualTo(UpdateInstallState.Idle)
    }

    @Test
    fun `cancelling the caller does not cancel the download`() = runTest {
        // Arrange
        server.enqueue(MockResponse().setBody(APK_CONTENT).setBodyDelay(BODY_DELAY_SECONDS, TimeUnit.SECONDS))
        val testSubject = createInstaller(backgroundScope)
        val caller = launch { testSubject.install(createUpdate()) }
        testScheduler.runCurrent()

        // Act
        caller.cancelAndJoin()

        // Assert
        // The download runs in the app wide scope, so it continues after the caller was cancelled.
        assertThat(testSubject.state.value).isInstanceOf<UpdateInstallState.Downloading>()
        val finalState = awaitFinalState(testSubject)
        assertThat(finalState).isNotInstanceOf<UpdateInstallState.Downloading>()
        assertThat(finalState).isNotInstanceOf<UpdateInstallState.Verifying>()
    }

    @Test
    fun `cancelling the installation does not leave the state in downloading`() = runTest {
        // Arrange
        server.enqueue(MockResponse().setBody(APK_CONTENT).setBodyDelay(BODY_DELAY_SECONDS, TimeUnit.SECONDS))
        val installScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val testSubject = createInstaller(installScope)
        launch { testSubject.install(createUpdate()) }
        awaitState(testSubject) { state -> state is UpdateInstallState.Downloading }

        // Act
        installScope.cancel()

        // Assert
        assertThat(awaitFinalState(testSubject)).isEqualTo(
            UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD),
        )
    }

    @Test
    fun `a second install joins the running download`() = runTest {
        // Arrange
        server.enqueue(MockResponse().setBody(APK_CONTENT).setBodyDelay(BODY_DELAY_SECONDS, TimeUnit.SECONDS))
        val testSubject = createInstaller(backgroundScope)
        launch { testSubject.install(createUpdate()) }
        testScheduler.runCurrent()

        // Act
        testSubject.install(createUpdate())

        // Assert
        // The APK is only downloaded once, the second installation waits for the running one.
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(testSubject.state.value).isNotInstanceOf<UpdateInstallState.Downloading>()
    }

    @Test
    fun `successful installation keeps the current state`() = runTest {
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

    private fun createInstaller(coroutineScope: CoroutineScope): PackageInstallerAppUpdateInstaller {
        return PackageInstallerAppUpdateInstaller(
            context = application,
            downloader = ApkDownloader(httpClient, downloadDir),
            logger = TestLogger(),
            coroutineScope = coroutineScope,
        )
    }

    private suspend fun awaitFinalState(
        testSubject: PackageInstallerAppUpdateInstaller,
    ): UpdateInstallState {
        return awaitState(testSubject) { state ->
            state !is UpdateInstallState.Downloading && state !is UpdateInstallState.Verifying
        }
    }

    private suspend fun awaitState(
        testSubject: PackageInstallerAppUpdateInstaller,
        predicate: (UpdateInstallState) -> Boolean,
    ): UpdateInstallState {
        // The download runs on another thread, so the timeout has to be a real one.
        return withContext(Dispatchers.IO) {
            withTimeout(TIMEOUT_MILLIS) {
                testSubject.state.first(predicate)
            }
        }
    }

    private fun createUpdate(
        apkUrl: String = "https://example.test/app.apk",
        sha256: String? = sha256Of(APK_CONTENT),
    ): AppUpdate {
        return AppUpdate(
            tag = TAG,
            title = "Test release",
            releaseNotes = "Release notes",
            apkUrl = apkUrl,
            apkSizeBytes = 0L,
            apkSha256 = sha256,
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
        const val TAG = "test-tag"
        const val SHA_256_LENGTH = 64
        const val APK_CONTENT = "apk content"
        const val BODY_DELAY_SECONDS = 2L
        const val TIMEOUT_MILLIS = 30_000L
        const val HEX_DIGITS = "0123456789abcdef"
        const val BYTE_MASK = 0xFF
        const val NIBBLE_MASK = 0x0F
        const val NIBBLE_SHIFT = 4
    }
}
