package com.fsck.k9.ui.messagelist

import net.thunderbird.feature.search.legacy.LocalMessageSearch
import net.thunderbird.feature.search.legacy.api.MessageSearchField
import net.thunderbird.feature.search.legacy.api.SearchAttribute
import net.thunderbird.feature.search.legacy.api.SearchCondition

/**
 * Create a manual search that matches [query] against the standard message fields.
 *
 * The search is not restricted to any account or folder; callers can add restrictions afterwards.
 */
fun createManualQuerySearch(query: String): LocalMessageSearch {
    return LocalMessageSearch().apply {
        isManualSearch = true
        or(SearchCondition(MessageSearchField.SENDER, SearchAttribute.CONTAINS, query))
        or(SearchCondition(MessageSearchField.TO, SearchAttribute.CONTAINS, query))
        or(SearchCondition(MessageSearchField.CC, SearchAttribute.CONTAINS, query))
        or(SearchCondition(MessageSearchField.BCC, SearchAttribute.CONTAINS, query))
        or(SearchCondition(MessageSearchField.SUBJECT, SearchAttribute.CONTAINS, query))
        or(SearchCondition(MessageSearchField.MESSAGE_CONTENTS, SearchAttribute.CONTAINS, query))
    }
}
