package net.thunderbird.feature.updater.internal.check

import kotlinx.coroutines.CancellationException
import net.thunderbird.components.core.logging.Logger
import net.thunderbird.feature.updater.api.AppUpdateConfig
import net.thunderbird.feature.updater.api.ReleaseNotesResult
import net.thunderbird.feature.updater.api.UpdateCheckFailure

private const val TAG = "InstalledReleaseNotes"

private const val GITHUB_URL_PREFIX = "https://github.com/"

/**
 * Loads the release notes of the release that is installed.
 *
 * The notes are the description of the release on GitHub, so that the user can read what changed in the version
 * that is used.
 */
internal class InstalledReleaseNotes(
    private val config: AppUpdateConfig,
    private val client: GithubReleaseClient,
    private val logger: Logger,
) {

    /**
     * Asks GitHub for the release notes of the installed release.
     *
     * Never throws; every failure is reported as a [ReleaseNotesResult] value.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun load(): ReleaseNotesResult {
        return try {
            loadNotes()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            logger.error(TAG, e) { "The release notes of the installed release couldn't be loaded." }
            ReleaseNotesResult.Failed(UpdateCheckFailure.INVALID_RESPONSE)
        }
    }

    private suspend fun loadNotes(): ReleaseNotesResult {
        val tag = config.currentReleaseTag
        if (tag.isEmpty()) {
            // The build wasn't made for a release, so there are no release notes.
            return ReleaseNotesResult.Disabled
        }

        return when (val response = client.fetchReleaseByTag(config.repository, tag)) {
            is GithubReleaseResponse.Success -> loaded(response.release)

            GithubReleaseResponse.NetworkError -> ReleaseNotesResult.Failed(UpdateCheckFailure.NETWORK)

            is GithubReleaseResponse.HttpError -> {
                logger.warn(TAG) { "GitHub answered with status code ${response.code}." }
                ReleaseNotesResult.Failed(UpdateCheckFailure.SERVER)
            }

            GithubReleaseResponse.InvalidResponse -> ReleaseNotesResult.Failed(UpdateCheckFailure.INVALID_RESPONSE)
        }
    }

    private fun loaded(release: GithubRelease): ReleaseNotesResult {
        return ReleaseNotesResult.Loaded(
            title = release.name?.takeIf { it.isNotBlank() } ?: release.tagName,
            notes = release.body.orEmpty(),
            url = urlOf(release),
        )
    }

    private fun urlOf(release: GithubRelease): String {
        val htmlUrl = release.htmlUrl
        return if (htmlUrl != null && htmlUrl.startsWith(GITHUB_URL_PREFIX)) {
            htmlUrl
        } else {
            // The page of the release is known, even when GitHub doesn't tell where the release is.
            "$GITHUB_URL_PREFIX${config.repository}/releases/tag/${release.tagName}"
        }
    }
}
