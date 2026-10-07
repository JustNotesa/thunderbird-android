package com.fsck.k9.mail.store.imap

/**
 * The storage an account uses on the server and how much it is allowed to use, as reported by the server.
 */
data class ImapStorageQuota(
    val usedBytes: Long,
    val limitBytes: Long,
)
