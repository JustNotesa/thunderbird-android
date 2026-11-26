package com.fsck.k9.ui.settings.search

/**
 * Holds the search index once it was read. A read that was started before [invalidate] is called does not end up in
 * the cache, as its values might already be out of date.
 */
internal class SettingsSearchIndexCache(private val load: suspend () -> SettingsSearchIndex) {
    private var index: SettingsSearchIndex? = null
    private var generation = 0

    /** The index if it was read already. */
    fun peek(): SettingsSearchIndex? = index

    /** Forgets the index so the next [get] reads it again. */
    fun invalidate() {
        generation++
        index = null
    }

    /** Returns the cached index or reads it. */
    suspend fun get(): SettingsSearchIndex {
        index?.let { return it }

        val startedAt = generation
        val loaded = load()
        if (startedAt == generation) {
            index = loaded
        }
        return loaded
    }
}
