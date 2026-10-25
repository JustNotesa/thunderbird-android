package net.thunderbird.feature.updater.internal.check

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.AppUpdateConfig
import net.thunderbird.feature.updater.api.UpdateCheckFailure
import net.thunderbird.feature.updater.api.UpdateCheckResult
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before

private const val REPOSITORY = "JustNotesa/thunderbird-android"
private const val CURRENT_TAG = "mein-thunderbird-2026-10-25"
private const val NEWER_TAG = "mein-thunderbird-2026-10-25-3"
private const val NEXT_DAY_TAG = "mein-thunderbird-2026-10-26"
private const val FOREIGN_TAG = "combined-preview-2026-10-26"
private const val APK_NAME = "mein-thunderbird.apk"
private const val APK_URL = "https://github.com/JustNotesa/thunderbird-android/releases/download/v1/app.apk"
private const val APK_SIZE = 4711L
private const val ANDROID_PACKAGE_MIME_TYPE = "application/vnd.android.package-archive"
private const val SHA256_UPPER = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"
private const val SHA256_LOWER = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

class DefaultAppUpdateCheckerTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `should be disabled for build without release tag`() = runTest {
        // Arrange
        val testSubject = createChecker(currentReleaseTag = "")

        // Act
        val result = testSubject.check()

        // Assert
        assertThat(testSubject.isEnabled).isFalse()
        assertThat(testSubject.currentReleaseTag).isEqualTo("")
        assertThat(result).isEqualTo(UpdateCheckResult.Disabled)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `should be disabled for build with debug release tag`() = runTest {
        // Arrange
        val testSubject = createChecker(currentReleaseTag = "debug-build")

        // Act
        val result = testSubject.check()

        // Assert
        assertThat(testSubject.isEnabled).isFalse()
        assertThat(testSubject.currentReleaseTag).isEqualTo("")
        assertThat(result).isEqualTo(UpdateCheckResult.Disabled)
    }

    @Test
    fun `should be enabled for release build`() = runTest {
        // Arrange
        val testSubject = createChecker(currentReleaseTag = CURRENT_TAG)

        // Assert
        assertThat(testSubject.isEnabled).isTrue()
        assertThat(testSubject.currentReleaseTag).isEqualTo(CURRENT_TAG)
    }

    @Test
    fun `should report up to date when the latest release is not newer than the installed one`() = runTest {
        // Arrange
        server.enqueue(MockResponse().setBody(releaseJson(tag = CURRENT_TAG)))
        server.enqueue(MockResponse().setBody(releaseJson(tag = CURRENT_TAG)))
        val sameTagChecker = createChecker(currentReleaseTag = CURRENT_TAG)
        val newerBuildChecker = createChecker(currentReleaseTag = NEWER_TAG)

        // Act
        val sameTagResult = sameTagChecker.check()
        val olderReleaseResult = newerBuildChecker.check()

        // Assert
        assertThat(sameTagResult, name = "same release tag").isEqualTo(UpdateCheckResult.UpToDate)
        assertThat(olderReleaseResult, name = "older release on server").isEqualTo(UpdateCheckResult.UpToDate)
    }

    @Test
    fun `should report available update when the latest release is newer`() = runTest {
        // Arrange
        val assets = listOf(apkAsset(digest = "sha256:$SHA256_UPPER"))
        server.enqueue(MockResponse().setBody(releaseJson(tag = NEWER_TAG, assets = assets)))
        val testSubject = createChecker(currentReleaseTag = CURRENT_TAG)

        // Act
        val result = testSubject.check()

        // Assert
        assertThat(result).isEqualTo(
            UpdateCheckResult.Available(
                AppUpdate(
                    tag = NEWER_TAG,
                    title = "Mein Thunderbird $NEWER_TAG",
                    releaseNotes = "Release notes for $NEWER_TAG",
                    apkUrl = APK_URL,
                    apkSizeBytes = APK_SIZE,
                    apkSha256 = SHA256_LOWER,
                ),
            ),
        )
    }

    @Test
    fun `should report available update when the latest release is of the next day`() = runTest {
        // Arrange
        server.enqueue(MockResponse().setBody(releaseJson(tag = NEXT_DAY_TAG)))
        val testSubject = createChecker(currentReleaseTag = CURRENT_TAG)

        // Act
        val result = testSubject.check()

        // Assert
        assertThat(result)
            .isInstanceOf<UpdateCheckResult.Available>()
            .transform { it.update.tag }
            .isEqualTo(NEXT_DAY_TAG)
    }

    @Test
    fun `should ignore prerelease and draft releases`() = runTest {
        // Arrange
        server.enqueue(MockResponse().setBody(releaseJson(tag = NEXT_DAY_TAG, prerelease = true)))
        server.enqueue(MockResponse().setBody(releaseJson(tag = NEXT_DAY_TAG, draft = true)))
        val testSubject = createChecker(currentReleaseTag = CURRENT_TAG)

        // Act
        val prereleaseResult = testSubject.check()
        val draftResult = testSubject.check()

        // Assert
        assertThat(prereleaseResult, name = "prerelease").isEqualTo(UpdateCheckResult.UpToDate)
        assertThat(draftResult, name = "draft").isEqualTo(UpdateCheckResult.UpToDate)
    }

