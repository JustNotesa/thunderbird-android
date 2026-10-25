package net.thunderbird.feature.updater.internal.check

import kotlinx.coroutines.CancellationException
import net.thunderbird.components.core.logging.Logger
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.AppUpdateChecker
import net.thunderbird.feature.updater.api.AppUpdateConfig
import net.thunderbird.feature.updater.api.UpdateCheckFailure
import net.thunderbird.feature.updater.api.UpdateCheckResult

private const val TAG = "DefaultAppUpdateChecker"

private const val APK_FILE_EXTENSION = ".apk"
private const val ANDROID_PACKAGE_MIME_TYPE = "application/vnd.android.package-archive"
private const val HTTPS_SCHEME_PREFIX = "https://"
private const val DIGEST_ALGORITHM_PREFIX = "sha256:"
private const val SHA256_HEX_LENGTH = 64

private val SHA256_HEX_PATTERN = Regex("[0-9a-fA-F]{$SHA256_HEX_LENGTH}")

/**
 * Asks GitHub for the latest release of the app and compares it with the release tag of this build.
 *
 * The updater is switched off in builds that were not made for a release; those have no release tag.
 */
internal class DefaultAppUpdateChecker(
    private val config: AppUpdateConfig,
    private val client: GithubReleaseClient,
    private val logger: Logger,
) : AppUpdateChecker {

    private val currentTag: ReleaseTag? = ReleaseTag.parse(config.currentReleaseTag, config.tagPrefix)

    override val isEnabled: Boolean = currentTag != null

    override val currentReleaseTag: String = if (isEnabled) config.currentReleaseTag else ""

    @Suppress("TooGenericExceptionCaught")
    override suspend fun check(): UpdateCheckResult {
        return try {
            checkForUpdate()
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (e: Exception) {
            logger.error(TAG, e) { "Checking for a new release failed." }
            UpdateCheckResult.Failed(UpdateCheckFailure.INVALID_RESPONSE)
        }
    }

    private suspend fun checkForUpdate(): UpdateCheckResult {
        val installedTag = currentTag ?: return UpdateCheckResult.Disabled

        return when (val response = client.fetchLatestRelease(config.repository)) {
            is GithubReleaseResponse.Success -> evaluateRelease(response.release, installedTag)

            GithubReleaseResponse.NetworkError -> UpdateCheckResult.Failed(UpdateCheckFailure.NETWORK)

            is GithubReleaseResponse.HttpError -> {
                logger.warn(TAG) { "GitHub answered with status code ${response.code}." }
                UpdateCheckResult.Failed(UpdateCheckFailure.SERVER)
            }

            GithubReleaseResponse.InvalidResponse -> UpdateCheckResult.Failed(UpdateCheckFailure.INVALID_RESPONSE)
        }
    }

    private fun evaluateRelease(release: GithubRelease, installedTag: ReleaseTag): UpdateCheckResult {
        if (release.draft || release.prerelease) {
            return UpdateCheckResult.UpToDate
        }

        return compareWithInstalledTag(release, installedTag)
    }

    private fun compareWithInstalledTag(release: GithubRelease, installedTag: ReleaseTag): UpdateCheckResult {
        val latestTag = ReleaseTag.parse(release.tagName, config.tagPrefix) ?: return UpdateCheckResult.UpToDate

        return if (latestTag > installedTag) {
            logger.info(TAG) { "Found a newer release: ${release.tagName}" }
            createAvailableResult(release)
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    private fun createAvailableResult(release: GithubRelease): UpdateCheckResult {
        val asset = findApkAsset(release.assets) ?: return UpdateCheckResult.Failed(UpdateCheckFailure.NO_APK)

        val update = AppUpdate(
            tag = release.tagName,
            title = release.name?.takeIf { it.isNotBlank() } ?: release.tagName,
            releaseNotes = release.body.orEmpty(),
            apkUrl = asset.browserDownloadUrl,
            apkSizeBytes = asset.size,
            apkSha256 = parseDigest(asset.digest),
        )

        return UpdateCheckResult.Available(update)
    }

    private fun findApkAsset(assets: List<GithubAsset>): GithubAsset? {
        val apkAssets = assets.filter { it.name.endsWith(APK_FILE_EXTENSION, ignoreCase = true) }
        val asset = apkAssets.firstOrNull { it.contentType == ANDROID_PACKAGE_MIME_TYPE } ?: apkAssets.firstOrNull()

        return asset?.takeIf { it.browserDownloadUrl.startsWith(HTTPS_SCHEME_PREFIX) }
    }

    private fun parseDigest(digest: String?): String? {
        val hex = digest
            ?.takeIf { it.startsWith(DIGEST_ALGORITHM_PREFIX) }
            ?.removePrefix(DIGEST_ALGORITHM_PREFIX)
            ?: return null

        return hex.takeIf { SHA256_HEX_PATTERN.matches(it) }?.lowercase()
    }
}
