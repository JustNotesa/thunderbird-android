package com.fsck.k9.ui.messagelist

import net.thunderbird.core.common.mail.splitSearchQuery
import net.thunderbird.feature.search.legacy.LocalMessageSearch
import net.thunderbird.feature.search.legacy.SearchConditionTreeNode
import net.thunderbird.feature.search.legacy.api.MessageSearchField
import net.thunderbird.feature.search.legacy.api.SearchAttribute
import net.thunderbird.feature.search.legacy.api.SearchCondition

private val MANUAL_SEARCH_HEADER_FIELDS = listOf(
    MessageSearchField.SENDER,
    MessageSearchField.TO,
    MessageSearchField.CC,
    MessageSearchField.BCC,
    MessageSearchField.SUBJECT,
)

private val MANUAL_SEARCH_FIELDS = MANUAL_SEARCH_HEADER_FIELDS + MessageSearchField.MESSAGE_CONTENTS

/**
 * Create a manual search that matches [query] against the standard message fields.
 *
 * A message has to contain every term of the query (see [splitSearchQuery]), but the terms can be found in different
 * fields, e.g. one in the sender and another one in the subject.
 *
 * The search is not restricted to any account or folder; callers can add restrictions afterwards.
 *
 * @param searchMessageContents Whether the text of the messages is searched, too. Without it only the sender, the
 *   recipients and the subject are.
 */
fun createManualQuerySearch(query: String, searchMessageContents: Boolean = true): LocalMessageSearch {
    val fields = if (searchMessageContents) MANUAL_SEARCH_FIELDS else MANUAL_SEARCH_HEADER_FIELDS
    val searchTerms = splitSearchQuery(query).ifEmpty { listOf(query) }

    return LocalMessageSearch().apply {
        isManualSearch = true
        searchTerms.forEach { searchTerm ->
            and(createAnyFieldContainsCondition(searchTerm, fields))
        }
    }
}

private fun createAnyFieldContainsCondition(
    searchTerm: String,
    fields: List<MessageSearchField>,
): SearchConditionTreeNode {
    val conditions = fields.map { field ->
        SearchCondition(field, SearchAttribute.CONTAINS, searchTerm)
    }

    return conditions.drop(1)
        .fold(SearchConditionTreeNode.Builder(conditions.first())) { builder, condition -> builder.or(condition) }
        .build()
}
