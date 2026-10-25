package net.thunderbird.feature.updater.api

/**
 * A newer release of the app that can be installed.
 *
 * @param tag Release tag, e.g. `mein-thunderbird-2026-10-26-2`.
 * @param title Release title as shown on GitHub.
 * @param releaseNotes Release description (plain text or Markdown as written on GitHub).
 * @param apkUrl HTTPS download URL of the APK asset.
 * @param apkSizeBytes Size of the APK in bytes.
 * @param apkSha256 Lower-case hex SHA-256 of the APK as reported by GitHub, or `null` if GitHub reported none.
 */
data class AppUpdate(
    val tag: String,
    val title: String,
    val releaseNotes: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val apkSha256: String?,
)
