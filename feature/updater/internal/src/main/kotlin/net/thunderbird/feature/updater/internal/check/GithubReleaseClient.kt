package net.thunderbird.feature.updater.internal.check

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

private const val DEFAULT_BASE_URL = "https://api.github.com"
private const val ACCEPT_HEADER = "application/vnd.github+json"
private const val API_VERSION_HEADER = "2022-11-28"
private const val USER_AGENT = "MeinThunderbird-Updater"
private val REPOSITORY_PATTERN = Regex("^[A-Za-z0-9-]+/[A-Za-z0-9._-]+$")
private val TAG_PATTERN = Regex("^[A-Za-z0-9._-]+$")

// The tags that would change the path of the request, because the browser of the URL treats them as a step up.
private val DOT_SEGMENT_TAGS = setOf(".", "..")

private const val SEGMENT_REPOS = "repos"
private const val SEGMENT_RELEASES = "releases"
private const val SEGMENT_LATEST = "latest"
private const val SEGMENT_TAGS = "tags"

/**
 * Asks the GitHub REST API for the latest release of a repository.
 *
 * Requests are anonymous; GitHub then applies its rate limit for unauthenticated requests.
 */
internal class GithubReleaseClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * Fetches the latest release of [repository], given in the form `owner/name`.
     *
     * Never throws; every failure is reported as a [GithubReleaseResponse] value.
     */
    suspend fun fetchLatestRelease(repository: String): GithubReleaseResponse {
        val request = request(repository, listOf(SEGMENT_RELEASES, SEGMENT_LATEST))
            ?: return GithubReleaseResponse.InvalidResponse

        return execute(request)
    }

    /**
     * Fetches the release with the tag [tag] of [repository], given in the form `owner/name`.
     *
     * Never throws; every failure is reported as a [GithubReleaseResponse] value.
     */
    suspend fun fetchReleaseByTag(repository: String, tag: String): GithubReleaseResponse {
        // A tag that isn't a plain name isn't asked for, so that it can't change the requested path.
        val plainTag = TAG_PATTERN.matches(tag) && tag !in DOT_SEGMENT_TAGS
        val request = if (plainTag) request(repository, listOf(SEGMENT_RELEASES, SEGMENT_TAGS, tag)) else null

        return if (request == null) GithubReleaseResponse.InvalidResponse else execute(request)
    }

    private fun request(repository: String, segments: List<String>): Request? {
        if (!REPOSITORY_PATTERN.matches(repository)) {
            return null
        }

        return createRequest(repository, segments)
    }

    private fun createRequest(repository: String, segments: List<String>): Request? {
        val builder = baseUrl.toHttpUrlOrNull()?.newBuilder() ?: return null
        // The tag is a path segment of its own, so that it can't change the path of the request.
        builder.addPathSegment(SEGMENT_REPOS)
        builder.addPathSegment(repository.substringBefore('/'))
        builder.addPathSegment(repository.substringAfter('/'))
        segments.forEach { segment -> builder.addPathSegment(segment) }

        return Request.Builder()
            .url(builder.build())
            .header("Accept", ACCEPT_HEADER)
            .header("X-GitHub-Api-Version", API_VERSION_HEADER)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
    }

    private suspend fun execute(request: Request): GithubReleaseResponse {
        return try {
            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    parseResponse(response)
                }
            }
        } catch (_: IOException) {
            // OkHttp reports a cancelled call as an IOException, so let cancellation win over the network error.
            currentCoroutineContext().ensureActive()
            GithubReleaseResponse.NetworkError
        }
    }

    private fun parseResponse(response: Response): GithubReleaseResponse {
        if (!response.isSuccessful) {
            return GithubReleaseResponse.HttpError(response.code)
        }

        return parseRelease(response.readBody())
    }

    private fun parseRelease(body: String?): GithubReleaseResponse {
        if (body == null) {
            return GithubReleaseResponse.InvalidResponse
        }

        return try {
            GithubReleaseResponse.Success(json.decodeFromString<GithubRelease>(body))
        } catch (_: SerializationException) {
            GithubReleaseResponse.InvalidResponse
        } catch (_: IllegalArgumentException) {
            GithubReleaseResponse.InvalidResponse
        }
    }

    private fun Response.readBody(): String? {
        val responseBody: ResponseBody? = body
        return responseBody?.string()
    }
}

/** The outcome of a request to the GitHub releases API. */
internal sealed interface GithubReleaseResponse {
    /** GitHub answered with a release. */
    data class Success(val release: GithubRelease) : GithubReleaseResponse

    /** GitHub could not be reached. */
    data object NetworkError : GithubReleaseResponse

    /** GitHub answered with an error status code. */
    data class HttpError(val code: Int) : GithubReleaseResponse

    /** The answer was not a release this client understands. */
    data object InvalidResponse : GithubReleaseResponse
}
