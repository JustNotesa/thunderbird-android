package com.fsck.k9.backend.api

/**
 * Notified about what a search on the server is doing at the moment.
 *
 * A server that is slow or can't be reached makes a search take a long time. This allows telling the user what is
 * being waited for.
 */
fun interface SearchListener {
    fun onSearchStep(step: SearchStep)
}

enum class SearchStep {
    /**
     * A connection to the server is being established and the folder is being opened. No query has been sent yet.
     */
    CONNECTING,

    /**
     * The query has been sent to the server and its response is awaited.
     */
    WAITING_FOR_RESPONSE,
}
