package com.fsck.k9.ui.settings.search

import java.text.Normalizer

/**
 * Finds settings by what the user types.
 */
internal object SettingsSearcher {
    const val BREADCRUMB_SEPARATOR = " › "

    /**
     * Returns the entries whose title or summary contain every word of [query], ignoring case and accents. Entries
     * with all words in the title come first; otherwise the order of [entries] is kept.
     */
    fun search(entries: List<SettingsSearchEntry>, query: String): List<SettingsSearchEntry> {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        val matches = entries.mapNotNull { entry ->
            val title = normalize(entry.title)
            val all = entry.searchTexts.joinToString(separator = " ") { normalize(it) }
            if (words.all { it in all }) {
                entry to words.all { it in title }
            } else {
                null
            }
        }

        val (titleMatches, summaryMatches) = matches.partition { (_, inTitle) -> inTitle }
        return (titleMatches + summaryMatches).map { (entry, _) -> entry }
    }

    fun formatBreadcrumb(breadcrumb: List<String>): String = breadcrumb.joinToString(BREADCRUMB_SEPARATOR)

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
        return decomposed.filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
            .replace(WHITESPACE, " ")
    }

    private val WHITESPACE = Regex("\\s+")
}
