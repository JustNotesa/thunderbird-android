package com.fsck.k9.backend.api

/**
 * The storage an account uses on the server and how much it is allowed to use.
 */
data class StorageQuota(
    val usedBytes: Long,
    val limitBytes: Long,
)
