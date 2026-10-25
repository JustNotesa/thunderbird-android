package net.thunderbird.feature.updater.api

/** Looks up whether a newer release of the app exists. */
interface AppUpdateChecker {
    /** `false` when this build has no release tag; [check] then always returns [UpdateCheckResult.Disabled]. */
    val isEnabled: Boolean

    /** Release tag of the installed build, empty when [isEnabled] is `false`. */
    val currentReleaseTag: String

    /** Asks GitHub for the newest release. Never throws; failures are returned as [UpdateCheckResult.Failed]. */
    suspend fun check(): UpdateCheckResult
}
