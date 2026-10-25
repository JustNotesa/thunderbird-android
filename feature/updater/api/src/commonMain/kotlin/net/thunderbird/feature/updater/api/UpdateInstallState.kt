package net.thunderbird.feature.updater.api

sealed interface UpdateInstallState {
    data object Idle : UpdateInstallState

    /** @param totalBytes total size, or -1 if unknown. */
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : UpdateInstallState

    /** Checking the SHA-256 of the downloaded file. */
    data object Verifying : UpdateInstallState

    /** The APK was handed to Android; the system asks the user to confirm the installation. */
    data object WaitingForConfirmation : UpdateInstallState

    data class Failed(val reason: UpdateInstallFailure) : UpdateInstallState
}

enum class UpdateInstallFailure {
    /** Downloading the APK failed (network or server error). */
    DOWNLOAD,

    /** The downloaded file does not match the SHA-256 reported by GitHub. */
    CHECKSUM,

    /** The user has not allowed this app to install apps ("Install unknown apps"). */
    PERMISSION_MISSING,

    /** The user cancelled the installation in the system dialog. */
    CANCELLED,

    /** Android rejected the APK (e.g. signed with a different key, older version, not enough storage). */
    REJECTED,
}
