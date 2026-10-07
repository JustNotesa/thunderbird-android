package com.fsck.k9.ui.messagelist

import app.k9mail.legacy.message.controller.MessageReference
import net.thunderbird.core.android.account.SortType
import net.thunderbird.feature.search.legacy.LocalMessageSearch

data class MessageListConfig(
    val search: LocalMessageSearch,
    val showingThreadedList: Boolean,
    val sortType: SortType,
    val sortAscending: Boolean,
    val sortDateAscending: Boolean,
    val activeMessage: MessageReference?,
    val sortOverrides: Map<MessageReference, MessageSortOverride>,
    /**
     * Messages to display even if they don't match [search], e.g. messages a server search found by their contents.
     */
    val includedMessages: Set<MessageReference> = emptySet(),
)

data class MessageSortOverride(
    val isRead: Boolean,
    val isStarred: Boolean,
)
