package com.fsck.k9.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds the state of the "Updates" section of the about screen.
 *
 * The state is `null` as long as the updater isn't used by this build.
 */
internal class AboutUpdatesStateHolder {
    private val _state = MutableStateFlow<UpdatesState?>(null)
    val state: StateFlow<UpdatesState?> = _state.asStateFlow()

    /** Shows the section with the given state. */
    fun set(state: UpdatesState) {
        _state.value = state
    }

    /** Changes parts of the state. Does nothing when the section isn't shown. */
    fun update(transform: (UpdatesState) -> UpdatesState) {
        _state.update { state -> state?.let(transform) }
    }

    /** Hides the section. */
    fun clear() {
        _state.value = null
    }
}
