package net.thunderbird.core.common.mail

private val SEARCH_TERM_REGEX = Regex("""\"([^\"]+)\"|(\S+)""")

/**
 * Split a search query into the terms a message has to contain to match the query.
 *
 * Terms are separated by whitespace. Text enclosed in double quotes is kept together as one term.
 */
fun splitSearchQuery(query: String): List<String> {
    return SEARCH_TERM_REGEX.findAll(query)
        .map { match -> match.groupValues[1].ifEmpty { match.groupValues[2].trim('"') } }
        .map { term -> term.trim() }
        .filter { term -> term.isNotEmpty() }
        .toList()
}
