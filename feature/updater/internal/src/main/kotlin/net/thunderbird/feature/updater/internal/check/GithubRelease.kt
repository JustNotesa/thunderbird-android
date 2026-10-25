package net.thunderbird.feature.updater.internal.check

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A release of a GitHub repository, as returned by the GitHub REST API. */
@Serializable
internal data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

/** A file attached to a GitHub release. */
@Serializable
internal data class GithubAsset(
    val name: String,
    @SerialName("content_type") val contentType: String? = null,
    val size: Long = 0,
    val digest: String? = null,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)
