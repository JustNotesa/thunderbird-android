package net.thunderbird.feature.updater.api

sealed interface UpdateInstallState {
    data object Idle : UpdateInstallState

    /**
     * @param totalBytes total size, or -1 if unknown.
     * @param waiting why the download manager of the system doesn't download right now, `null` while it downloads.
     */
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val waiting: DownloadWaiting? = null,
    ) : UpdateInstallState

    /**
     * The update with the release tag [tag] was downloaded and verified in the background and waits until the user
     * (or the automatic installation) starts the installation with [AppUpdateInstaller.installDownloaded].
     */
    data class ReadyToInstall(val tag: String) : UpdateInstallState

    /** Checking the SHA-256 of the downloaded file. */
    data object Verifying : UpdateInstallState

    /** The APK was handed to Android; the system asks the user to confirm the installation. */
    data object WaitingForConfirmation : UpdateInstallState

    /**
     * The user confirmed the installation and Android is installing the update. Android closes the app when it is
     * done, so this is the last state the user sees in this version of the app.
     *
     * Android doesn't report a meaningful progress for this step, so there is none.
     */
    data object Installing : UpdateInstallState

    data class Failed(val reason: UpdateInstallFailure) : UpdateInstallState
}

/** Why the download manager of the system is waiting instead of downloading. */
enum class DownloadWaiting {
    /** There is no internet connection. */
    NETWORK,

    /** The download waits for a Wi-Fi connection. */
    WIFI,

    /** The download failed for a moment and the system tries it again soon. */
    RETRY,

    /** The system paused the download for another reason. */
    PAUSED,
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
