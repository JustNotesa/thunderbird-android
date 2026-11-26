package com.fsck.k9.ui.settings.search

import android.content.Context
import androidx.annotation.XmlRes
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceManager
import net.thunderbird.components.core.logging.Logger

/**
 * The settings that can be searched, plus how many sets of settings could not be read.
 */
internal data class SettingsSearchIndex(
    val entries: List<SettingsSearchEntry>,
    val failedScopes: List<SettingsScope>,
    private val dataStores: Map<SettingsScope, PreferenceDataStore> = emptyMap(),
) {
    /** Whether the switch with the given [key] is on. */
    fun isOn(scope: SettingsScope, key: String): Boolean = dataStore(scope).getBoolean(key, false)

    /** Saves the switch with the given [key] in the same place the settings screen does. */
    fun setOn(scope: SettingsScope, key: String, isOn: Boolean) = dataStore(scope).putBoolean(key, isOn)

    private fun dataStore(scope: SettingsScope) = checkNotNull(dataStores[scope]) { "No settings for the scope" }

    companion object {
        val EMPTY = SettingsSearchIndex(entries = emptyList(), failedScopes = emptyList())
    }
}

/** One set of settings to index: the XML file, where its values are stored and what is hidden from the user. */
internal class SettingsSearchSource(
    val scope: SettingsScope,
    @param:XmlRes val xmlResource: Int,
    val dataStore: PreferenceDataStore,
    val excludedKeys: Set<String>,
    val noInlineToggleKeys: Set<String>,
)

/**
 * Builds the search index from the preference XML files themselves, so it never gets out of sync with the screens.
 */
internal class SettingsSearchIndexLoader(
    private val context: Context,
    private val logger: Logger,
) {
    fun load(sources: List<SettingsSearchSource>): SettingsSearchIndex {
        val entries = mutableListOf<SettingsSearchEntry>()
        val failed = mutableListOf<SettingsScope>()

        for (source in sources) {
            try {
                entries += loadSource(source)
            } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
                // No personal data in the log: only the kind of settings that failed.
                logger.error(tag = TAG, throwable = e) {
                    "Could not read the settings of ${source.scope.logName()} for the search"
                }
                failed += source.scope
            }
        }

        return SettingsSearchIndex(entries, failed, sources.associate { it.scope to it.dataStore })
    }

    private fun loadSource(source: SettingsSearchSource): List<SettingsSearchEntry> {
        val preferenceManager = PreferenceManager(context)
        preferenceManager.preferenceDataStore = ReadOnlyDataStore(source.dataStore)
        val root = preferenceManager.inflateFromResource(context, source.xmlResource, null)

        return SettingsSearchIndexBuilder.build(
            root = root.toSettingsNode(),
            scope = source.scope,
            excludedKeys = source.excludedKeys,
            noInlineToggleKeys = source.noInlineToggleKeys,
        )
    }

    private fun SettingsScope.logName() = when (this) {
        SettingsScope.General -> "the general settings"
        is SettingsScope.Account -> "an account"
    }

    private companion object {
        const val TAG = "SettingsSearchIndexLoader"
    }
}

/**
 * Inflating preferences saves their initial values to the data store. The search only wants to read the current
 * values, so everything written while inflating is dropped.
 */
@Suppress("TooManyFunctions")
private class ReadOnlyDataStore(private val delegate: PreferenceDataStore) : PreferenceDataStore() {
    override fun getString(key: String, defValue: String?) = delegate.getString(key, defValue)

    // A multi-select preference cannot handle a missing value
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String> {
        return delegate.getStringSet(key, defValues) ?: defValues ?: emptySet()
    }

    override fun getInt(key: String, defValue: Int) = delegate.getInt(key, defValue)

    override fun getLong(key: String, defValue: Long) = delegate.getLong(key, defValue)

    override fun getFloat(key: String, defValue: Float) = delegate.getFloat(key, defValue)

    override fun getBoolean(key: String, defValue: Boolean) = delegate.getBoolean(key, defValue)

    override fun putString(key: String, value: String?) = Unit

    override fun putStringSet(key: String, values: Set<String>?) = Unit

    override fun putInt(key: String, value: Int) = Unit

    override fun putLong(key: String, value: Long) = Unit

    override fun putFloat(key: String, value: Float) = Unit

    override fun putBoolean(key: String, value: Boolean) = Unit
}
