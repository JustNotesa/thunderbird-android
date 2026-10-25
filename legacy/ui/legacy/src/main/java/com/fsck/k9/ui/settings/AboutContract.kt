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
        data object OnCheckForUpdatesClick : Event
        data object OnInstallClick : Event
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
    val check: CheckStatus,
    val install: UpdateInstallState,
    val canInstallPackages: Boolean,
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