    @Test
    fun `should ignore release with foreign tag`() = runTest {
        // Arrange
        server.enqueue(MockResponse().setBody(releaseJson(tag = FOREIGN_TAG)))
        val testSubject = createChecker(currentReleaseTag = CURRENT_TAG)

        // Act
        val result = testSubject.check()

        // Assert
        assertThat(result).isEqualTo(UpdateCheckResult.UpToDate)
    }

    @Test
    fun `should fail when the latest release has no usable apk`() = runTest {
        // Arrange
        val textAsset = apkAsset(
            name = "release-notes.txt",
            url = "https://example.com/release-notes.txt",
            contentType = "text/plain",
        )
        val insecureAsset = apkAsset(url = "http://example.com/mein-thunderbird.apk")
        server.enqueue(MockResponse().setBody(releaseJson(tag = NEWER_TAG, assets = listOf(textAsset))))
        server.enqueue(MockResponse().setBody(releaseJson(tag = NEWER_TAG, assets = listOf(insecureAsset))))
        val testSubject = createChecker(currentReleaseTag = CURRENT_TAG)

        // Act
        val missingApkResult = testSubject.check()
        val insecureApkResult = testSubject.check()

        // Assert
        assertThat(missingApkResult, name = "no apk asset").isEqualTo(
            UpdateCheckResult.Failed(UpdateCheckFailure.NO_APK),
        )
        assertThat(insecureApkResult, name = "insecure apk url").isEqualTo(
            UpdateCheckResult.Failed(UpdateCheckFailure.NO_APK),
        )
    }

    @Test
    fun `should ignore unusable apk digest`() = runTest {
        // Arrange
        val digests = listOf("md5:abc", "sha256:short", "sha256:${"z".repeat(64)}", null)
        digests.forEach { digest ->
            val assets = listOf(apkAsset(digest = digest))
            server.enqueue(MockResponse().setBody(releaseJson(tag = NEWER_TAG, assets = assets)))
        }
        val testSubject = createChecker(currentReleaseTag = CURRENT_TAG)

        // Act & Assert
        digests.forEach { digest ->
            val result = testSubject.check()

            assertThat(result, name = "digest $digest")
                .isInstanceOf<UpdateCheckResult.Available>()
                .transform { it.update.apkSha256 }
                .isNull()
        }
    }

    @Test
    fun `should report failure when the check fails`() = runTest {
        // Arrange
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody("this is not json"))
        val testSubject = createChecker(currentReleaseTag = CURRENT_TAG)

        // Act
        val serverErrorResult = testSubject.check()
        val invalidResponseResult = testSubject.check()

        // Assert
        assertThat(serverErrorResult, name = "http error").isEqualTo(
            UpdateCheckResult.Failed(UpdateCheckFailure.SERVER),
        )
        assertThat(invalidResponseResult, name = "malformed json").isEqualTo(
            UpdateCheckResult.Failed(UpdateCheckFailure.INVALID_RESPONSE),
        )

        server.shutdown()

        val networkErrorResult = testSubject.check()

        assertThat(networkErrorResult, name = "server unreachable").isEqualTo(
            UpdateCheckResult.Failed(UpdateCheckFailure.NETWORK),
        )
    }

    private fun createChecker(currentReleaseTag: String): DefaultAppUpdateChecker {
        val client = GithubReleaseClient(OkHttpClient(), server.url("/").toString().trimEnd('/'))
        val config = AppUpdateConfig(repository = REPOSITORY, currentReleaseTag = currentReleaseTag)
        return DefaultAppUpdateChecker(config, client, TestLogger())
    }
}

private fun releaseJson(
    tag: String,
    prerelease: Boolean = false,
    draft: Boolean = false,
    assets: List<String> = listOf(apkAsset()),
): String {
    return listOf(
        "\"tag_name\": \"$tag\"",
        "\"name\": \"Mein Thunderbird $tag\"",
        "\"body\": \"Release notes for $tag\"",
        "\"draft\": $draft",
        "\"prerelease\": $prerelease",
        "\"assets\": [${assets.joinToString(", ")}]",
    ).joinToString(", ", prefix = "{", postfix = "}")
}

private fun apkAsset(
    name: String = APK_NAME,
    url: String = APK_URL,
    size: Long = APK_SIZE,
    digest: String? = null,
    contentType: String? = ANDROID_PACKAGE_MIME_TYPE,
): String {
    val contentTypeField = contentType?.let { "\"content_type\": \"$it\"" }.orEmpty()
    val digestField = digest?.let { "\"digest\": \"$it\"" }.orEmpty()

    return listOf(
        "\"name\": \"$name\"",
        contentTypeField,
        digestField,
        "\"size\": $size",
        "\"browser_download_url\": \"$url\"",
    ).filter { it.isNotEmpty() }.joinToString(", ", prefix = "{", postfix = "}")
}
