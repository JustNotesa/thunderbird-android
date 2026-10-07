package com.fsck.k9.ui.messagelist

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

private const val PREFERENCES_NAME = "search_history"
private const val KEY_QUERIES = "queries"
private const val SEPARATOR = "\n"
private const val MAX_SIZE = 8

/**
 * Stores the most recent manual search queries so they can be suggested when the user searches again.
 *
 * The queries never leave the device.
 */
class SearchHistory(context: Context) {
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Returns the stored search queries, most recent first.
     */
    fun getQueries(): List<String> {
        return sharedPreferences.getString(KEY_QUERIES, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotEmpty() }
            .orEmpty()
    }

    fun add(query: String) {
        val newQuery = query.replace(SEPARATOR, " ").trim()
        if (newQuery.isEmpty()) return

        val queries = buildList {
            add(newQuery)
            addAll(getQueries().filterNot { it.equals(newQuery, ignoreCase = true) })
        }.take(MAX_SIZE)

        save(queries)
    }

    fun remove(query: String) {
        save(getQueries().filterNot { it == query })
    }

    fun clear() {
        sharedPreferences.edit { remove(KEY_QUERIES) }
    }

    private fun save(queries: List<String>) {
        sharedPreferences.edit { putString(KEY_QUERIES, queries.joinToString(SEPARATOR)) }
    }
}
