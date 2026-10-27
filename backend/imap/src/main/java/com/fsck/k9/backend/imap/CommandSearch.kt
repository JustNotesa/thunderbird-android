package com.fsck.k9.backend.imap

import com.fsck.k9.backend.api.SearchListener
import com.fsck.k9.backend.api.SearchStep
import com.fsck.k9.mail.store.imap.ImapFolder
import com.fsck.k9.mail.store.imap.ImapMessage
import com.fsck.k9.mail.store.imap.ImapStore
import com.fsck.k9.mail.store.imap.OpenMode
import net.thunderbird.core.common.mail.Flag
import net.thunderbird.core.common.mail.ServerSearchCriteria

internal class CommandSearch(private val imapStore: ImapStore) {

    fun search(
        folderServerId: String,
        query: String?,
        requiredFlags: Set<Flag>?,
        forbiddenFlags: Set<Flag>?,
        performFullTextSearch: Boolean,
        listener: SearchListener? = null,
    ): List<String> {
        val folder = imapStore.getFolder(folderServerId)

        return search(folder, listener) {
            folder.search(
                queryString = query,
                requiredFlags = requiredFlags,
                forbiddenFlags = forbiddenFlags,
                performFullTextSearch = performFullTextSearch,
            )
        }
    }

    fun search(
        folderServerId: String,
        criteria: ServerSearchCriteria,
        listener: SearchListener? = null,
    ): List<String> {
        val folder = imapStore.getFolder(folderServerId)

        return search(folder, listener) {
            folder.search(criteria)
        }
    }

    private fun search(
        folder: ImapFolder,
        listener: SearchListener?,
        searchBlock: () -> List<ImapMessage>,
    ): List<String> {
        try {
            listener?.onSearchStep(SearchStep.CONNECTING)
            folder.open(OpenMode.READ_ONLY)

            listener?.onSearchStep(SearchStep.WAITING_FOR_RESPONSE)
            return searchBlock().sortedWith(UidReverseComparator())
                .map { it.uid }
        } finally {
            folder.close()
        }
    }
}
