package net.thunderbird.feature.updater.api

/** Looks up whether a newer release of the app exists. */
interface AppUpdateChecker {
    /** `false` when this build has no release tag; [check] then always returns [UpdateCheckResult.Disabled]. */
    val isEnabled: Boolean

    /** Release tag of the installed build, empty when [isEnabled] is `false`. */
    val currentReleaseTag: String

    /** Asks GitHub for the newest release. Never throws; failures are returned as [UpdateCheckResult.Failed]. */
    suspend fun check(): UpdateCheckResult

    /**
     * Asks GitHub for the release notes of the installed release ([currentReleaseTag]). Never throws; failures are
     * returned as [ReleaseNotesResult.Failed].
     */
    suspend fun installedReleaseNotes(): ReleaseNotesResult
}

sealed interface ReleaseNotesResult {
    /** The updater is switched off in this build (no release tag). */
    data object Disabled : ReleaseNotesResult

    /**
     * @param title the title of the release.
     * @param notes the full release notes (Markdown as written on GitHub).
     * @param url the `https://github.com/...` page of the release.
     */
    data class Loaded(val title: String, val notes: String, val url: String) : ReleaseNotesResult

    data class Failed(val reason: UpdateCheckFailure) : ReleaseNotesResult
}
