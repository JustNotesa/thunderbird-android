package com.fsck.k9.ui.settings

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.UpdateCheckFailure
import net.thunderbird.feature.updater.api.UpdateInstallState

internal interface AboutContract {

    interface ViewModel :
        UnidirectionalViewModel<State, Event, Effect>

    @Stable
    data class State(
        val version: String = "",
        val libraries: ImmutableList<Library> = persistentListOf(),
        val updates: UpdatesState? = null,
    )

    sealed interface Event {
        data object OnChangeLogClick : Event
        data class OnSectionContentClick(val url: String) : Event
        data class OnLibraryClick(val library: Library) : Event
        data class OnAutoCheckChanged(val enabled: Boolean) : Event
        data class OnBackgroundDownloadChanged(val enabled: Boolean) : Event
        data class OnDownloadOnlyOnWifiChanged(val enabled: Boolean) : Event
        data class OnAutoInstallWhenIdleChanged(val enabled: Boolean) : Event
        data object OnInstalledVersionClick : Event
        data object OnRetryInstalledNotes : Event
        data object OnCheckForUpdatesClick : Event
        data object OnInstallClick : Event
        data object OnInstallDownloadedClick : Event
        data object OnRefreshDownloadClick : Event
        data object OnOpenInstallPermissionClick : Event
        data object OnScreenResumed : Event
        data object OnInstallFailureDismiss : Event
        data object OnCancelInstallClick : Event
    }

    sealed interface Effect {
        data class OpenUrl(val url: String) : Effect
        data object OpenChangeLog : Effect
    }
}

/** State of the "Updates" section of the about screen. `null` when the updater is not available. */
@Stable
data class UpdatesState(
    val currentVersionTag: String,
    val autoCheckEnabled: Boolean,
    val backgroundDownloadEnabled: Boolean,
    val downloadOnlyOnWifi: Boolean,
    val autoInstallWhenIdle: Boolean,
    val check: CheckStatus,
    val install: UpdateInstallState,
    val canInstallPackages: Boolean,
    val installedNotes: InstalledNotesStatus,
)

/** State of the check for a newer release. */
sealed interface CheckStatus {
    /** No check has been performed yet. */
    data object Idle : CheckStatus

    /** A check is currently running. */
    data object Checking : CheckStatus

    /** The installed version is the newest release. */
    data object UpToDate : CheckStatus

    /** A newer release is available. */
    data class Available(val update: AppUpdate) : CheckStatus

    /** The check failed. */
    data class Failed(val reason: UpdateCheckFailure) : CheckStatus
}

/** State of the release notes of the installed version. */
sealed interface InstalledNotesStatus {
    /** The release notes aren't shown. */
    data object Hidden : InstalledNotesStatus

    /** The release notes are being loaded. */
    data object Loading : InstalledNotesStatus

    /**
     * @param title the title of the release.
     * @param notes the full release notes (Markdown as written on GitHub).
     * @param url the `https://github.com/...` page of the release.
     */
    data class Loaded(val title: String, val notes: String, val url: String) : InstalledNotesStatus

    /** The release notes couldn't be loaded. */
    data class Failed(val reason: UpdateCheckFailure) : InstalledNotesStatus
}
