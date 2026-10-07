package com.fsck.k9.ui.messagelist

import net.thunderbird.core.common.mail.splitSearchQuery
import net.thunderbird.feature.search.legacy.LocalMessageSearch
import net.thunderbird.feature.search.legacy.SearchConditionTreeNode
import net.thunderbird.feature.search.legacy.api.MessageSearchField
import net.thunderbird.feature.search.legacy.api.SearchAttribute
import net.thunderbird.feature.search.legacy.api.SearchCondition

private val MANUAL_SEARCH_FIELDS = listOf(
    MessageSearchField.SENDER,
    MessageSearchField.TO,
    MessageSearchField.CC,
    MessageSearchField.BCC,
    MessageSearchField.SUBJECT,
    MessageSearchField.MESSAGE_CONTENTS,
)

/**
 * Create a manual search that matches [query] against the standard message fields.
 *
 * A message has to contain every term of the query (see [splitSearchQuery]), but the terms can be found in different
 * fields, e.g. one in the sender and another one in the subject.
 *
 * The search is not restricted to any account or folder; callers can add restrictions afterwards.
 */
fun createManualQuerySearch(query: String): LocalMessageSearch {
    val searchTerms = splitSearchQuery(query).ifEmpty { listOf(query) }

    return LocalMessageSearch().apply {
        isManualSearch = true
        searchTerms.forEach { searchTerm ->
            and(createAnyFieldContainsCondition(searchTerm))
        }
    }
}

private fun createAnyFieldContainsCondition(searchTerm: String): SearchConditionTreeNode {
    val conditions = MANUAL_SEARCH_FIELDS.map { field ->
        SearchCondition(field, SearchAttribute.CONTAINS, searchTerm)
    }

    return conditions.drop(1)
        .fold(SearchConditionTreeNode.Builder(conditions.first())) { builder, condition -> builder.or(condition) }
        .build()
}
