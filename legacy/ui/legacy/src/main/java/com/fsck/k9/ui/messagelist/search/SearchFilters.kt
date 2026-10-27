package com.fsck.k9.ui.messagelist.search

/**
 * Filters the user has set for a search, in addition to the text in the search field.
 */
data class SearchFilters(
    val textScope: TextScope = TextScope.HEADERS,
    val from: String? = null,
    val to: String? = null,
    val subject: String? = null,
    val period: SearchPeriod? = null,
    val isUnreadOnly: Boolean = false,
    val isStarredOnly: Boolean = false,
    val isAnsweredOnly: Boolean = false,
    val hasAttachment: Boolean = false,
    val largerThanBytes: Long? = null,
) : java.io.Serializable {
    /** Where the text of the search field is looked for. */
    enum class TextScope { HEADERS, BODY, EVERYWHERE }

    val isEmpty: Boolean get() = this == SearchFilters()

    /** The number of filters that are set, not counting [textScope]. */
    val activeFilterCount: Int
        get() = listOf(
            from != null,
            to != null,
            subject != null,
            period != null,
            isUnreadOnly,
            isStarredOnly,
            isAnsweredOnly,
            hasAttachment,
            largerThanBytes != null,
        ).count { it }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** A period of time the messages were received in. */
sealed interface SearchPeriod : java.io.Serializable {
    data object Today : SearchPeriod
    data object Last7Days : SearchPeriod
    data object Last30Days : SearchPeriod
    data object ThisYear : SearchPeriod

    /** [startMillis] inclusive, [endMillis] exclusive, both start of a day in the device's time zone. */
    data class Custom(val startMillis: Long, val endMillis: Long) : SearchPeriod

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
