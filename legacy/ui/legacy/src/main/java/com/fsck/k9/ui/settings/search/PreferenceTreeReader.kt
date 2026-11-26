package com.fsck.k9.ui.settings.search

import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference

/**
 * Reads an inflated preference tree into [SettingsNode]s.
 */
internal fun PreferenceGroup.toSettingsNode(): SettingsNode = (this as Preference).toSettingsNode()

private fun Preference.toSettingsNode(): SettingsNode {
    val children = if (this is PreferenceGroup) {
        (0 until preferenceCount).map { index -> getPreference(index).toSettingsNode() }
    } else {
        emptyList()
    }

    val twoState = this as? TwoStatePreference
    return SettingsNode(
        kind = when {
            this is PreferenceScreen -> SettingsNodeKind.SCREEN
            this is PreferenceCategory || this is PreferenceGroup -> SettingsNodeKind.CATEGORY
            twoState != null -> SettingsNodeKind.TOGGLE
            else -> SettingsNodeKind.PREFERENCE
        },
        key = key,
        title = title?.toString(),
        summary = summary?.toString(),
        summaryOn = twoState?.summaryOn?.toString(),
        summaryOff = twoState?.summaryOff?.toString(),
        dependencyKey = dependency,
        children = children,
    )
}
