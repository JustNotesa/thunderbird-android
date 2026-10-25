package net.thunderbird.feature.updater.internal.check

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.prop
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

private const val REPOSITORY = "JustNotesa/thunderbird-android"
private const val RELEASES_PATH = "/repos/JustNotesa/thunderbird-android/releases/latest"
private const val TAG_NAME = "mein-thunderbird-2026-10-25-3"
private const val RELEASE_NAME = "Mein Thunderbird 2026-10-25 (3)"
private const val RELEASE_BODY = "notes"
private const val ASSET_NAME = "mein-thunderbird-2026-10-25-3.apk"
private const val ASSET_CONTENT_TYPE = "application/vnd.android.package-archive"
private const val ASSET_SIZE = 36_349_629L
private const val ASSET_DIGEST = "sha256:398d0034e0e8ede314aa130243fd47c87bd94bf019515e3d683dd50c3fb8d942"
private const val ASSET_DOWNLOAD_URL = "https://github.com/JustNotesa/thunderbird-android/releases/download/x/x.apk"

private val RELEASE_JSON = """
    {
      "tag_name": "$TAG_NAME",
      "name": "$RELEASE_NAME",
      "body": "$RELEASE_BODY",
      "prerelease": false,
      "draft": false,
      "unknown_field": 1,
      "assets": [
        {
          "name": "$ASSET_NAME",
          "content_type": "$ASSET_CONTENT_TYPE",
          "size": $ASSET_SIZE,
          "digest": "$ASSET_DIGEST",
          "browser_download_url": "$ASSET_DOWNLOAD_URL"
        }
      ]
    }
""".trimIndent()

private val RELEASE_JSON_WITHOUT_DIGEST = """
    {
      "tag_name": "$TAG_NAME",
      "assets": [
        {
          "name": "$ASSET_NAME",
          "browser_download_url": "$ASSET_DOWNLOAD_URL"
        }
      ]
    }
""".trimIndent()

private val RELEASE_JSON_WITH_NULL_DIGEST = """
    {
      "tag_name": "$TAG_NAME",
      "assets": [
        {
          "name": "$ASSET_NAME",
          "digest": null,
          "browser_download_url": "$ASSET_DOWNLOAD_URL"
        }
      ]
    }
""".trimIndent()

private val RATE_LIMIT_JSON = """
    {
      "message": "API rate limit exceeded",
      "documentation_url": "https://docs.github.com/rest/overview/resources-in-the-rest-api"
    }
""".trimIndent()

class GithubReleaseClientTest {
    private lateinit var server: MockWebServer
    private lateinit var testSubject: GithubReleaseClient

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        testSubject = GithubReleaseClient(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `should fetch latest release`() = runTest {
        server.enqueue(MockResponse().setBody(RELEASE_JSON))

        val result = testSubject.fetchLatestRelease(REPOSITORY)

        assertThat(result).isEqualTo(
            GithubReleaseResponse.Success(
                GithubRelease(
                    tagName = TAG_NAME,
                    name = RELEASE_NAME,
                    body = RELEASE_BODY,
                    draft = false,
                    prerelease = false,
                    assets = listOf(
                        GithubAsset(
                            name = ASSET_NAME,
                            contentType = ASSET_CONTENT_TYPE,
                            size = ASSET_SIZE,
                            digest = ASSET_DIGEST,
                            browserDownloadUrl = ASSET_DOWNLOAD_URL,
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `should send anonymous request for latest release`() = runTest {
        server.enqueue(MockResponse().setBody(RELEASE_JSON))

        testSubject.fetchLatestRelease(REPOSITORY)

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo(RELEASES_PATH)
        assertThat(request.getHeader("Accept")).isEqualTo("application/vnd.github+json")
        assertThat(request.getHeader("X-GitHub-Api-Version")).isEqualTo("2022-11-28")
        assertThat(request.getHeader("User-Agent")).isEqualTo("MeinThunderbird-Updater")
        assertThat(request.getHeader("Authorization")).isNull()
    }

    @Test
    fun `should return null digest when asset has no digest`() = runTest {
        server.enqueue(MockResponse().setBody(RELEASE_JSON_WITHOUT_DIGEST))
        server.enqueue(MockResponse().setBody(RELEASE_JSON_WITH_NULL_DIGEST))

        val withoutDigest = testSubject.fetchLatestRelease(REPOSITORY)
        val withNullDigest = testSubject.fetchLatestRelease(REPOSITORY)

        assertThat(withoutDigest).isInstanceOf<GithubReleaseResponse.Success>().all {
            prop(GithubReleaseResponse.Success::release).transform { it.assets.single().digest }.isNull()
        }
        assertThat(withNullDigest).isInstanceOf<GithubReleaseResponse.Success>().all {
            prop(GithubReleaseResponse.Success::release).transform { it.assets.single().digest }.isNull()
        }
    }

    @Test
    fun `should report http error when release is not found`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))

        val result = testSubject.fetchLatestRelease(REPOSITORY)

        assertThat(result).isEqualTo(GithubReleaseResponse.HttpError(404))
    }

    @Test
    fun `should report http error when rate limited`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody(RATE_LIMIT_JSON))

        val result = testSubject.fetchLatestRelease(REPOSITORY)

        assertThat(result).isEqualTo(GithubReleaseResponse.HttpError(403))
    }

    @Test
    fun `should report http error when server fails`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = testSubject.fetchLatestRelease(REPOSITORY)

        assertThat(result).isEqualTo(GithubReleaseResponse.HttpError(500))
    }

    @Test
    fun `should report invalid response for malformed json`() = runTest {
        server.enqueue(MockResponse().setBody("{not json"))

        val result = testSubject.fetchLatestRelease(REPOSITORY)

        assertThat(result).isEqualTo(GithubReleaseResponse.InvalidResponse)
    }

    @Test
    fun `should report invalid response when tag name is missing`() = runTest {
        server.enqueue(MockResponse().setBody("""{"name":"Mein Thunderbird"}"""))

        val result = testSubject.fetchLatestRelease(REPOSITORY)

        assertThat(result).isEqualTo(GithubReleaseResponse.InvalidResponse)
    }

    @Test
    fun `should report invalid response for invalid repository`() = runTest {
        val invalidRepositories = listOf("", "foo", "../x/y", "a/b/c", "a b/c")

        val results = invalidRepositories.map { testSubject.fetchLatestRelease(it) }

        assertThat(results).containsOnly(GithubReleaseResponse.InvalidResponse)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `should report network error when server is unreachable`() = runTest {
        server.shutdown()

        val result = testSubject.fetchLatestRelease(REPOSITORY)

        assertThat(result).isEqualTo(GithubReleaseResponse.NetworkError)
    }
}
