package com.fsck.k9.backend.imap

import com.fsck.k9.backend.api.SearchListener
import com.fsck.k9.backend.api.SearchStep
import com.fsck.k9.mail.store.imap.ImapStore
import com.fsck.k9.mail.store.imap.OpenMode
import net.thunderbird.core.common.mail.Flag

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
        try {
            listener?.onSearchStep(SearchStep.CONNECTING)
            folder.open(OpenMode.READ_ONLY)

            listener?.onSearchStep(SearchStep.WAITING_FOR_RESPONSE)
            return folder.search(
                queryString = query,
                requiredFlags = requiredFlags,
                forbiddenFlags = forbiddenFlags,
                performFullTextSearch = performFullTextSearch,
            ).sortedWith(UidReverseComparator())
                .map { it.uid }
        } finally {
            folder.close()
        }
    }
}
