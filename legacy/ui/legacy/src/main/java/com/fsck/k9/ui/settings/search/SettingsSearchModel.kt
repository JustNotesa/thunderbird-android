package com.fsck.k9.ui.settings.search

/** Which settings a search entry belongs to. */
internal sealed interface SettingsScope {
    /** The general settings. */
    data object General : SettingsScope

    /** The settings of one account, identified by [accountId] and shown as [accountName]. */
    data class Account(val accountId: String, val accountName: String) : SettingsScope
}

internal enum class SettingsNodeKind {
    /** A (nested) screen that is opened by tapping on it. */
    SCREEN,

    /** A group of preferences inside of a screen. */
    CATEGORY,

    /** A preference without a switch. */
    PREFERENCE,

    /** A preference that can be turned on and off. */
    TOGGLE,
}

/**
 * A preference as read from a preference XML file, free of any Android types.
 */
internal data class SettingsNode(
    val kind: SettingsNodeKind,
    val key: String?,
    val title: String?,
    val summary: String? = null,
    val summaryOn: String? = null,
    val summaryOff: String? = null,
    val dependencyKey: String? = null,
    val children: List<SettingsNode> = emptyList(),
)

/**
 * One searchable setting.
 *
 * @property screenKey The key of the screen that contains the setting, or `null` for the first screen.
 * @property highlightKey The key of the preference to scroll to and highlight, or `null` to just open the screen.
 */
internal data class SettingsSearchEntry(
    val id: String,
    val scope: SettingsScope,
    val title: String,
    val summary: String?,
    val summaryOn: String?,
    val summaryOff: String?,
    val breadcrumb: List<String>,
    val screenKey: String?,
    val highlightKey: String?,
    val isToggle: Boolean,
    /** The key of the switch that has to be on for this setting to be usable, if any. */
    val dependencyKey: String? = null,
) {
    /** The summary to display. Settings that describe their state show the text matching [isOn]. */
    fun summaryFor(isOn: Boolean?): String? {
        return when (isOn) {
            true -> summaryOn ?: summary
            false -> summaryOff ?: summary
            null -> summary ?: summaryOn ?: summaryOff
        }
    }

    /** All texts the search looks at. */
    val searchTexts: List<String> = listOfNotNull(title, summary, summaryOn, summaryOff).filter { it.isNotBlank() }
}
