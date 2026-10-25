package net.thunderbird.feature.updater.api

sealed interface UpdateCheckResult {
    /** The updater is switched off in this build (no release tag). */
    data object Disabled : UpdateCheckResult

    /** The installed version is the newest release. */
    data object UpToDate : UpdateCheckResult

    data class Available(val update: AppUpdate) : UpdateCheckResult

    data class Failed(val reason: UpdateCheckFailure) : UpdateCheckResult
}

enum class UpdateCheckFailure {
    /** No connection to GitHub (offline, DNS, timeout). */
    NETWORK,

    /** GitHub answered with an error status (e.g. rate limit, server error, repository not found). */
    SERVER,

    /** GitHub's answer could not be understood. */
    INVALID_RESPONSE,

    /** The newest release has no APK attached. */
    NO_APK,
}
