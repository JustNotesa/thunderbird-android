package com.fsck.k9.backend.api

/**
 * The messages of a folder that stay on the device although they are older than the messages the folder is
 * synchronized with, e.g. the results of a search on the server. Without this they would be removed by the next
 * synchronization of the folder, only to be downloaded again by the next search.
 *
 * Their server IDs are stored as an extra value of the folder. A message is no longer kept once it is gone from the
 * server.
 */
object KeptMessages {
    const val FOLDER_EXTRA = "keptMessages"

    private const val SEPARATOR = ","

    @JvmStatic
    fun parse(value: String?): Set<String> {
        return value?.split(SEPARATOR)?.filter { it.isNotEmpty() }?.toSet().orEmpty()
    }

    @JvmStatic
    fun format(messageServerIds: Set<String>): String? {
        return messageServerIds.takeIf { it.isNotEmpty() }?.joinToString(SEPARATOR)
    }
}
