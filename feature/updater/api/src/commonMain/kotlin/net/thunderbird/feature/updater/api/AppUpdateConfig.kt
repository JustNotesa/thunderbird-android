package net.thunderbird.feature.updater.api

/**
 * Where the app looks for its own updates.
 *
 * @param repository GitHub repository in the form `owner/name`, e.g. `JustNotesa/thunderbird-android`.
 * @param currentReleaseTag Release tag this build was made for, e.g. `mein-thunderbird-2026-10-26`. Empty when the
 *   build was not made for a release (debug and local builds); the updater is then switched off.
 * @param tagPrefix Prefix of the release tags that belong to this app.
 */
data class AppUpdateConfig(
    val repository: String,
    val currentReleaseTag: String,
    val tagPrefix: String = DEFAULT_TAG_PREFIX,
) {
    companion object {
        const val DEFAULT_TAG_PREFIX = "mein-thunderbird-"
    }
}
